package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class EconomyFactPolicyTest {
    @Test fun `accepts a discrete event without a measurement`() = assertDoesNotThrow {
        EconomyFactPolicy.validate(event())
    }

    @Test fun `accepts a monthly indicator with a negative percentage`() = assertDoesNotThrow {
        EconomyFactPolicy.validate(indicator(value = BigDecimal("-1.2")))
    }

    @Test fun `rejects invalid event and measurement combinations`() {
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(measurement = null)) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(period = null)) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(measurement = measurement(), period = month())) }
    }

    @Test fun `validates monthly quarterly and yearly periods`() {
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = month())) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), EconomyReferencePeriodGranularity.QUARTER))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), EconomyReferencePeriodGranularity.YEAR))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH))) }
    }

    @Test fun `rejects fractional counts insufficient evidence invalid geography and blank entities`() {
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(measurement = measurement(BigDecimal("1.5"), EconomyMeasurementUnit.COUNT))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.DOCUMENTED)) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "de"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(entities = listOf(EconomyEntityReference(" ", EconomyEntityType.CENTRAL_BANK)))) }
    }

    private fun event(
        evidence: EconomyEvidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
        measurement: EconomyMeasurement? = null,
        period: EconomyReferencePeriod? = null,
        geography: EconomyGeography? = null,
        entities: List<EconomyEntityReference> = emptyList(),
    ) = PublishEconomyFact("A central bank changed its policy rate.", EconomyCategory.MONETARY_POLICY,
        EconomyEventType.MONETARY_POLICY_DECIDED, evidence, geography = geography, measurement = measurement,
        referencePeriod = period, entities = entities, sources = sources())

    private fun indicator(
        value: BigDecimal = BigDecimal("2.4"), measurement: EconomyMeasurement? = measurement(value),
        period: EconomyReferencePeriod? = month(),
    ) = PublishEconomyFact("Inflation was reported for August 2026.", EconomyCategory.PRICES_AND_INFLATION,
        EconomyEventType.INDICATOR_VALUE_REPORTED, EconomyEvidenceLevel.PRIMARY_CONFIRMED,
        measurement = measurement, referencePeriod = period, sources = sources())

    private fun measurement(value: BigDecimal = BigDecimal("2.4"), unit: EconomyMeasurementUnit = EconomyMeasurementUnit.PERCENT) = EconomyMeasurement(value, unit)
    private fun month() = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH)
    private fun sources() = listOf(SourceReference("https://example.org/source", "Example", SourceType.PRIMARY_DOCUMENT))
}
