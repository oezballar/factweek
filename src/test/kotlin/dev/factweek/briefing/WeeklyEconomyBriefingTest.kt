package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import dev.factweek.economy.EconomyEntityReference
import dev.factweek.economy.EconomyEntityType
import dev.factweek.economy.EconomyEvidenceLevel
import dev.factweek.economy.EconomyEventType
import dev.factweek.economy.EconomyFact
import dev.factweek.economy.EconomyFacts
import dev.factweek.economy.EconomyGeography
import dev.factweek.economy.EconomyGeographyKind
import dev.factweek.economy.EconomyMeasurement
import dev.factweek.economy.EconomyMeasurementUnit
import dev.factweek.economy.EconomyReferencePeriod
import dev.factweek.economy.EconomyReferencePeriodGranularity
import dev.factweek.economy.PublishEconomyFact
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class WeeklyEconomyBriefingTest {
    @Test
    fun `builds a Monday to Sunday window from one clock instant`() {
        val instant = Instant.parse("2026-09-13T12:00:00Z")
        val clock = CountingClock(instant, ZoneId.of("Europe/Berlin"))
        val facts = RecordingEconomyFacts()

        val result = WeeklyEconomyBriefing(facts, clock).current(emptySet(), 10)

        assertEquals(instant, result.generatedAt)
        assertEquals(LocalDate.of(2026, 9, 7), result.from)
        assertEquals(LocalDate.of(2026, 9, 13), result.to)
        assertEquals(1, clock.instantCalls)
        assertEquals(LocalDate.of(2026, 9, 7), facts.from)
        assertEquals(LocalDate.of(2026, 9, 13), facts.to)
    }

    @Test
    fun `derives references filters before limiting and sorts deterministically`() {
        val sameDayLaterId = fact("00000000-0000-0000-0000-000000000002", occurredOn = LocalDate.of(2026, 9, 11))
        val sameDayEarlierId = fact("00000000-0000-0000-0000-000000000001", occurredOn = LocalDate.of(2026, 9, 11))
        val sourceDated = fact(
            "00000000-0000-0000-0000-000000000003",
            sources = listOf(
                source("https://example.org/later", "Later", "2026-09-13T00:00:00Z"),
                source("https://example.org/earlier", "Earlier", "2026-09-12T23:59:59Z"),
            ),
        )
        val excludedCategory = fact(
            "00000000-0000-0000-0000-000000000004",
            occurredOn = LocalDate.of(2026, 9, 13),
            category = EconomyCategory.MONETARY_POLICY,
        )
        val undated = fact("00000000-0000-0000-0000-000000000005")

        val result = WeeklyEconomyBriefing(
            RecordingEconomyFacts(listOf(excludedCategory, sameDayLaterId, undated, sourceDated, sameDayEarlierId)),
            utcClock(),
        ).current(setOf(EconomyCategory.PRICES_AND_INFLATION), 3)

        assertEquals(listOf(sourceDated.id, sameDayEarlierId.id, sameDayLaterId.id), result.facts.map { it.id })
        assertEquals(BriefingReferenceBasis.SOURCE_PUBLISHED_AT, result.facts.first().referenceDateBasis)
        assertEquals(LocalDate.of(2026, 9, 12), result.facts.first().referenceDate)
        assertEquals(3, result.factCount)
        assertEquals(3, result.requestedMaximum)
        assertEquals(listOf(EconomyCategory.PRICES_AND_INFLATION), result.appliedCategories)
    }

    @Test
    fun `occurred date wins over sources and reference period does not affect reference`() {
        val fact = fact(
            "00000000-0000-0000-0000-000000000001",
            occurredOn = LocalDate.of(2026, 9, 11),
            referencePeriod = EconomyReferencePeriod(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                EconomyReferencePeriodGranularity.MONTH,
            ),
            sources = listOf(source("https://example.org/source", "Source", "2026-09-08T23:30:00Z")),
        )

        val result = WeeklyEconomyBriefing(RecordingEconomyFacts(listOf(fact)), utcClock()).current(emptySet(), 10)

        assertEquals(LocalDate.of(2026, 9, 11), result.facts.single().referenceDate)
        assertEquals(BriefingReferenceBasis.OCCURRED_ON, result.facts.single().referenceDateBasis)
        assertEquals(fact.referencePeriod, result.facts.single().referencePeriod)
    }

    @Test
    fun `keeps all economy fields and returns metadata for an empty result`() {
        val empty = WeeklyEconomyBriefing(RecordingEconomyFacts(), utcClock()).current(emptySet(), 10)
        assertEquals(0, empty.factCount)
        assertEquals(EconomyCategory.entries.sortedBy { it.name }, empty.appliedCategories)

        val complete = fact(
            "00000000-0000-0000-0000-000000000001",
            sources = listOf(source("https://example.org/source", "Source", "2026-09-12T00:00:00Z")),
            referencePeriod = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH),
            measurement = EconomyMeasurement(BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT),
            geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"),
            entities = listOf(EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)),
        )
        val result = WeeklyEconomyBriefing(RecordingEconomyFacts(listOf(complete)), utcClock()).current(emptySet(), 10)

        assertEquals(complete.measurement, result.facts.single().measurement)
        assertEquals(complete.geography, result.facts.single().geography)
        assertEquals(complete.entities, result.facts.single().entities)
        assertEquals(complete.sources, result.facts.single().sources)
    }

    @Test
    fun `rejects maximum outside configured bounds`() {
        val briefing = WeeklyEconomyBriefing(RecordingEconomyFacts(), utcClock())

        assertThrows<InvalidWeeklyEconomyBriefingRequestException> { briefing.current(emptySet(), 0) }
        assertThrows<InvalidWeeklyEconomyBriefingRequestException> { briefing.current(emptySet(), 51) }
    }

    private fun utcClock(): Clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC)

    private fun fact(
        id: String,
        occurredOn: LocalDate? = null,
        sources: List<SourceReference> = emptyList(),
        category: EconomyCategory = EconomyCategory.PRICES_AND_INFLATION,
        referencePeriod: EconomyReferencePeriod? = null,
        measurement: EconomyMeasurement? = null,
        geography: EconomyGeography? = null,
        entities: List<EconomyEntityReference> = emptyList(),
    ): EconomyFact = EconomyFact(
        id = UUID.fromString(id),
        statement = "Fact",
        category = category,
        eventType = if (measurement == null) EconomyEventType.MONETARY_POLICY_DECIDED else EconomyEventType.INDICATOR_VALUE_REPORTED,
        evidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
        occurredOn = occurredOn,
        referencePeriod = referencePeriod,
        geography = geography,
        measurement = measurement,
        entities = entities,
        sources = sources,
    )

    private fun source(url: String, publisher: String, publishedAt: String?): SourceReference =
        SourceReference(url, publisher, SourceType.PRIMARY_DOCUMENT, publishedAt?.let(Instant::parse))

    private class RecordingEconomyFacts(
        private val facts: List<EconomyFact> = emptyList(),
    ) : EconomyFacts {
        var from: LocalDate? = null
        var to: LocalDate? = null

        override fun publish(command: PublishEconomyFact): EconomyFact = error("unused")

        override fun relevantForBriefingBetween(from: LocalDate, to: LocalDate): List<EconomyFact> {
            this.from = from
            this.to = to
            return facts
        }
    }

    private class CountingClock(
        private val value: Instant,
        private val clockZone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = clockZone
        override fun withZone(zone: ZoneId): Clock = this
        var instantCalls = 0
            private set

        override fun instant(): Instant {
            instantCalls++
            return value
        }
    }
}
