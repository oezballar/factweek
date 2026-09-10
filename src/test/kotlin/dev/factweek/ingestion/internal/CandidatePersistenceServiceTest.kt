package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [CandidatePersistenceServiceTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@Testcontainers
class CandidatePersistenceServiceTest {
    @Autowired
    private lateinit var service: CandidatePersistenceService

    @Autowired
    private lateinit var repository: NewsCandidateRepository

    @BeforeEach
    fun cleanDatabase() {
        repository.deleteAll()
    }

    @Test
    fun `stores a discovered candidate`() {
        val publishedAt = Instant.parse("2026-09-10T08:15:30Z")

        val stored = service.storeDiscovered(
            GdeltCandidate(
                title = "  Breakthrough battery enters pilot production  ",
                url = URI.create("HTTPS://WWW.Example.com:443/news/story?utm_source=gdelt#section"),
                sourceCountry = "US",
                language = "en",
                discoveredAt = publishedAt,
            ),
        )

        assertNotNull(stored.id)
        assertEquals("https://www.example.com/news/story?utm_source=gdelt", stored.canonicalUrl)
        assertEquals("Breakthrough battery enters pilot production", stored.title)
        assertEquals("example.com", stored.sourceDomain)
        assertEquals(publishedAt, stored.publishedAt)
        assertNotNull(stored.fetchedAt)
        assertEquals(CandidateStatus.DISCOVERED, stored.status)
        assertEquals(1, repository.count())
    }

    @Test
    fun `does not duplicate candidates with the same canonical URL`() {
        val first = GdeltCandidate(
            title = "First title wins",
            url = URI.create("https://example.org/article#first"),
            sourceCountry = "DE",
            language = "de",
            discoveredAt = Instant.parse("2026-09-10T08:00:00Z"),
        )
        val duplicate = first.copy(
            title = "Duplicate title is ignored",
            url = URI.create("HTTPS://EXAMPLE.ORG:443/article#second"),
        )

        val stored = service.storeDiscovered(listOf(first, duplicate))

        assertEquals(stored[0].id, stored[1].id)
        assertEquals(1, repository.count())
        assertEquals(1, repository.countByCanonicalUrl("https://example.org/article"))
        assertEquals("First title wins", repository.findByCanonicalUrl("https://example.org/article")?.title)
    }

    @Test
    fun `concurrent stores of the same canonical URL return one persisted candidate`() {
        val candidate = GdeltCandidate(
            title = "Concurrent candidate",
            url = URI.create("https://example.org/concurrent#first"),
            sourceCountry = "DE",
            language = "de",
            discoveredAt = Instant.parse("2026-09-10T08:00:00Z"),
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val results: List<Future<NewsCandidateEntity>> = List(2) {
                executor.submit<NewsCandidateEntity> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "Concurrent store did not start" }
                    service.storeDiscovered(candidate)
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val stored = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(stored[0].id, stored[1].id)
            assertEquals(1, repository.countByCanonicalUrl("https://example.org/concurrent"))
            assertEquals(1, repository.count())
        } finally {
            executor.shutdownNow()
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = [NewsCandidateRepository::class])
    @Import(CandidatePersistenceService::class, CandidateWriter::class)
    internal class TestApplication

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
