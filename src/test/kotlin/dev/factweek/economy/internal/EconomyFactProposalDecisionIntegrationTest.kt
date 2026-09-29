package dev.factweek.economy.internal

import dev.factweek.FactweekApplication
import dev.factweek.briefing.WeeklyEconomyBriefing
import dev.factweek.economy.*
import dev.factweek.ingestion.*
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.ai.model.chat=none", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"],
)
@Testcontainers
class EconomyFactProposalDecisionIntegrationTest {
    @Autowired private lateinit var proposals: EconomyFactProposals
    @Autowired private lateinit var facts: EconomyFacts
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var documents: SourceDocumentPersistenceService
    @Autowired private lateinit var briefing: WeeklyEconomyBriefing
    @Autowired private lateinit var clock: Clock
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var context: WebApplicationContext
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.update("delete from economy_fact_proposal_measurement")
        jdbc.update("delete from economy_fact_proposal_entity")
        jdbc.update("delete from economy_fact_proposal")
        jdbc.update("delete from economy_fact_measurement")
        jdbc.update("delete from economy_fact_entity")
        jdbc.update("delete from economy_fact_source")
        jdbc.update("delete from economy_fact")
        jdbc.update("delete from source_document")
        jdbc.update("delete from news_candidate")
    }

    @Test
    fun `accepts event with every typed field and stores decision`() {
        val proposal = event(sourceType = CandidateSourceType.PRIMARY_DOCUMENT)
        val accepted = proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
        val fact = facts.relevantForBriefingBetween(LocalDate.now(clock), LocalDate.now(clock))
            .single { it.id == accepted.economyFactId }
        assertEquals(EconomyFactProposalStatus.ACCEPTED, accepted.status)
        assertEquals(EconomyEvidenceLevel.PRIMARY_CONFIRMED, accepted.reviewedEvidenceLevel)
        assertNotNull(accepted.reviewedAt)
        assertEquals(accepted, proposals.find(proposal.id))
        assertEquals(proposal.statement, fact.statement)
        assertEquals(proposal.category, fact.category)
        assertEquals(proposal.eventType, fact.eventType)
        assertEquals(proposal.occurredOn, fact.occurredOn)
        assertEquals(proposal.geography, fact.geography)
        assertEquals(proposal.entities.toSet(), fact.entities.toSet())
        assertEquals(1, fact.sources.size)
        assertEquals(SourceType.PRIMARY_DOCUMENT, fact.sources.single().sourceType)
        assertEquals(accepted.economyFactId, jdbc.queryForObject("select economy_fact_id from economy_fact_proposal where id = ?", UUID::class.java, proposal.id))
    }

    @Test
    fun `accepts complete indicator with independent evidence and includes only accepted fact in briefing`() {
        val acceptedProposal = indicator()
        val rejectedProposal = indicator()
        val accepted = proposals.accept(acceptedProposal.id, AcceptEconomyFactProposal(
            evidenceLevel = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED,
            additionalSources = listOf(SourceReference("https://independent.example/data", "Independent", SourceType.DATASET)),
        ))
        proposals.reject(rejectedProposal.id, "Incorrect indicator")
        val fact = facts.relevantForBriefingBetween(LocalDate.now(clock).minusDays(6), LocalDate.now(clock))
            .single { it.id == accepted.economyFactId }
        assertEquals(0, fact.measurement!!.value.compareTo(BigDecimal("2.1234567891")))
        assertEquals(acceptedProposal.referencePeriod, fact.referencePeriod)
        assertEquals(acceptedProposal.measurement, fact.measurement)
        assertEquals(acceptedProposal.geography, fact.geography)
        assertEquals(acceptedProposal.entities.toSet(), fact.entities.toSet())
        assertEquals(2, fact.sources.size)
        assertEquals(1, briefing.current(setOf(EconomyCategory.PRICES_AND_INFLATION), 10).factCount)
        assertEquals(0, briefing.current(setOf(EconomyCategory.MONETARY_POLICY), 10).factCount)
        assertEquals(1L, count("economy_fact"))
    }

    @Test
    fun `invalid final evidence leaves proposal and all fact tables unchanged`() {
        val proposal = event(sourceType = CandidateSourceType.NEWS_REPORT)
        val before = factCounts()
        assertThrows<InvalidEconomyFactPublicationException> {
            proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
        }
        assertEquals(before, factCounts())
        assertEquals(proposal, proposals.find(proposal.id))
        val source = SourceReference("https://independent.example/report", "Independent", SourceType.PRIMARY_DOCUMENT)
        val accepted = proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, listOf(source)))
        assertNotNull(accepted.economyFactId)
    }

    @Test
    fun `rejects once and refuses every later decision`() {
        val proposal = event()
        val before = factCounts()
        val rejected = proposals.reject(proposal.id, "  Unsupported claim  ")
        assertEquals("Unsupported claim", rejected.rejectionReason)
        assertNotNull(rejected.reviewedAt)
        assertNull(rejected.economyFactId)
        assertEquals(before, factCounts())
        assertThrows<EconomyFactProposalConflictException> { proposals.reject(proposal.id, "Different reason") }
        assertThrows<EconomyFactProposalConflictException> {
            proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
        }
        assertEquals(rejected, proposals.find(proposal.id))
        assertEquals(before, factCounts())
    }

    @Test
    fun `accepted proposal refuses repeated accept and reject`() {
        val proposal = event()
        val accepted = proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
        val before = factCounts()
        assertThrows<EconomyFactProposalConflictException> {
            proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
        }
        assertThrows<EconomyFactProposalConflictException> { proposals.reject(proposal.id, "Too late") }
        assertEquals(before, factCounts())
        assertEquals(accepted, proposals.find(proposal.id))
    }

    @Test
    fun `http errors and decision fields use problem json`() {
        val proposal = event()
        val path = "/api/v1/economy/fact-proposals/{id}"
        for (reason in listOf("", "   ", "x".repeat(1001))) {
            mvc.perform(post("$path/reject", proposal.id).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"$reason"}"""))
                .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            assertEquals(EconomyFactProposalStatus.PROPOSED, proposals.find(proposal.id)!!.status)
        }
        mvc.perform(post("$path/accept", proposal.id).contentType(MediaType.APPLICATION_JSON).content("""{"evidenceLevel":"DOCUMENTED"}"""))
            .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        mvc.perform(post("$path/accept", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("""{"evidenceLevel":"PRIMARY_CONFIRMED"}"""))
            .andExpect(status().isNotFound).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        mvc.perform(post("$path/reject", proposal.id).contentType(MediaType.APPLICATION_JSON).content("""{"reason":" No evidence "}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("No evidence"))
        mvc.perform(get(path, proposal.id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewedAt").isNotEmpty)
            .andExpect(jsonPath("$.rejectionReason").value("No evidence"))
        mvc.perform(post("$path/reject", proposal.id).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Again"}"""))
            .andExpect(status().isConflict).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        assertEquals(0L, count("economy_fact"))
    }

    @Test
    fun `http accepts and exposes the fact reference on later get`() {
        val proposal = event()
        val path = "/api/v1/economy/fact-proposals/{id}"
        mvc.perform(post("$path/accept", proposal.id).contentType(MediaType.APPLICATION_JSON)
            .content("""{"evidenceLevel":"PRIMARY_CONFIRMED"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.reviewedEvidenceLevel").value("PRIMARY_CONFIRMED"))
            .andExpect(jsonPath("$.economyFactId").isNotEmpty)
        mvc.perform(get(path, proposal.id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.economyFactId").value(proposals.find(proposal.id)!!.economyFactId.toString()))
        mvc.perform(post("$path/accept", proposal.id).contentType(MediaType.APPLICATION_JSON)
            .content("""{"evidenceLevel":"PRIMARY_CONFIRMED"}"""))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        mvc.perform(post("$path/reject", proposal.id).contentType(MediaType.APPLICATION_JSON)
            .content("""{"reason":"Too late"}"""))
            .andExpect(status().isConflict)
        assertEquals(1L, count("economy_fact"))
    }

    @Test
    fun `parallel accept and accept serialize to one fact`() = concurrentDecisions(false)

    @Test
    fun `parallel accept and reject leave one consistent decision`() = concurrentDecisions(true)

    private fun concurrentDecisions(rejectSecond: Boolean) {
        val proposal = event()
        val before = count("economy_fact")
        val connection = jdbc.dataSource!!.connection
        connection.autoCommit = false
        connection.prepareStatement("select id from economy_fact_proposal where id = ? for update").use {
            it.setObject(1, proposal.id)
            it.executeQuery().close()
        }
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val decisions = (0..1).map { index ->
                pool.submit<String> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    try {
                        if (index == 1 && rejectSecond) proposals.reject(proposal.id, "Concurrent rejection")
                        else proposals.accept(proposal.id, AcceptEconomyFactProposal(EconomyEvidenceLevel.PRIMARY_CONFIRMED))
                        "success"
                    } catch (_: EconomyFactProposalConflictException) {
                        "conflict"
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            awaitBlockedDecisions()
            connection.commit()
            assertEquals(listOf("conflict", "success"), decisions.map { it.get(20, TimeUnit.SECONDS) }.sorted())
            val decided = proposals.find(proposal.id)!!
            assertNotNull(decided.reviewedAt)
            assertEquals(if (decided.status == EconomyFactProposalStatus.ACCEPTED) before + 1 else before, count("economy_fact"))
            assertEquals(decided.status == EconomyFactProposalStatus.ACCEPTED, decided.economyFactId != null)
        } finally {
            connection.rollback()
            connection.close()
            pool.shutdownNow()
        }
    }

    private fun awaitBlockedDecisions() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            val blocked = jdbc.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%economy_fact_proposal%'",
                Long::class.java,
            )!!
            if (blocked >= 2) return
            Thread.yield()
        }
        fail<Unit>("Both decision transactions did not reach the PostgreSQL row lock")
    }

    private fun event(sourceType: CandidateSourceType = CandidateSourceType.PRIMARY_DOCUMENT): EconomyFactProposal {
        val source = fetched(sourceType)
        return proposals.create(CreateEconomyFactProposal(
            sourceDocumentId = source,
            statement = "The central bank kept its rate unchanged.",
            category = EconomyCategory.MONETARY_POLICY,
            eventType = EconomyEventType.MONETARY_POLICY_DECIDED,
            occurredOn = LocalDate.now(clock),
            geography = EconomyGeography(EconomyGeographyKind.COUNTRY, " Germany ", "de"),
            entities = listOf(EconomyEntityReference(" Central Bank ", EconomyEntityType.CENTRAL_BANK)),
            evidenceText = "Rate decision evidence",
        ))
    }

    private fun indicator(): EconomyFactProposal {
        val source = fetched(CandidateSourceType.PRIMARY_DOCUMENT)
        return proposals.create(CreateEconomyFactProposal(
            sourceDocumentId = source,
            statement = "Inflation was reported.",
            category = EconomyCategory.PRICES_AND_INFLATION,
            eventType = EconomyEventType.INDICATOR_VALUE_REPORTED,
            referencePeriod = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH),
            geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"),
            measurement = EconomyMeasurement(BigDecimal("2.1234567891"), EconomyMeasurementUnit.PERCENT, EconomyReleaseStatus.FINAL, EconomySeasonalAdjustment.ADJUSTED, EconomyValueBasis.REAL),
            entities = listOf(EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)),
            evidenceText = "Rate decision evidence",
        ))
    }

    private fun fetched(sourceType: CandidateSourceType): UUID {
        val id = UUID.randomUUID()
        val candidate = candidates.storeDiscovered(CandidateCaptureCommand(
            sourceUrl = URI("https://example.org/$id"),
            title = "Source $id",
            publisher = "Original publisher",
            publishedAt = clock.instant(),
            language = "de",
            discoveryProvider = CandidateDiscoveryProvider.MANUAL,
            sourceType = sourceType,
        ))
        documents.recordSuccess(candidate, SourceContentFetchResult(URI(candidate.canonicalUrl), "text/html", 200, "Rate decision evidence", "a".repeat(64)))
        return candidate.id
    }

    private fun count(table: String): Long = jdbc.queryForObject("select count(*) from $table", Long::class.java)!!

    private fun factCounts() = listOf("economy_fact", "economy_fact_measurement", "economy_fact_entity", "economy_fact_source").map(::count)

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
