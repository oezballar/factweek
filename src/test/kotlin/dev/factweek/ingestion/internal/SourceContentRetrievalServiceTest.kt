package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
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
import java.time.ZoneOffset

@SpringBootTest(
    classes = [SourceContentRetrievalServiceTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class SourceContentRetrievalServiceTest {
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var service: SourceContentRetrievalService
    @Autowired private lateinit var documents: SourceDocumentRepository
    @Autowired private lateinit var candidateRepository: NewsCandidateRepository
    @Autowired private lateinit var fetcher: StubFetcher

    @BeforeEach
    fun clean() {
        documents.deleteAll()
        candidateRepository.deleteAll()
        fetcher.failPaths = emptySet()
        fetcher.unexpectedPaths = emptySet()
        fetcher.calls.clear()
    }

    @Test
    fun `persists successful content and does not download it again`() {
        val candidate = candidates.storeDiscovered(candidate("success"))

        val first = service.retrieve(10, retryFailed = false)
        val second = service.retrieve(10, retryFailed = false)

        val stored = documents.findById(candidate.id).orElseThrow()
        assertEquals(1, first.selectedCount)
        assertEquals(1, first.fetchedCount)
        assertEquals(SourceDocumentStatus.FETCHED, stored.status)
        assertEquals("Stored source content for success with enough useful words.", stored.textContent)
        assertEquals(1, stored.attemptCount)
        assertEquals(0, second.selectedCount)
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun `continues after a failed candidate and retries only when requested`() {
        candidates.storeDiscovered(candidate("failure"))
        candidates.storeDiscovered(candidate("success"))
        fetcher.failPaths = setOf("failure")

        val first = service.retrieve(10, retryFailed = false)
        val skipped = service.retrieve(10, retryFailed = false)
        fetcher.failPaths = emptySet()
        val retried = service.retrieve(10, retryFailed = true)

        assertEquals(1, first.fetchedCount)
        assertEquals(1, first.failedCount)
        assertEquals(0, skipped.selectedCount)
        assertEquals(0, skipped.skippedCount)
        assertEquals(1, retried.fetchedCount)
        assertEquals(2, documents.count())
        assertEquals(3, fetcher.calls.size)
    }

    @Test
    fun `prioritizes never fetched candidates over many failed candidates`() {
        repeat(10) { index -> candidates.storeDiscovered(candidate("failed-$index", Instant.parse("2026-09-01T00:00:00Z"))) }
        fetcher.failPaths = (0 until 10).map { "failed-$it" }.toSet()
        service.retrieve(10, retryFailed = false)
        candidates.storeDiscovered(candidate("new", Instant.parse("2026-09-10T00:00:00Z")))
        fetcher.calls.clear()

        val result = service.retrieve(2, retryFailed = true)

        assertEquals(2, result.selectedCount)
        assertEquals("/new", fetcher.calls.first())
    }

    @Test
    fun `propagates unexpected fetcher failures without persisting a failed document`() {
        val candidate = candidates.storeDiscovered(candidate("unexpected"))
        fetcher.unexpectedPaths = setOf("unexpected")

        assertThrows<IllegalStateException> { service.retrieve(10, retryFailed = false) }

        assertEquals(false, documents.existsById(candidate.id))
    }

    private fun candidate(path: String, discoveredAt: Instant = Instant.parse("2026-09-01T12:00:00Z")) = GdeltCandidate(
        title = "Candidate $path",
        url = URI.create("https://example.org/$path"),
        sourceCountry = "DE",
        language = "de",
        discoveredAt = discoveredAt,
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = [NewsCandidateRepository::class])
    @Import(
        CandidatePersistenceService::class,
        CandidateWriter::class,
        SourceDocumentPersistenceService::class,
        SourceContentRetrievalService::class,
        TestConfiguration::class,
    )
    internal class TestApplication

    internal class TestConfiguration {
        @Bean fun sourceContentFetcher(): StubFetcher = StubFetcher()
        @Bean fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
        @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC)
    }

    internal class StubFetcher : SourceContentFetcher {
        var failPaths: Set<String> = emptySet()
        var unexpectedPaths: Set<String> = emptySet()
        val calls = mutableListOf<String>()

        override fun fetch(sourceUrl: URI): SourceContentFetchResult {
            calls += sourceUrl.path
            if (sourceUrl.path.removePrefix("/") in unexpectedPaths) throw IllegalStateException("unexpected test failure")
            if (sourceUrl.path.removePrefix("/") in failPaths) throw SourceContentFetchException(SourceContentFailureReason.HTTP_ERROR)
            val text = "Stored source content for ${sourceUrl.path.removePrefix("/")} with enough useful words."
            return SourceContentFetchResult(sourceUrl, "text/html", 200, text, "a".repeat(64))
        }
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
