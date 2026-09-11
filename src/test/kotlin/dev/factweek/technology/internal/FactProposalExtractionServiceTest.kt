package dev.factweek.technology.internal

import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.CandidateWriter
import dev.factweek.ingestion.internal.NewsCandidateRepository
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import dev.factweek.ingestion.internal.SourceDocumentQueryService
import dev.factweek.ingestion.internal.SourceDocumentRepository
import dev.factweek.ingestion.internal.SourceContentFailureReason
import dev.factweek.technology.EntityReference
import dev.factweek.technology.EntityType
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest(
    classes = [FactProposalExtractionServiceTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class FactProposalExtractionServiceTest {
    @Autowired private lateinit var extraction: FactProposalExtractionService
    @Autowired private lateinit var candidatePersistence: CandidatePersistenceService
    @Autowired private lateinit var sourcePersistence: SourceDocumentPersistenceService
    @Autowired private lateinit var candidates: NewsCandidateRepository
    @Autowired private lateinit var documents: SourceDocumentRepository
    @Autowired private lateinit var proposals: FactProposalRepository
    @Autowired private lateinit var extractor: StubExtractor

    @BeforeEach
    fun clean() {
        proposals.deleteAll()
        documents.deleteAll()
        candidates.deleteAll()
        extractor.response = listOf(validProposal())
        extractor.failure = null
        extractor.calls = 0
    }

    @Test
    fun `stores proposals only for successfully fetched source documents and skips repeats`() {
        val candidate = fetchedCandidate("successful")

        val first = extraction.extract(10)
        val second = extraction.extract(10)

        assertEquals(1, first.selectedCount)
        assertEquals(1, first.proposedCount)
        assertEquals(FactProposalStatus.PROPOSED, proposals.findAll().single().status)
        assertEquals(candidate.id, proposals.findAll().single().sourceDocumentId)
        assertEquals(1, second.selectedCount)
        assertEquals(1, second.skippedCount)
        assertEquals(1, proposals.count())
        assertEquals(1, extractor.calls)
    }

    @Test
    fun `rejects invalid extracted proposals without persisting them`() {
        fetchedCandidate("invalid")
        extractor.response = listOf(validProposal(statement = " "))

        val result = extraction.extract(10)

        assertEquals(1, result.rejectedCount)
        assertEquals(0, proposals.count())
    }

    @Test
    fun `does not process source documents that were not fetched successfully`() {
        val candidate = candidatePersistence.storeDiscovered(candidate("failed-source"))
        sourcePersistence.recordFailure(candidate, SourceContentFailureReason.HTTP_ERROR)

        val result = extraction.extract(10)

        assertEquals(0, result.selectedCount)
        assertEquals(0, extractor.calls)
    }

    @Test
    fun `propagates unexpected extractor failures`() {
        fetchedCandidate("failure")
        extractor.failure = IllegalStateException("extractor configuration failed")

        assertThrows<IllegalStateException> { extraction.extract(10) }

        assertEquals(0, proposals.count())
    }

    private fun fetchedCandidate(path: String) = candidatePersistence.storeDiscovered(candidate(path)).also { candidate ->
        sourcePersistence.recordSuccess(
            candidate,
            SourceContentFetchResult(
                URI.create(candidate.canonicalUrl),
                "text/html",
                200,
                "Stored source content for $path with sufficient text.",
                "a".repeat(64),
            ),
        )
    }

    private fun candidate(path: String) = GdeltCandidate(
        title = "Candidate $path",
        url = URI.create("https://example.org/$path"),
        sourceCountry = "DE",
        language = "de",
        discoveredAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun validProposal(statement: String = "A laboratory demonstrated a measurable technology result.") = ExtractedFactProposal(
        statement = statement,
        category = TechnologyCategory.ENERGY_AND_CLIMATE,
        entities = listOf(EntityReference("Example battery", EntityType.TECHNOLOGY)),
        occurredOn = LocalDate.of(2026, 9, 1),
        evidenceText = "The source explicitly reports a measurable technology result.",
        evidenceLevel = EvidenceLevel.DOCUMENTED,
        extractionModel = "stub-model",
        extractionSchemaVersion = "v1",
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [FactProposalEntity::class, dev.factweek.ingestion.internal.SourceDocumentEntity::class])
    @EnableJpaRepositories(basePackageClasses = [FactProposalRepository::class, SourceDocumentRepository::class])
    @Import(
        CandidatePersistenceService::class,
        CandidateWriter::class,
        SourceDocumentPersistenceService::class,
        SourceDocumentQueryService::class,
        FactProposalExtractionService::class,
        TestConfiguration::class,
    )
    internal class TestApplication

    internal class TestConfiguration {
        @Bean fun factProposalExtractor(): StubExtractor = StubExtractor()
        @Bean fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
        @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC)
    }

    internal class StubExtractor : FactProposalExtractor {
        var response: List<ExtractedFactProposal> = emptyList()
        var failure: RuntimeException? = null
        var calls: Int = 0

        override fun extract(request: FactProposalExtractionRequest): List<ExtractedFactProposal> {
            calls++
            failure?.let { throw it }
            return response
        }
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
