package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import jakarta.persistence.EntityManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(classes = [EconomyFactRepositoryTest.TestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@Testcontainers
class EconomyFactRepositoryTest {
    @Autowired private lateinit var facts: EconomyFacts
    @Autowired private lateinit var repository: EconomyFactRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var entityManager: EntityManager

    @BeforeEach fun clean() = repository.deleteAll()

    @Test fun `round trips discrete events and optional source timestamps`() {
        val published = facts.publish(event())
        val stored = repository.findById(published.id).orElseThrow().toDomain()
        assertEquals("A central bank decided its policy rate.", stored.statement)
        assertEquals(LocalDate.of(2026, 9, 10), stored.occurredOn)
        assertNull(stored.measurement)
        assertEquals(Instant.parse("2026-09-10T08:00:00Z"), stored.sources.single().publishedAt)
    }

    @Test fun `round trips periodic measurements and preserves null classifications`() {
        val fact = facts.publish(indicator())
        val stored = repository.findById(fact.id).orElseThrow().toDomain()
        assertEquals(EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH), stored.referencePeriod)
        assertEquals(0, BigDecimal("2.4").compareTo(stored.measurement!!.value))
        assertEquals(EconomyMeasurementUnit.PERCENT, stored.measurement!!.unit)
        assertNull(stored.measurement!!.releaseStatus)
        assertNull(stored.measurement!!.seasonalAdjustment)
        assertNull(stored.measurement!!.valueBasis)
    }

    @Test fun `database rejects fractional counts and cleans dependent rows`() {
        val fact = facts.publish(indicator(measurement = EconomyMeasurement(BigDecimal("4"), EconomyMeasurementUnit.COUNT)))
        repository.deleteById(fact.id)
        assertEquals(0, jdbc.queryForObject("select count(*) from economy_fact_measurement where fact_id = ?", Int::class.java, fact.id))
        val event = facts.publish(event())
        assertThrows<Exception> {
            jdbc.update("insert into economy_fact_measurement (fact_id, measurement_value, measurement_unit, reference_period_from, reference_period_to, reference_period_granularity) values (?, 1.5, 'COUNT', date '2026-08-01', date '2026-08-31', 'MONTH')", event.id)
        }
    }

    @Test fun `round trips geography entities source provenance and classifications after a database reload`() {
        val fact = facts.publish(PublishEconomyFact(
            "Inflation was reported for August 2026.", EconomyCategory.PRICES_AND_INFLATION, EconomyEventType.INDICATOR_VALUE_REPORTED,
            EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, referencePeriod = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH),
            geography = EconomyGeography(EconomyGeographyKind.COUNTRY, " Germany ", "de"),
            measurement = EconomyMeasurement(BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT, EconomyReleaseStatus.FINAL),
            entities = listOf(EconomyEntityReference(" Federal Statistical Office ", EconomyEntityType.STATISTICAL_AUTHORITY), EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)),
            sources = listOf(SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-10T08:00:00Z")), SourceReference("https://example.net/report", "Independent", SourceType.NEWS_REPORT)),
        ))
        repository.flush()
        entityManager.clear()
        val stored = repository.findById(fact.id).orElseThrow().toDomain()
        assertEquals(EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"), stored.geography)
        assertEquals(setOf(EconomyEntityReference("Federal Statistical Office", EconomyEntityType.STATISTICAL_AUTHORITY), EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)), stored.entities.toSet())
        assertEquals(2, stored.sources.size)
        assertEquals(Instant.parse("2026-09-10T08:00:00Z"), stored.sources.single { it.publisher == "Authority" }.publishedAt)
        assertNull(stored.sources.single { it.publisher == "Independent" }.publishedAt)
        assertEquals(SourceType.PRIMARY_DOCUMENT, stored.sources.single { it.publisher == "Authority" }.sourceType)
        assertEquals(EconomyReleaseStatus.FINAL, stored.measurement!!.releaseStatus)
        assertNull(stored.measurement!!.seasonalAdjustment)
        assertNull(stored.measurement!!.valueBasis)
    }

    @Test fun `database enforces precision without rounding and period boundaries`() {
        val insert = "insert into economy_fact_measurement (fact_id, measurement_value, measurement_unit, reference_period_from, reference_period_to, reference_period_granularity) values (?, ?, 'PERCENT', date '2026-08-01', date '2026-08-31', 'MONTH')"
        assertDoesNotThrow { jdbc.update(insert, facts.publish(event()).id, BigDecimal("0.1234567890")) }
        assertDoesNotThrow { jdbc.update(insert, facts.publish(event()).id, BigDecimal("2.40000000000")) }
        assertDoesNotThrow { jdbc.update(insert, facts.publish(event()).id, BigDecimal("99999999999999999999")) }
        assertThrows<Exception> { jdbc.update(insert, facts.publish(event()).id, BigDecimal("0.12345678901")) }
        assertThrows<Exception> { jdbc.update(insert, facts.publish(event()).id, BigDecimal("100000000000000000000")) }
        assertThrows<Exception> { jdbc.update("insert into economy_fact_measurement (fact_id, measurement_value, measurement_unit, reference_period_from, reference_period_to, reference_period_granularity) values (?, 1, 'PERCENT', date '2026-09-01', date '2026-08-31', 'MONTH')", facts.publish(event()).id) }
    }

    private fun event() = PublishEconomyFact("A central bank decided its policy rate.", EconomyCategory.MONETARY_POLICY,
        EconomyEventType.MONETARY_POLICY_DECIDED, EconomyEvidenceLevel.PRIMARY_CONFIRMED, LocalDate.of(2026, 9, 10),
        sources = listOf(SourceReference("https://example.org/decision", "Example Central Bank", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-10T08:00:00Z"))))
    private fun indicator(measurement: EconomyMeasurement = EconomyMeasurement(BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT)) = PublishEconomyFact(
        "Inflation was reported for August 2026.", EconomyCategory.PRICES_AND_INFLATION, EconomyEventType.INDICATOR_VALUE_REPORTED,
        EconomyEvidenceLevel.PRIMARY_CONFIRMED, referencePeriod = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH),
        measurement = measurement, sources = listOf(SourceReference("https://example.org/inflation", "Example Statistics Office", SourceType.PRIMARY_DOCUMENT)))

    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [EconomyFactEntity::class])
    @EnableJpaRepositories(basePackageClasses = [EconomyFactRepository::class])
    @Import(EconomyFactService::class)
    internal class TestApplication

    companion object {
        @Container @JvmStatic @ServiceConnection val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
