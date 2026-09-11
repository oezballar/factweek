package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.GdeltCandidates
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
import java.time.Instant

@SpringBootTest(
    classes = [GdeltImportServiceTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class GdeltImportServiceTest {
    @Autowired
    private lateinit var service: GdeltImportService

    @Autowired
    private lateinit var candidates: StubGdeltCandidates

    @Autowired
    private lateinit var repository: NewsCandidateRepository

    @BeforeEach
    fun cleanDatabase() {
        repository.deleteAll()
        candidates.response = emptyList()
        candidates.failure = null
        candidates.calls = 0
    }

    @Test
    fun `stores candidates and reports new then existing counts for an identical import`() {
        val from = Instant.parse("2026-09-01T00:00:00Z")
        val to = Instant.parse("2026-09-02T00:00:00Z")
        candidates.response = listOf(candidate("one"), candidate("two"))

        val first = service.import("technology", from, to, 25)
        val second = service.import("technology", from, to, 25)

        assertEquals(2, first.discoveredCount)
        assertEquals(2, first.storedCount)
        assertEquals(0, first.existingCount)
        assertEquals(2, second.discoveredCount)
        assertEquals(0, second.storedCount)
        assertEquals(2, second.existingCount)
        assertEquals(2, repository.count())
    }

    @Test
    fun `rejects invalid time windows and maximum values`() {
        val from = Instant.parse("2026-09-01T00:00:00Z")
        val to = Instant.parse("2026-09-02T00:00:00Z")

        assertThrows<InvalidGdeltImportRequestException> { service.import("technology", to, from, 25) }
        assertThrows<InvalidGdeltImportRequestException> { service.import("technology", from, from.plusSeconds(7 * 24 * 60 * 60 + 1), 25) }
        assertThrows<InvalidGdeltImportRequestException> { service.import("technology", from, to, 0) }
        assertThrows<InvalidGdeltImportRequestException> { service.import("technology", from, to, 251) }
        assertEquals(0, candidates.calls)
    }

    @Test
    fun `does not persist candidates when GDELT retrieval fails`() {
        candidates.failure = GdeltRequestException("GDELT is unavailable")

        assertThrows<GdeltRequestException> {
            service.import(
                "technology",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                25,
            )
        }

        assertEquals(0, repository.count())
    }

    private fun candidate(path: String) = GdeltCandidate(
        title = "Candidate $path",
        url = URI.create("https://example.org/$path"),
        sourceCountry = "DE",
        language = "de",
        discoveredAt = Instant.parse("2026-09-01T12:00:00Z"),
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = [NewsCandidateRepository::class])
    @Import(
        CandidatePersistenceService::class,
        CandidateWriter::class,
        CandidateUrlNormalizer::class,
        GdeltImportService::class,
        TestConfiguration::class,
    )
    internal class TestApplication

    internal class TestConfiguration {
        @Bean
        fun gdeltCandidates(): StubGdeltCandidates = StubGdeltCandidates()

        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

        @Bean
        fun clock(): java.time.Clock = java.time.Clock.systemUTC()

    }

    internal class StubGdeltCandidates : GdeltCandidates {
        var response: List<GdeltCandidate> = emptyList()
        var failure: RuntimeException? = null
        var calls: Int = 0

        override fun findTechnologyCandidates(query: String, from: Instant, to: Instant, maximum: Int): List<GdeltCandidate> {
            calls++
            failure?.let { throw it }
            return response
        }
    }

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
