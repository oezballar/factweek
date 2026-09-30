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
        assertFailure("INDICATOR_MEASUREMENT_MISSING", "measurement") { EconomyFactPolicy.validate(indicator(measurement = null)) }
        assertFailure("INDICATOR_REFERENCE_PERIOD_MISSING", "referencePeriod") { EconomyFactPolicy.validate(indicator(period = null)) }
        assertFailure("NON_INDICATOR_MEASUREMENT_NOT_ALLOWED", "measurement") { EconomyFactPolicy.validate(event(measurement = measurement(), period = month())) }
        assertFailure("NON_INDICATOR_REFERENCE_PERIOD_NOT_ALLOWED", "referencePeriod") { EconomyFactPolicy.validate(event(period = month())) }
    }

    @Test fun `validates monthly quarterly and yearly periods`() {
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = month())) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), EconomyReferencePeriodGranularity.QUARTER))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), EconomyReferencePeriodGranularity.YEAR))) }
        assertFailure("REFERENCE_PERIOD_REVERSED", "referencePeriod") { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH))) }
        assertFailure("MONTH_BOUNDARIES_INVALID", "referencePeriod") { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH))) }
        assertFailure("QUARTER_BOUNDARIES_INVALID", "referencePeriod") { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 31), EconomyReferencePeriodGranularity.QUARTER))) }
        assertFailure("YEAR_BOUNDARIES_INVALID", "referencePeriod") { EconomyFactPolicy.validate(indicator(period = EconomyReferencePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 30), EconomyReferencePeriodGranularity.YEAR))) }
    }

    @Test fun `enforces measurement precision without rounding`() {
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("0.1234567890"))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("2.40000000000"))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(indicator(value = BigDecimal("99999999999999999999"))) }
        assertFailure("MEASUREMENT_SCALE_EXCEEDED", "measurement.value") { EconomyFactPolicy.validate(indicator(value = BigDecimal("0.12345678901"))) }
        assertFailure("MEASUREMENT_INTEGER_DIGITS_EXCEEDED", "measurement.value") { EconomyFactPolicy.validate(indicator(value = BigDecimal("100000000000000000000"))) }
    }

    @Test fun `rejects fractional counts insufficient evidence and blank entities`() {
        assertFailure("COUNT_REQUIRES_INTEGER", "measurement.value") { EconomyFactPolicy.validate(indicator(measurement = measurement(BigDecimal("1.5"), EconomyMeasurementUnit.COUNT))) }
        assertFailure("FINAL_EVIDENCE_LEVEL_REQUIRED", "evidenceLevel") { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.DOCUMENTED)) }
        assertFailure("ENTITY_NAME_EMPTY", "entities[2].name") { EconomyFactPolicy.validate(event(entities = listOf(
            EconomyEntityReference("First", EconomyEntityType.COMPANY), EconomyEntityReference("Second", EconomyEntityType.COMPANY),
            EconomyEntityReference(" ", EconomyEntityType.CENTRAL_BANK),
        ))) }
        assertFailure("ENTITY_NAME_TOO_LONG", "entities[1].name") { EconomyFactPolicy.validate(event(entities = listOf(
            EconomyEntityReference("First", EconomyEntityType.COMPANY), EconomyEntityReference("x".repeat(256), EconomyEntityType.COMPANY),
        ))) }
    }

    @Test fun `normalizes and validates geography through the publish path`() {
        val normalized = EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.COUNTRY, " Germany ", "de")))
        assertEquals("Germany", normalized.geography!!.name)
        assertEquals("DE", normalized.geography!!.code)
        assertDoesNotThrow { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, "Europe"))) }
        assertFailure("COUNTRY_CODE_INVALID", "geography.code") { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DEU"))) }
        assertFailure("GLOBAL_CODE_NOT_ALLOWED", "geography.code") { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.GLOBAL, "Global", "GL"))) }
        assertFailure("GEOGRAPHY_NAME_EMPTY", "geography.name") { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, " "))) }
        assertFailure("GEOGRAPHY_NAME_TOO_LONG", "geography.name") { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, "x".repeat(256)))) }
        assertFailure("GEOGRAPHY_CODE_TOO_LONG", "geography.code") { EconomyFactPolicy.normalizeAndValidate(event(geography = EconomyGeography(EconomyGeographyKind.REGION, "Region", "x".repeat(33)))) }
    }

    @Test fun `requires sources that structurally support the evidence level`() {
        assertFailure("SOURCES_MISSING", "sources") { EconomyFactPolicy.validate(event(sources = emptyList())) }
        assertFailure("PRIMARY_SOURCE_REQUIRED", "sources") { EconomyFactPolicy.validate(event(sources = listOf(SourceReference("https://example.org/report", "News", SourceType.NEWS_REPORT)))) }
        assertDoesNotThrow { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.net/report", "Independent", SourceType.NEWS_REPORT),
        ))) }
        assertFailure("INDEPENDENT_SOURCE_URLS_REQUIRED", "sources") { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.org/primary", "Independent", SourceType.NEWS_REPORT),
        ))) }
        assertFailure("INDEPENDENT_PUBLISHERS_REQUIRED", "sources") { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED, sources = listOf(
            SourceReference("https://example.org/primary", "Authority", SourceType.PRIMARY_DOCUMENT),
            SourceReference("https://example.net/report", "AUTHORITY", SourceType.NEWS_REPORT),
        ))) }
        assertFailure("INDEPENDENT_SOURCE_COUNT_REQUIRED", "sources") { EconomyFactPolicy.validate(event(evidence = EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED)) }
        assertFailure("SOURCE_URL_EMPTY", "sources[1].url") { EconomyFactPolicy.validate(event(sources = sources() + SourceReference(" ", "Another", SourceType.NEWS_REPORT))) }
        assertFailure("SOURCE_PUBLISHER_EMPTY", "sources[1].publisher") { EconomyFactPolicy.validate(event(sources = sources() + SourceReference("https://other.example/report", " ", SourceType.NEWS_REPORT))) }
    }

    @Test fun `distinguishes empty and long statements`() {
        assertFailure("STATEMENT_EMPTY", "statement") { EconomyFactPolicy.validate(event().copy(statement = "")) }
        assertFailure("STATEMENT_TOO_LONG", "statement") { EconomyFactPolicy.validate(event().copy(statement = "s".repeat(1_001))) }
    }

    private fun assertFailure(code: String, path: String, action: () -> Unit) {
        val failure = assertThrows<InvalidEconomyFactPublicationException>(action)
        assertEquals(code, failure.code)
        assertEquals(path, failure.fieldPath)
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
