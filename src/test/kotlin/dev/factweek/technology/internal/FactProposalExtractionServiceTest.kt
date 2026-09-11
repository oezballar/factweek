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
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    @Autowired private lateinit var attempts: FactProposalExtractionAttemptRepository
    @Autowired private lateinit var extractor: StubExtractor

    @BeforeEach
    fun clean() {
        attempts.deleteAll()
        proposals.deleteAll()
        documents.deleteAll()
        candidates.deleteAll()
        extractor.response = listOf(validProposal())
        extractor.metadataValue = FactProposalExtractionMetadata("stub-model", "v1")
        extractor.failure = null
        extractor.calls.set(0)
        extractor.transactionActiveDuringExtraction = false
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
        assertEquals(0, second.selectedCount)
        assertEquals(0, second.skippedCount)
        assertEquals(1, proposals.count())
        assertEquals(1, extractor.calls.get())
        assertFalse(extractor.transactionActiveDuringExtraction)
    }

    @Test
    fun `rejects invalid extracted proposals without persisting them`() {
        fetchedCandidate("invalid")
        extractor.response = listOf(validProposal(statement = " "))

        val result = extraction.extract(10)

        assertEquals(1, result.rejectedCount)
        assertEquals(0, proposals.count())

        extraction.extract(10)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun `empty extraction results are recorded and not retried for the same schema`() {
        fetchedCandidate("empty")
        extractor.response = emptyList()

        val first = extraction.extract(10)
        val second = extraction.extract(10)

        assertEquals(1, first.selectedCount)
        assertEquals(0, first.proposedCount)
        assertEquals(0, first.rejectedCount)
        assertEquals(0, second.selectedCount)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun `does not process source documents that were not fetched successfully`() {
        val candidate = candidatePersistence.storeDiscovered(candidate("failed-source"))
        sourcePersistence.recordFailure(candidate, SourceContentFailureReason.HTTP_ERROR)

        val result = extraction.extract(10)

        assertEquals(0, result.selectedCount)
        assertEquals(0, extractor.calls.get())
    }

    @Test
    fun `propagates unexpected extractor failures`() {
        fetchedCandidate("failure")
        extractor.failure = IllegalStateException("extractor configuration failed")

        assertThrows<IllegalStateException> { extraction.extract(10) }

        assertEquals(0, proposals.count())
        assertEquals(FactProposalExtractionAttemptStatus.FAILED, attempts.findAll().single().status)
    }

    @Test
    fun `marks controlled OpenAI adapter failures as failed attempts`() {
        fetchedCandidate("openai-output-limit")
        extractor.failure = OpenAiFactProposalAdapterException("OpenAI returned more proposals than requested")

        assertThrows<OpenAiFactProposalAdapterException> { extraction.extract(10) }

        assertEquals(FactProposalExtractionAttemptStatus.FAILED, attempts.findAll().single().status)
        assertEquals(0, proposals.count())
    }

    @Test
    fun `failed extraction attempts are retried immediately`() {
        fetchedCandidate("retry")
        extractor.failure = IllegalStateException("temporary extractor failure")

        assertThrows<IllegalStateException> { extraction.extract(10) }
        extractor.failure = null

        val result = extraction.extract(10)

        assertEquals(1, result.proposedCount)
        assertEquals(2, extractor.calls.get())
        assertEquals(FactProposalExtractionAttemptStatus.COMPLETED, attempts.findAll().single().status)
    }

    @Test
    fun `expired claims can be claimed again`() {
        val candidate = fetchedCandidate("expired-claim")
        attempts.save(
            FactProposalExtractionAttemptEntity(
                id = java.util.UUID.randomUUID(),
                sourceDocumentId = candidate.id,
                extractionModel = "old-model",
                extractionSchemaVersion = "v1",
                createdAt = Instant.parse("2026-09-09T00:00:00Z"),
                status = FactProposalExtractionAttemptStatus.CLAIMED,
                claimedAt = Instant.parse("2026-09-09T00:00:00Z"),
            ),
        )

        val result = extraction.extract(10)

        assertEquals(1, result.proposedCount)
        assertEquals(FactProposalExtractionAttemptStatus.COMPLETED, attempts.findAll().single().status)
    }

    @Test
    fun `empty and invalid results complete their attempts`() {
        fetchedCandidate("completed-empty")
        extractor.response = emptyList()
        extraction.extract(10)
        assertEquals(FactProposalExtractionAttemptStatus.COMPLETED, attempts.findAll().single().status)

        fetchedCandidate("completed-invalid")
        extractor.response = listOf(validProposal(statement = " "))
        extraction.extract(10)
        assertEquals(
            setOf(FactProposalExtractionAttemptStatus.COMPLETED),
            attempts.findAll().map { it.status }.toSet(),
        )
    }

    @Test
    fun `persistence failure does not complete the attempt`() {
        fetchedCandidate("persistence-failure")
        extractor.response = listOf(validProposal(entityName = "bad\u0000entity"))

        assertThrows<RuntimeException> { extraction.extract(10) }

        assertEquals(0, proposals.count())
        assertEquals(FactProposalExtractionAttemptStatus.FAILED, attempts.findAll().single().status)
    }

    @Test
    fun `processed documents beyond maximum do not block a new document`() {
        repeat(30) { fetchedCandidate("old-$it") }
        extraction.extract(25)
        extraction.extract(25)
        val newest = fetchedCandidate("new")

        val result = extraction.extract(1)

        assertEquals(1, result.selectedCount)
        assertEquals(1, result.proposedCount)
        assertEquals(newest.id, proposals.findAll().single { it.sourceDocumentId == newest.id }.sourceDocumentId)
    }

    @Test
    fun `a new schema version can process the same source document again`() {
        fetchedCandidate("schema")
        extraction.extract(10)
        extractor.metadataValue = FactProposalExtractionMetadata("stub-model", "v2")

        val result = extraction.extract(10)

        assertEquals(1, result.selectedCount)
        assertEquals(1, result.proposedCount)
        assertEquals(2, proposals.count())
    }

    @Test
    fun `counts each valid and invalid proposal independently`() {
        fetchedCandidate("mixed")
        extractor.response = listOf(
            validProposal(statement = "One valid claim."),
            validProposal(statement = "Paraphrased claim.", evidence = "Not in the source."),
            validProposal(statement = "Two valid claim."),
            validProposal(statement = "Three valid claim."),
        )

        val result = extraction.extract(10)

        assertEquals(3, result.proposedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(3, proposals.count())
    }

    @Test
    fun `stores valid proposals while rejecting invalid proposals from the same document`() {
        fetchedCandidate("one-valid-one-invalid")
        extractor.response = listOf(
            validProposal(statement = "A supported claim."),
            validProposal(statement = "An unsupported claim.", evidence = "Paraphrased evidence."),
        )

        val result = extraction.extract(10)

        assertEquals(1, result.proposedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(1, proposals.count())
    }

    @Test
    fun `rejects evidence that is not a source excerpt`() {
        fetchedCandidate("invented-evidence")
        extractor.response = listOf(validProposal(evidence = "This is an invented paraphrase."))

        val result = extraction.extract(10)

        assertEquals(0, result.proposedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(0, proposals.count())
    }

    @Test
    fun `concurrent extraction claims a source document only once`() {
        fetchedCandidate("concurrent")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = executor.invokeAll(List(2) { Callable { extraction.extract(10) } })
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, proposals.count())
        assertEquals(1, extractor.calls.get())
    }

    private fun fetchedCandidate(path: String) = candidatePersistence.storeDiscovered(candidate(path)).also { candidate ->
        sourcePersistence.recordSuccess(
            candidate,
            SourceContentFetchResult(
                URI.create(candidate.canonicalUrl),
                "text/html",
                200,
                "The source explicitly reports a measurable technology result. Additional stored source content for $path.",
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

    private fun validProposal(
        statement: String = "A laboratory demonstrated a measurable technology result.",
        evidence: String = "The source explicitly reports a measurable technology result.",
        entityName: String = "Example battery",
    ) = ExtractedFactProposal(
        statement = statement,
        category = TechnologyCategory.ENERGY_AND_CLIMATE,
        entities = listOf(EntityReference(entityName, EntityType.TECHNOLOGY)),
        occurredOn = LocalDate.of(2026, 9, 1),
        evidenceText = evidence,
        evidenceLevel = EvidenceLevel.DOCUMENTED,
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
        FactProposalExtractionAttemptService::class,
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
        override var metadata: FactProposalExtractionMetadata
            get() = metadataValue
            set(value) { metadataValue = value }
        var metadataValue = FactProposalExtractionMetadata("stub-model", "v1")
        var response: List<ExtractedFactProposal> = emptyList()
        var failure: RuntimeException? = null
        val calls = AtomicInteger()
        var transactionActiveDuringExtraction: Boolean = false

        override fun extract(request: FactProposalExtractionRequest): List<ExtractedFactProposal> {
            calls.incrementAndGet()
            transactionActiveDuringExtraction = TransactionSynchronizationManager.isActualTransactionActive()
            failure?.let { throw it }
            return response
        }
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
