package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test fun `enforces measurement precision without rounding`() {
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("0.1234567890"))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("2.40000000000"))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("99999999999999999999"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(value = BigDecimal("0.12345678901"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(value = BigDecimal("100000000000000000000"))) }
    }

    @Test fun `rejects fractional counts insufficient evidence and blank entities`() {
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(indicator(measurement = measurement(BigDecimal("1.5"), EconomyMeasurementUnit.COUNT))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.DOCUMENTED)) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(entities = listOf(EconomyEntityReference(" ", EconomyEntityType.CENTRAL_BANK)))) }
    }

    @Test fun `normalizes and validates geography through the publish path`() {
        val normalized = EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.COUNTRY, " Germany ", "de")))
        assertEquals("Germany", normalized.geography!!.name)
        assertEquals("DE", normalized.geography!!.code)
        assertDoesNotThrow { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, "Europe"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DEU"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.GLOBAL, "Global", "GL"))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, " "))) }
    }

    @Test fun `requires sources that structurally support the evidence level`() {
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(sources = listOf(SourceReference("https://example.org/report", "News", SourceType.NEWS_REPORT)))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.net/report", "Independent", SourceType.NEWS_REPORT),
        ))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.org/primary", "Independent", SourceType.NEWS_REPORT),
        ))) }
        assertThrows<InvalidEconomyFactPublicationException> { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.net/report", "AUTHORITY", SourceType.NEWS_REPORT),
        ))) }
    }

    private fun event(
        evidence: EconomyEvidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
        measurement: EconomyMeasurement? = null,
        period: EconomyReferencePeriod? = null,
        geography: EconomyGeography? = null,
        entities: List<EconomyEntityReference> = emptyList(),
        sources: List<SourceReference> = sources(),
    ) = PublishEconomyFact("A central bank changed its policy rate.", EconomyCategory.MONETARY_POLICY,
        EconomyEventType.MONETARY_POLICY_DECIDED, evidence, geography = geography, measurement = measurement,
        referencePeriod = period, entities = entities, sources = sources)

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
