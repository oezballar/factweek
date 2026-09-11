package dev.factweek.technology.internal

import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(
    classes = [TechnologyFactRepositoryTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
@Testcontainers
class TechnologyFactRepositoryTest {
    @Autowired private lateinit var repository: TechnologyFactRepository

    @BeforeEach
    fun clean() = repository.deleteAll()

    @Test
    fun `occurred between includes both boundaries and orders date descending then id ascending`() {
        val lowerBoundary = fact("00000000-0000-0000-0000-000000000003", LocalDate.of(2026, 9, 7))
        val sameDayLaterId = fact("00000000-0000-0000-0000-000000000002", LocalDate.of(2026, 9, 13))
        val sameDayFirstId = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 13))
        val before = fact("00000000-0000-0000-0000-000000000004", LocalDate.of(2026, 9, 6))
        val after = fact("00000000-0000-0000-0000-000000000005", LocalDate.of(2026, 9, 14))
        repository.saveAll(listOf(lowerBoundary, sameDayLaterId, sameDayFirstId, before, after))

        val results = repository.findAllByOccurredOnBetweenOrderByOccurredOnDescIdAsc(
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 13),
        )

        assertEquals(listOf(sameDayFirstId.id, sameDayLaterId.id, lowerBoundary.id), results.map { it.id })
    }

    private fun fact(id: String, occurredOn: LocalDate) = TechnologyFactEntity(
        id = UUID.fromString(id),
        statement = "A persisted technology fact.",
        category = TechnologyCategory.AI_AND_SOFTWARE,
        eventType = TechnologyEventType.TECHNOLOGY_DEPLOYED,
        readiness = TechnologyReadiness.PRODUCTION_USE,
        evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
        occurredOn = occurredOn,
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [TechnologyFactEntity::class])
    @EnableJpaRepositories(basePackageClasses = [TechnologyFactRepository::class])
    internal class TestApplication

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
