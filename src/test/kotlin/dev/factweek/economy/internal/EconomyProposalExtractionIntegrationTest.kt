package dev.factweek.economy.internal

import dev.factweek.FactweekApplication
import dev.factweek.briefing.WeeklyEconomyBriefing
import dev.factweek.economy.*
import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateDiscoveryProvider
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceContentFailureReason
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.ai.model.chat=none", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"],
)
@Import(EconomyProposalExtractionIntegrationTest.ModelBoundary::class)
@Testcontainers
class EconomyProposalExtractionIntegrationTest {
    @Autowired private lateinit var context: WebApplicationContext
    @Autowired private lateinit var client: StubEconomyModelClient
    @Autowired private lateinit var proposals: EconomyFactProposals
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var documents: SourceDocumentPersistenceService
    @Autowired private lateinit var briefing: WeeklyEconomyBriefing
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: Clock
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        client.reset()
        jdbc.update("delete from economy_fact_proposal_measurement")
        jdbc.update("delete from economy_fact_proposal_entity")
        jdbc.update("delete from economy_fact_proposal")
        jdbc.update("delete from document_section_classification_section")
        jdbc.update("delete from document_section_classification")
        jdbc.update("delete from source_document")
        jdbc.update("delete from news_candidate")
    }

    @Test
    fun `extracts a complete event as undecided proposal without publishing`() {
        val id = fetched()
        classify(id, "economy")
        val factsBefore = factCounts()
        client.response = response(event())

        mvc.perform(extract(id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceDocumentId").value(id.toString()))
            .andExpect(jsonPath("$.proposalCount").value(1))
            .andExpect(jsonPath("$.proposals[0].status").value("PROPOSED"))
            .andExpect(jsonPath("$.proposals[0].geography.code").value("DE"))
        val stored = stored(id).single()
        assertEquals("The central bank kept its policy rate unchanged.", stored.statement)
        assertEquals(EconomyCategory.MONETARY_POLICY, stored.category)
        assertEquals(EconomyEventType.MONETARY_POLICY_DECIDED, stored.eventType)
        assertEquals(LocalDate.now(clock), stored.occurredOn)
        assertEquals(EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"), stored.geography)
        assertEquals(listOf(EconomyEntityReference("Central Bank", EconomyEntityType.CENTRAL_BANK)), stored.entities)
        assertEquals("Rate decision evidence", stored.evidenceText)
        assertProposed(stored)
        assertEquals(factsBefore, factCounts())
        assertEquals(0, briefing.current(emptySet(), 10).factCount)
        assertTrue(client.lastPrompt!!.userContent.contains("Rate decision evidence"))
        assertFalse(client.transactionActiveWhenCalled)
    }

    @Test
    fun `extracts full indicator measurement and reference period`() {
        val id = fetched()
        classify(id, "economy", "technology")
        client.response = response(indicator())

        mvc.perform(extract(id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.proposalCount").value(1))
        val stored = stored(id).single()
        assertEquals(EconomyCategory.PRICES_AND_INFLATION, stored.category)
        assertEquals(EconomyEventType.INDICATOR_VALUE_REPORTED, stored.eventType)
        assertEquals("Inflation was reported for August 2026.", stored.statement)
        assertNull(stored.occurredOn)
        assertEquals(EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH), stored.referencePeriod)
        val measurement = requireNotNull(stored.measurement)
        assertEquals(0, measurement.value.compareTo(java.math.BigDecimal("2.1234567891")))
        assertEquals(EconomyMeasurementUnit.PERCENT, measurement.unit)
        assertEquals(EconomyReleaseStatus.FINAL, measurement.releaseStatus)
        assertEquals(EconomySeasonalAdjustment.ADJUSTED, measurement.seasonalAdjustment)
        assertEquals(EconomyValueBasis.REAL, measurement.valueBasis)
        assertEquals(EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"), stored.geography)
        assertEquals(listOf(EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)), stored.entities)
        assertEquals("Inflation evidence", stored.evidenceText)
        assertProposed(stored)
        assertEquals(0L, count("economy_fact"))
    }

    @Test
    fun `stores multiple proposals, accepts empty result and repeats by creating new proposals`() {
        val id = fetched()
        classify(id, "economy")
        client.response = response(event(), indicator())
        mvc.perform(extract(id)).andExpect(status().isOk).andExpect(jsonPath("$.proposalCount").value(2))
        assertEquals(2, stored(id).size)
        client.response = response()
        mvc.perform(extract(id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.proposalCount").value(0))
            .andExpect(jsonPath("$.proposals").isEmpty)
        assertEquals(2, stored(id).size)
        client.response = response(event())
        mvc.perform(extract(id)).andExpect(status().isOk).andExpect(jsonPath("$.proposalCount").value(1))
        assertEquals(3, stored(id).size)
        assertEquals(0L, count("economy_fact"))
    }

    @Test
    fun `checks fetched document and current economy classification before model call`() {
        val missing = UUID.randomUUID()
        val failedCandidate = discoveredEntity()
        documents.recordFailure(failedCandidate, SourceContentFailureReason.HTTP_ERROR)
        val notFetched = failedCandidate.id
        val unclassified = fetched()
        val oldVersion = fetched()
        classify(oldVersion, "economy", version = "old-version")
        val wrongSection = fetched()
        classify(wrongSection, "technology")
        val before = proposalCounts()

        assertProblem(missing, 404)
        assertProblem(notFetched, 409)
        assertProblem(unclassified, 404)
        assertProblem(oldVersion, 404)
        assertProblem(wrongSection, 409)
        assertEquals(0, client.calls)
        assertEquals(before, proposalCounts())
    }

    @Test
    fun `invalid model structures and proposal policy failures leave no partial batch`() {
        val id = fetched()
        classify(id, "economy")
        val before = proposalCounts()
        val responses = listOf(
            "not-json",
            response(event().replace("MONETARY_POLICY", "INVALID_CATEGORY")),
            response(indicator().replace(Regex("\"referencePeriod\":\\{[^}]+}"), "\"referencePeriod\":null")),
            response(event().replace("Rate decision evidence", "")),
            response(event().replace("Rate decision evidence", "Not in article")),
            response(event(), event().replace("Rate decision evidence", "Not in article")),
        )
        for (modelResponse in responses) {
            client.response = modelResponse
            mvc.perform(extract(id)).andExpect(status().isBadGateway)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").isNotEmpty)
            assertEquals(before, proposalCounts())
        }
    }

    @Test
    fun `model failure returns sanitized bad gateway without persistence`() {
        val id = fetched()
        classify(id, "economy")
        val before = proposalCounts()
        client.failure = IllegalStateException("secret article text and credentials")
        mvc.perform(extract(id)).andExpect(status().isBadGateway)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret article text"))))
        assertEquals(before, proposalCounts())
    }

    @Test
    fun `diagnostic records safe types but no exception content or secrets`() {
        val id = fetched()
        classify(id, "economy")
        val logger = LoggerFactory.getLogger(EconomyProposalExtractionService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            client.failure = IllegalStateException("ARTICLE_MARKER", IllegalArgumentException("API_KEY_MARKER"))
            mvc.perform(extract(id)).andExpect(status().isBadGateway)
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ARTICLE_MARKER"))))
            val events = appender.list.filter { it.formattedMessage.contains("economy_extraction_failed") }
            assertEquals(1, events.size)
            val event = events.single()
            assertTrue(event.formattedMessage.contains("documentId=$id"))
            assertTrue(event.formattedMessage.contains("phase=provider"))
            assertTrue(event.formattedMessage.contains("IllegalStateException,IllegalArgumentException"))
            assertFalse(event.formattedMessage.contains("ARTICLE_MARKER"))
            assertFalse(event.formattedMessage.contains("API_KEY_MARKER"))
            assertNull(event.throwableProxy)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun extract(id: UUID) = post("/api/v1/economy/fact-proposals/extractions/{sourceDocumentId}", id)

    private fun assertProblem(id: UUID, status: Int) {
        mvc.perform(extract(id))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().`is`(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(status))
    }

    private fun assertProposed(proposal: EconomyFactProposal) {
        assertEquals(EconomyFactProposalStatus.PROPOSED, proposal.status)
        assertNull(proposal.reviewedAt)
        assertNull(proposal.reviewedEvidenceLevel)
        assertNull(proposal.rejectionReason)
        assertNull(proposal.economyFactId)
    }

    private fun stored(id: UUID): List<EconomyFactProposal> = jdbc.query(
        "select id from economy_fact_proposal where source_document_id = ? order by created_at, id",
        { rows, _ -> proposals.find(rows.getObject(1, UUID::class.java))!! }, id,
    )

    private fun discoveredEntity(): dev.factweek.ingestion.internal.NewsCandidateEntity {
        val id = UUID.randomUUID()
        return candidates.storeDiscovered(CandidateCaptureCommand(
            sourceUrl = URI("https://example.org/$id"),
            title = "Economy article",
            publisher = "Example",
            publishedAt = clock.instant(),
            language = "de",
            discoveryProvider = CandidateDiscoveryProvider.MANUAL,
        ))
    }

    private fun fetched(): UUID = discoveredEntity().let { candidate ->
        documents.recordSuccess(candidate, SourceContentFetchResult(
            URI(candidate.canonicalUrl), "text/html", 200,
            "Rate decision evidence on ${LocalDate.now(clock)}. Inflation evidence for August 2026 was 2.1234567891 percent.", "a".repeat(64),
        ))
        candidate.id
    }

    private fun classify(id: UUID, vararg sections: String, version: String = "article-section-classification-v1") {
        jdbc.update(
            "insert into document_section_classification (source_document_id, classification_version, classified_at) values (?, ?, ?)",
            id, version, java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC),
        )
        sections.forEach { section ->
            jdbc.update(
                "insert into document_section_classification_section (source_document_id, classification_version, section_id) values (?, ?, ?)",
                id, version, section,
            )
        }
    }

    private fun response(vararg items: String) = """{"proposals":[${items.joinToString()}]}"""

    private fun event() = """{"statement":"The central bank kept its policy rate unchanged.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","occurredOn":"${LocalDate.now(clock)}","referencePeriod":null,"geography":{"kind":"COUNTRY","name":"Germany","code":"DE"},"measurement":null,"entities":[{"name":"Central Bank","type":"CENTRAL_BANK"}],"evidenceText":"Rate decision evidence"}"""

    private fun indicator() = """{"statement":"Inflation was reported for August 2026.","category":"PRICES_AND_INFLATION","eventType":"INDICATOR_VALUE_REPORTED","occurredOn":null,"referencePeriod":{"from":"2026-08-01","to":"2026-08-31","granularity":"MONTH"},"geography":{"kind":"COUNTRY","name":"Germany","code":"DE"},"measurement":{"value":"2.1234567891","unit":"PERCENT","releaseStatus":"FINAL","seasonalAdjustment":"ADJUSTED","valueBasis":"REAL"},"entities":[{"name":"Inflation","type":"INDICATOR"}],"evidenceText":"Inflation evidence"}"""

    private fun count(table: String): Long = jdbc.queryForObject("select count(*) from $table", Long::class.java)!!
    private fun proposalCounts() = listOf("economy_fact_proposal", "economy_fact_proposal_entity", "economy_fact_proposal_measurement").map(::count) + factCounts()
    private fun factCounts() = listOf("economy_fact", "economy_fact_entity", "economy_fact_source", "economy_fact_measurement").map(::count)

    @TestConfiguration
    internal class ModelBoundary {
        @Bean fun client() = StubEconomyModelClient()
        @Bean fun extractor(client: StubEconomyModelClient): EconomyProposalExtractor = OpenAiEconomyProposalExtractor(
            client,
            OpenAiEconomyProposalSettings("test-key", "test-model", 5, 8000),
        )
    }

    internal class StubEconomyModelClient : OpenAiEconomyProposalClient {
        var response = """{"proposals":[]}"""
        var failure: RuntimeException? = null
        var calls = 0
        var lastPrompt: OpenAiEconomyProposalPrompt? = null
        var transactionActiveWhenCalled = false
        private val converter = OpenAiEconomyProposalStructuredOutput()

        override fun extract(prompt: OpenAiEconomyProposalPrompt): OpenAiEconomyProposalResponse {
            calls++
            lastPrompt = prompt
            transactionActiveWhenCalled = TransactionSynchronizationManager.isActualTransactionActive()
            failure?.let { throw it }
            return try {
                converter.convert(response)
            } catch (exception: RuntimeException) {
                throw EconomyExtractionInvalidResponseException(exception)
            }
        }

        fun reset() {
            response = """{"proposals":[]}"""
            failure = null
            calls = 0
            lastPrompt = null
            transactionActiveWhenCalled = false
        }
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
