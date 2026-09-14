package dev.factweek.technology.internal

import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyFacts
import dev.factweek.technology.TechnologyReadiness
import dev.factweek.technology.PublishTechnologyFact
import dev.factweek.technology.SourceReference
import dev.factweek.technology.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    classes = [TechnologyFactRepositoryTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class TechnologyFactRepositoryTest {
    @Autowired private lateinit var repository: TechnologyFactRepository
    @Autowired private lateinit var technologyFacts: TechnologyFacts

    @BeforeEach
    fun clean() = repository.deleteAll()

    @Test
    fun `occurred between includes both boundaries and orders date descending then id ascending`() {
        val lowerBoundary = fact("00000000-0000-0000-0000-000000000003", LocalDate.of(2026, 9, 7))
        val sameDayLaterId = fact("00000000-0000-0000-0000-000000000002", LocalDate.of(2026, 9, 13))
        val sameDayFirstId = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 13))
        val before = fact("00000000-0000-0000-0000-000000000004", LocalDate.of(2026, 9, 6))
        val after = fact("00000000-0000-0000-0000-000000000005", LocalDate.of(2026, 9, 14))
        val undated = fact("00000000-0000-0000-0000-000000000006", null)
        repository.saveAll(listOf(lowerBoundary, sameDayLaterId, sameDayFirstId, before, after, undated))

        val results = technologyFacts.occurredBetween(
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 13),
        )

        assertEquals(listOf(sameDayFirstId.id, sameDayLaterId.id, lowerBoundary.id), results.map { it.id })
        assertNull(repository.findById(undated.id).orElseThrow().occurredOn)
    }

    @Test
    fun `briefing relevance uses occurred date first otherwise the earliest source publication in UTC`() {
        val occurredInside = fact(
            "00000000-0000-0000-0000-000000000010",
            LocalDate.of(2026, 9, 10),
            sources = listOf(source("2026-09-01T00:00:00Z")),
        )
        val occurredOutsideSourceInside = fact(
            "00000000-0000-0000-0000-000000000011",
            LocalDate.of(2026, 9, 1),
            sources = listOf(source("2026-09-10T00:00:00Z")),
        )
        val earliestInside = fact(
            "00000000-0000-0000-0000-000000000012",
            null,
            sources = listOf(source("2026-09-07T00:00:00Z"), source("2026-09-13T23:59:59Z")),
        )
        val earliestOutside = fact(
            "00000000-0000-0000-0000-000000000013",
            null,
            sources = listOf(source("2026-09-06T23:59:59Z"), source("2026-09-10T00:00:00Z")),
        )
        val withoutPublicationTime = fact(
            "00000000-0000-0000-0000-000000000014",
            null,
            sources = listOf(SourceValue("https://example.org/unknown", "Example", dev.factweek.technology.SourceType.PAPER)),
        )
        val duplicateCandidates = fact(
            "00000000-0000-0000-0000-000000000015",
            null,
            sources = listOf(source("2026-09-10T00:00:00Z"), source("2026-09-10T12:00:00Z")),
        )
        repository.saveAll(listOf(occurredInside, occurredOutsideSourceInside, earliestInside, earliestOutside, withoutPublicationTime, duplicateCandidates))

        val results = technologyFacts.relevantForBriefingBetween(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13))

        assertEquals(
            setOf(occurredInside.id, earliestInside.id, duplicateCandidates.id),
            results.map { it.id }.toSet(),
        )
        assertEquals(3, results.size)
    }

    @Test
    fun `direct publishing preserves each optional source publication timestamp`() {
        val firstPublication = Instant.parse("2026-09-10T00:00:00Z")
        val fact = technologyFacts.publish(
            PublishTechnologyFact(
                statement = "A directly published source-backed fact.",
                category = TechnologyCategory.AI_AND_SOFTWARE,
                eventType = TechnologyEventType.TECHNOLOGY_DEPLOYED,
                readiness = TechnologyReadiness.PRODUCTION_USE,
                evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
                occurredOn = null,
                entities = emptyList(),
                sources = listOf(
                    SourceReference("https://example.org/first", "Example", SourceType.PAPER, firstPublication),
                    SourceReference("https://example.org/unknown", "Example", SourceType.REPOSITORY),
                ),
            ),
        )

        val stored = repository.findById(fact.id).orElseThrow().toDomain()

        assertEquals(firstPublication, stored.sources.single { it.url.endsWith("/first") }.publishedAt)
        assertNull(stored.sources.single { it.url.endsWith("/unknown") }.publishedAt)
    }

    private fun fact(
        id: String,
        occurredOn: LocalDate?,
        sources: List<SourceValue> = emptyList(),
    ) = TechnologyFactEntity(
        id = UUID.fromString(id),
        statement = "A persisted technology fact.",
        category = TechnologyCategory.AI_AND_SOFTWARE,
        eventType = TechnologyEventType.TECHNOLOGY_DEPLOYED,
        readiness = TechnologyReadiness.PRODUCTION_USE,
        evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
        occurredOn = occurredOn,
        sources = sources.toMutableList(),
    )

    private fun source(publishedAt: String) = SourceValue(
        url = "https://example.org/${publishedAt.hashCode()}",
        publisher = "Example",
        sourceType = dev.factweek.technology.SourceType.PAPER,
        publishedAt = Instant.parse(publishedAt),
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [TechnologyFactEntity::class])
    @EnableJpaRepositories(basePackageClasses = [TechnologyFactRepository::class])
    @Import(TechnologyFactService::class)
    internal class TestApplication

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
