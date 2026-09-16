package dev.factweek.economy.internal

import dev.factweek.FactweekApplication
import dev.factweek.economy.*
import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateDiscoveryProvider
import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.ai.model.chat=none", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"],
)
@Testcontainers
class EconomyFactProposalPersistenceTest {
    @Autowired private lateinit var proposals: EconomyFactProposals
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var sourceDocuments: SourceDocumentPersistenceService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactions: TransactionTemplate

    @BeforeEach fun clean() {
        jdbc.update("delete from economy_fact_proposal_measurement")
        jdbc.update("delete from economy_fact_proposal_entity")
        jdbc.update("delete from economy_fact_proposal")
        jdbc.update("delete from source_document")
        jdbc.update("delete from news_candidate")
    }

    @Test fun `captures and reloads a discrete economy proposal`() {
        val candidate = candidates.storeDiscovered(CandidateCaptureCommand(
            sourceUrl = URI("https://example.org/rate-decision"), title = "Rate decision", publisher = "Example",
            publishedAt = Instant.parse("2026-09-10T00:00:00Z"), language = "de",
            discoveryProvider = CandidateDiscoveryProvider.MANUAL, sourceType = CandidateSourceType.NEWS_REPORT,
        ))
        sourceDocuments.recordSuccess(candidate, SourceContentFetchResult(URI(candidate.canonicalUrl), "text/html", 200, "The central bank decided to keep its policy rate unchanged.", "a".repeat(64)))
        val factCount = jdbc.queryForObject("select count(*) from economy_fact", Long::class.java)!!

        val captured = proposals.create(CreateEconomyFactProposal(candidate.id, "The central bank kept its policy rate unchanged.", EconomyCategory.MONETARY_POLICY, EconomyEventType.MONETARY_POLICY_DECIDED, occurredOn = LocalDate.of(2026, 9, 10), evidenceText = "central bank decided to keep its policy rate unchanged"))
        val reloaded = transactions.execute { proposals.find(captured.id) }!!

        assertEquals(captured.id, reloaded.id)
        assertEquals(candidate.id, reloaded.sourceDocumentId)
        assertEquals("The central bank kept its policy rate unchanged.", reloaded.statement)
        assertEquals(EconomyCategory.MONETARY_POLICY, reloaded.category)
        assertEquals(EconomyEventType.MONETARY_POLICY_DECIDED, reloaded.eventType)
        assertEquals(LocalDate.of(2026, 9, 10), reloaded.occurredOn)
        assertEquals("central bank decided to keep its policy rate unchanged", reloaded.evidenceText)
        assertEquals(EconomyFactProposalStatus.PROPOSED, reloaded.status)
        assertNotNull(reloaded.createdAt)
        assertNull(reloaded.measurement); assertNull(reloaded.referencePeriod); assertNull(reloaded.geography)
        assertTrue(reloaded.entities.isEmpty())
        assertNull(reloaded.reviewedAt); assertNull(reloaded.reviewedEvidenceLevel); assertNull(reloaded.rejectionReason); assertNull(reloaded.economyFactId)
        assertEquals(1L, jdbc.queryForObject("select count(*) from economy_fact_proposal where id = ?", Long::class.java, captured.id))
        assertEquals(factCount, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
        assertEquals(1L, jdbc.queryForObject("select count(*) from source_document where candidate_id = ?", Long::class.java, candidate.id))
    }

    @Test fun `captures and reloads a complete indicator without rounding`() {
        val candidate = fetchedCandidate("indicator", "The statistical authority reported inflation evidence.")
        val captured = proposals.create(indicator(candidate.id, "inflation evidence", optional = true))
        val reloaded = transactions.execute { proposals.find(captured.id) }!!

        assertEquals(candidate.id, reloaded.sourceDocumentId)
        assertNull(reloaded.occurredOn)
        assertEquals(0, BigDecimal("2.1234567891").compareTo(reloaded.measurement!!.value))
        assertEquals(EconomyMeasurementUnit.PERCENT, reloaded.measurement!!.unit)
        assertEquals(EconomyReleaseStatus.FINAL, reloaded.measurement!!.releaseStatus)
        assertEquals(EconomySeasonalAdjustment.ADJUSTED, reloaded.measurement!!.seasonalAdjustment)
        assertEquals(EconomyValueBasis.REAL, reloaded.measurement!!.valueBasis)
        assertEquals(EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH), reloaded.referencePeriod)
        assertEquals(EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"), reloaded.geography)
        assertEquals(setOf(EconomyEntityReference("Statistical Authority", EconomyEntityType.STATISTICAL_AUTHORITY), EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)), reloaded.entities.toSet())
        assertEquals(EconomyFactProposalStatus.PROPOSED, reloaded.status)
        assertNull(reloaded.reviewedAt)
    }

    @Test fun `keeps optional indicator values unknown`() {
        val candidate = fetchedCandidate("optional", "The statistical authority reported optional evidence.")
        val captured = proposals.create(indicator(candidate.id, "optional evidence", optional = false))
        val reloaded = transactions.execute { proposals.find(captured.id) }!!

        assertNotNull(reloaded.measurement)
        assertNotNull(reloaded.referencePeriod)
        assertNull(reloaded.measurement!!.releaseStatus)
        assertNull(reloaded.measurement!!.seasonalAdjustment)
        assertNull(reloaded.measurement!!.valueBasis)
        assertNull(reloaded.geography)
        assertNull(reloaded.occurredOn)
        assertTrue(reloaded.entities.isEmpty())
        assertEquals(EconomyFactProposalStatus.PROPOSED, reloaded.status)
    }

    @Test fun `stores multiple proposals for one document without publishing facts`() {
        val candidate = fetchedCandidate("multiple", "First evidence passage. Second evidence passage.")
        val factsBefore = jdbc.queryForObject("select count(*) from economy_fact", Long::class.java)!!
        val first = proposals.create(event(candidate.id, "First evidence passage."))
        val second = proposals.create(event(candidate.id, "Second evidence passage."))

        assertNotEquals(first.id, second.id)
        assertEquals(candidate.id, transactions.execute { proposals.find(first.id) }!!.sourceDocumentId)
        assertEquals(candidate.id, transactions.execute { proposals.find(second.id) }!!.sourceDocumentId)
        assertEquals(2L, jdbc.queryForObject("select count(*) from economy_fact_proposal where source_document_id = ?", Long::class.java, candidate.id))
        assertEquals(factsBefore, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
        assertEquals("First evidence passage. Second evidence passage.", jdbc.queryForObject("select text_content from source_document where candidate_id = ?", String::class.java, candidate.id))
        assertEquals("a".repeat(64), jdbc.queryForObject("select content_sha256 from source_document where candidate_id = ?", String::class.java, candidate.id))
    }

    @Test fun `rolls back proposal and measurement when database rejects dependent row`() {
        val candidate = fetchedCandidate("rollback", "The statistical authority reported rollback evidence.")
        jdbc.execute("create function reject_economy_proposal_measurement() returns trigger language plpgsql as $$ begin raise exception 'test measurement failure'; end; $$")
        jdbc.execute("create trigger reject_economy_proposal_measurement_trigger before insert on economy_fact_proposal_measurement for each row execute function reject_economy_proposal_measurement()")
        try {
            val exception = assertThrows<RuntimeException> { proposals.create(indicator(candidate.id, "rollback evidence", optional = false)) }
            assertTrue(generateSequence<Throwable>(exception) { it.cause }.any { it.message?.contains("test measurement failure") == true })
        } finally {
            jdbc.execute("drop trigger if exists reject_economy_proposal_measurement_trigger on economy_fact_proposal_measurement")
            jdbc.execute("drop function if exists reject_economy_proposal_measurement()")
        }
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact_proposal where source_document_id = ?", Long::class.java, candidate.id))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact_proposal_measurement", Long::class.java))
        assertEquals(1L, jdbc.queryForObject("select count(*) from source_document where candidate_id = ?", Long::class.java, candidate.id))
    }

    private fun fetchedCandidate(path: String, text: String) = candidates.storeDiscovered(CandidateCaptureCommand(
        sourceUrl = URI("https://example.org/$path"), title = path, publisher = "Example", publishedAt = Instant.parse("2026-09-10T00:00:00Z"), language = "de", discoveryProvider = CandidateDiscoveryProvider.MANUAL,
    )).also { sourceDocuments.recordSuccess(it, SourceContentFetchResult(URI(it.canonicalUrl), "text/html", 200, text, "a".repeat(64))) }

    private fun event(documentId: java.util.UUID, evidence: String) = CreateEconomyFactProposal(documentId, "A policy decision.", EconomyCategory.MONETARY_POLICY, EconomyEventType.MONETARY_POLICY_DECIDED, evidenceText = evidence)
    private fun indicator(documentId: java.util.UUID, evidence: String, optional: Boolean) = CreateEconomyFactProposal(
        documentId, "Inflation was reported.", EconomyCategory.PRICES_AND_INFLATION, EconomyEventType.INDICATOR_VALUE_REPORTED,
        referencePeriod = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH),
        geography = if (optional) EconomyGeography(EconomyGeographyKind.COUNTRY, " Germany ", "de") else null,
        measurement = EconomyMeasurement(BigDecimal("2.1234567891"), EconomyMeasurementUnit.PERCENT, if (optional) EconomyReleaseStatus.FINAL else null, if (optional) EconomySeasonalAdjustment.ADJUSTED else null, if (optional) EconomyValueBasis.REAL else null),
        entities = if (optional) listOf(EconomyEntityReference(" Statistical Authority ", EconomyEntityType.STATISTICAL_AUTHORITY), EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)) else emptyList(), evidenceText = evidence,
    )

    companion object {
        @Container @JvmStatic @ServiceConnection val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
