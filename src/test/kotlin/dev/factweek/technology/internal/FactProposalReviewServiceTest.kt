package dev.factweek.technology.internal

import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateDiscoveryProvider
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.CandidateWriter
import dev.factweek.ingestion.internal.NewsCandidateRepository
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import dev.factweek.ingestion.internal.SourceDocumentQueryService
import dev.factweek.ingestion.internal.SourceDocumentRepository
import dev.factweek.technology.EntityType
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
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
import org.springframework.jdbc.core.JdbcTemplate
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

@SpringBootTest(
    classes = [FactProposalReviewServiceTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class FactProposalReviewServiceTest {
    @Autowired private lateinit var reviews: FactProposalReviewService
    @Autowired private lateinit var proposals: FactProposalRepository
    @Autowired private lateinit var technologyFacts: TechnologyFactRepository
    @Autowired private lateinit var candidatePersistence: CandidatePersistenceService
    @Autowired private lateinit var sourcePersistence: SourceDocumentPersistenceService
    @Autowired private lateinit var documents: SourceDocumentRepository
    @Autowired private lateinit var candidates: NewsCandidateRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clean() {
        proposals.deleteAll()
        technologyFacts.deleteAll()
        documents.deleteAll()
        candidates.deleteAll()
    }

    @Test
    fun `accepting a proposed fact creates exactly one fact with copied source and entities`() {
        val proposal = proposed()

        val result = reviews.accept(proposal.id, acceptCommand())

        val storedProposal = proposals.findById(proposal.id).orElseThrow()
        val fact = technologyFacts.findById(result.technologyFactId).orElseThrow()
        assertEquals(FactProposalStatus.ACCEPTED, storedProposal.status)
        assertEquals(result.technologyFactId, storedProposal.technologyFactId)
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), storedProposal.reviewedAt)
        assertEquals(1, technologyFacts.count())
        assertEquals(proposal.statement, fact.statement)
        assertEquals(proposal.entities.toList(), fact.entities.toList())
        assertEquals("https://example.org/review", fact.sources.single().url)
        assertEquals("example.org", fact.sources.single().publisher)
    }

    @Test
    fun `rejecting a proposed fact stores a reason and creates no technology fact`() {
        val proposal = proposed()

        val result = reviews.reject(proposal.id, "Insufficient independent corroboration")

        val storedProposal = proposals.findById(proposal.id).orElseThrow()
        assertEquals(FactProposalStatus.REJECTED, result.proposalStatus)
        assertEquals("Insufficient independent corroboration", storedProposal.rejectionReason)
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), storedProposal.reviewedAt)
        assertEquals(0, technologyFacts.count())
    }

    @Test
    fun `only proposed facts can be decided`() {
        val accepted = proposed("accepted")
        reviews.accept(accepted.id, acceptCommand())
        assertThrows<FactProposalReviewConflictException> { reviews.accept(accepted.id, acceptCommand()) }
        assertThrows<FactProposalReviewConflictException> { reviews.reject(accepted.id, "Too late") }

        val rejected = proposed("rejected")
        reviews.reject(rejected.id, "Rejected")
        assertThrows<FactProposalReviewConflictException> { reviews.accept(rejected.id, acceptCommand()) }
    }

    @Test
    fun `reject validation and unknown proposals fail predictably`() {
        assertThrows<InvalidFactProposalReviewRequestException> { reviews.reject(java.util.UUID.randomUUID(), " ") }
        assertThrows<FactProposalNotFoundException> { reviews.reject(java.util.UUID.randomUUID(), "Reason") }
    }

    @Test
    fun `concurrent accept creates at most one technology fact`() {
        val proposal = proposed("concurrent")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(List(2) { Callable { runCatching { reviews.accept(proposal.id, acceptCommand()) } } })
            results.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, technologyFacts.count())
        assertEquals(FactProposalStatus.ACCEPTED, proposals.findById(proposal.id).orElseThrow().status)
    }

    @Test
    fun `a persistence failure rolls back the generated technology fact`() {
        val proposal = proposed("transaction-rollback")
        jdbcTemplate.execute(
            """
            CREATE FUNCTION reject_fact_proposal_review() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
                RAISE EXCEPTION 'forced review update failure';
            END;
            $$
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            CREATE TRIGGER reject_fact_proposal_review
            BEFORE UPDATE ON fact_proposal
            FOR EACH ROW EXECUTE FUNCTION reject_fact_proposal_review()
            """.trimIndent(),
        )

        try {
            assertThrows<RuntimeException> { reviews.accept(proposal.id, acceptCommand()) }
            assertEquals(0, technologyFacts.count())
            assertEquals(FactProposalStatus.PROPOSED, proposals.findById(proposal.id).orElseThrow().status)
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_fact_proposal_review ON fact_proposal")
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_fact_proposal_review()")
        }
    }

    private fun proposed(path: String = "review"): FactProposalEntity {
        val candidate = candidatePersistence.storeDiscovered(
            CandidateCaptureCommand(
                title = "Review candidate $path",
                sourceUrl = URI.create("https://example.org/$path"),
                publisher = "Example",
                language = "en",
                publishedAt = Instant.parse("2026-09-01T00:00:00Z"),
                discoveryProvider = CandidateDiscoveryProvider.GDELT,
            ),
        )
        sourcePersistence.recordSuccess(
            candidate,
            SourceContentFetchResult(
                URI.create("https://example.org/review"),
                "text/html",
                200,
                "A source document with sufficient text.",
                "a".repeat(64),
            ),
        )
        return proposals.save(
            FactProposalEntity(
                sourceDocumentId = candidate.id,
                statement = "A battery reached a new efficiency threshold.",
                category = TechnologyCategory.ENERGY_AND_CLIMATE,
                occurredOn = LocalDate.of(2026, 9, 1),
                evidenceText = "A battery reached a new efficiency threshold.",
                evidenceLevel = EvidenceLevel.DOCUMENTED,
                extractionModel = "test-model",
                extractionSchemaVersion = "v1-$path",
                createdAt = Instant.parse("2026-09-01T00:00:00Z"),
                entities = mutableListOf(EntityValue("Example battery", EntityType.TECHNOLOGY)),
            ),
        )
    }

    private fun acceptCommand() = AcceptFactProposal(
        TechnologyEventType.PERFORMANCE_RECORD_VERIFIED,
        TechnologyReadiness.PROTOTYPE,
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [FactProposalEntity::class, TechnologyFactEntity::class, dev.factweek.ingestion.internal.SourceDocumentEntity::class])
    @EnableJpaRepositories(basePackageClasses = [FactProposalRepository::class, TechnologyFactRepository::class, SourceDocumentRepository::class])
    @Import(
        CandidatePersistenceService::class,
        CandidateWriter::class,
        dev.factweek.ingestion.internal.CandidateUrlNormalizer::class,
        SourceDocumentPersistenceService::class,
        SourceDocumentQueryService::class,
        FactProposalReviewService::class,
        TestConfiguration::class,
    )
    internal class TestApplication

    internal class TestConfiguration {
        @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC)
        @Bean fun meterRegistry() = SimpleMeterRegistry()
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
