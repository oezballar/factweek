package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Service
@Transactional
internal class EconomyFactService(private val repository: EconomyFactRepository) : EconomyFacts {
    override fun publish(command: PublishEconomyFact): EconomyFact {
        val normalized = EconomyFactPolicy.normalizeAndValidate(command)
        val fact = EconomyFactEntity(
            statement = normalized.statement,
            category = normalized.category,
            eventType = normalized.eventType,
            evidenceLevel = normalized.evidenceLevel,
            occurredOn = normalized.occurredOn,
            geographyKind = normalized.geography?.kind,
            geographyName = normalized.geography?.name,
            geographyCode = normalized.geography?.code,
            entities = normalized.entities.map { EconomyEntityValue(it.name, it.type) }.toMutableList(),
            sources = normalized.sources.map { EconomySourceValue(it.url, it.publisher, it.sourceType, it.publishedAt) }.toMutableList(),
        )
        normalized.measurement?.let { measurement ->
            val period = requireNotNull(normalized.referencePeriod)
            fact.measurement = EconomyMeasurementEntity(
                fact = fact, value = measurement.value, unit = measurement.unit,
                releaseStatus = measurement.releaseStatus, seasonalAdjustment = measurement.seasonalAdjustment,
                valueBasis = measurement.valueBasis, referencePeriodFrom = period.from,
                referencePeriodTo = period.to, referencePeriodGranularity = period.granularity,
            )
        }
        return repository.save(fact).toDomain()
    }

    @Transactional(readOnly = true)
    override fun relevantForBriefingBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<EconomyFact> =
        repository.findAllRelevantForBriefing(
            from = from,
            to = to,
            sourceFrom = from.atStartOfDay(ZoneOffset.UTC).toInstant(),
            sourceToExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        ).map { it.toDomain() }
}

internal object EconomyFactPolicy {
    private const val MAX_STATEMENT_LENGTH = 1000
    private const val MAX_ENTITY_NAME_LENGTH = 255
    private const val MAX_GEOGRAPHY_NAME_LENGTH = 255
    private const val MAX_GEOGRAPHY_CODE_LENGTH = 32
    private val countryCode = Regex("[A-Z]{2}")
    private val primarySourceTypes = setOf(SourceType.PRIMARY_DOCUMENT, SourceType.PAPER, SourceType.DATASET, SourceType.REPOSITORY, SourceType.REGULATOR)

    fun normalizeAndValidate(command: PublishEconomyFact): PublishEconomyFact {
        val normalized = command.copy(
            statement = command.statement.trim(),
            geography = command.geography?.let { normalizeGeography(it) },
            entities = command.entities.map { entity -> entity.copy(name = entity.name.trim()) },
        )
        validate(normalized)
        return normalized
    }

    fun validate(command: PublishEconomyFact) {
        validateStructure(command.statement, command.eventType, command.referencePeriod, command.geography, command.measurement, command.entities)
        if (command.sources.isEmpty()) invalid("SOURCES_MISSING", "sources")
        validateEvidence(command.evidenceLevel, command.sources)
        command.sources.forEachIndexed { index, source ->
            if (source.url.isBlank()) invalid("SOURCE_URL_EMPTY", "sources[$index].url")
            if (source.publisher.isBlank()) invalid("SOURCE_PUBLISHER_EMPTY", "sources[$index].publisher")
        }
    }

    fun normalizeAndValidateProposal(command: dev.factweek.economy.CreateEconomyFactProposal): dev.factweek.economy.CreateEconomyFactProposal {
        val normalized = command.copy(statement = command.statement.trim(), evidenceText = command.evidenceText.trim(), geography = command.geography?.let(::normalizeGeography), entities = command.entities.map { it.copy(name = it.name.trim()) })
        if (normalized.evidenceText.isEmpty()) invalid("EVIDENCE_TEXT_EMPTY", "evidenceText")
        if (normalized.evidenceText.length > 2000) invalid("EVIDENCE_TEXT_TOO_LONG", "evidenceText")
        validateStructure(normalized.statement, normalized.eventType, normalized.referencePeriod, normalized.geography, normalized.measurement, normalized.entities)
        return normalized
    }

    private fun validateStructure(statement: String, eventType: EconomyEventType, referencePeriod: EconomyReferencePeriod?, geography: EconomyGeography?, measurement: EconomyMeasurement?, entities: List<EconomyEntityReference>) {
        if (statement.isEmpty()) invalid("STATEMENT_EMPTY", "statement")
        if (statement.length > MAX_STATEMENT_LENGTH) invalid("STATEMENT_TOO_LONG", "statement")
        entities.forEachIndexed { index, entity ->
            if (entity.name.isBlank()) invalid("ENTITY_NAME_EMPTY", "entities[$index].name")
            if (entity.name.length > MAX_ENTITY_NAME_LENGTH) invalid("ENTITY_NAME_TOO_LONG", "entities[$index].name")
        }
        geography?.let(::validateGeography)
        if (eventType == EconomyEventType.INDICATOR_VALUE_REPORTED) {
            if (measurement == null) invalid("INDICATOR_MEASUREMENT_MISSING", "measurement")
            if (referencePeriod == null) invalid("INDICATOR_REFERENCE_PERIOD_MISSING", "referencePeriod")
            validatePeriod(referencePeriod); validateMeasurement(measurement)
        } else {
            if (measurement != null) invalid("NON_INDICATOR_MEASUREMENT_NOT_ALLOWED", "measurement")
            if (referencePeriod != null) invalid("NON_INDICATOR_REFERENCE_PERIOD_NOT_ALLOWED", "referencePeriod")
        }
    }


    private fun validateMeasurement(measurement: EconomyMeasurement) {
        val normalized = measurement.value.stripTrailingZeros()
        val requiredScale = maxOf(normalized.scale(), 0)
        val integerDigits = maxOf(normalized.precision() - normalized.scale(), 0)
        if (integerDigits > 20) invalid("MEASUREMENT_INTEGER_DIGITS_EXCEEDED", "measurement.value")
        if (requiredScale > 10) invalid("MEASUREMENT_SCALE_EXCEEDED", "measurement.value")
        if (measurement.unit == EconomyMeasurementUnit.COUNT && requiredScale > 0) invalid("COUNT_REQUIRES_INTEGER", "measurement.value")
    }

    private fun validateEvidence(level: EconomyEvidenceLevel, sources: List<dev.factweek.provenance.SourceReference>) {
        when (level) {
            EconomyEvidenceLevel.REPORTED, EconomyEvidenceLevel.DOCUMENTED -> invalid("FINAL_EVIDENCE_LEVEL_REQUIRED", "evidenceLevel")
            EconomyEvidenceLevel.PRIMARY_CONFIRMED -> if (sources.none { it.sourceType in primarySourceTypes }) invalid("PRIMARY_SOURCE_REQUIRED", "sources")
            EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED -> {
                if (sources.none { it.sourceType in primarySourceTypes }) invalid("PRIMARY_SOURCE_REQUIRED", "sources")
                val urls = sources.map { it.url.trim() }.filter { it.isNotEmpty() }.toSet()
                val publishers = sources.map { it.publisher.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
                if (sources.size < 2) invalid("INDEPENDENT_SOURCE_COUNT_REQUIRED", "sources")
                if (urls.size < 2) invalid("INDEPENDENT_SOURCE_URLS_REQUIRED", "sources")
                if (publishers.size < 2) invalid("INDEPENDENT_PUBLISHERS_REQUIRED", "sources")
            }
        }
    }

    private fun validatePeriod(period: EconomyReferencePeriod) {
        if (period.from.isAfter(period.to)) invalid("REFERENCE_PERIOD_REVERSED", "referencePeriod")
        val valid = when (period.granularity) {
            EconomyReferencePeriodGranularity.MONTH -> period.from.dayOfMonth == 1 && period.to == period.from.with(TemporalAdjusters.lastDayOfMonth())
            EconomyReferencePeriodGranularity.QUARTER -> period.from.dayOfMonth == 1 && period.from.monthValue in setOf(1, 4, 7, 10) &&
                period.to == period.from.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
            EconomyReferencePeriodGranularity.YEAR -> period.from == LocalDate.of(period.from.year, 1, 1) && period.to == LocalDate.of(period.from.year, 12, 31)
        }
        if (!valid) {
            val code = when (period.granularity) {
                EconomyReferencePeriodGranularity.MONTH -> "MONTH_BOUNDARIES_INVALID"
                EconomyReferencePeriodGranularity.QUARTER -> "QUARTER_BOUNDARIES_INVALID"
                EconomyReferencePeriodGranularity.YEAR -> "YEAR_BOUNDARIES_INVALID"
            }
            invalid(code, "referencePeriod")
        }
    }

    private fun normalizeGeography(geography: EconomyGeography): EconomyGeography =
        geography.copy(name = geography.name.trim(), code = geography.code?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT))

    private fun validateGeography(geography: EconomyGeography) {
        if (geography.name.isEmpty()) invalid("GEOGRAPHY_NAME_EMPTY", "geography.name")
        if (geography.name.length > MAX_GEOGRAPHY_NAME_LENGTH) invalid("GEOGRAPHY_NAME_TOO_LONG", "geography.name")
        if (geography.code != null && geography.code.length > MAX_GEOGRAPHY_CODE_LENGTH) invalid("GEOGRAPHY_CODE_TOO_LONG", "geography.code")
        when (geography.kind) {
            EconomyGeographyKind.COUNTRY -> if (geography.code == null || !countryCode.matches(geography.code)) invalid("COUNTRY_CODE_INVALID", "geography.code")
            EconomyGeographyKind.REGION -> Unit
            EconomyGeographyKind.GLOBAL -> if (geography.code != null) invalid("GLOBAL_CODE_NOT_ALLOWED", "geography.code")
        }
    }

    private fun invalid(code: String, fieldPath: String): Nothing = throw InvalidEconomyFactPublicationException(code, fieldPath)
}

internal class InvalidEconomyFactPublicationException(val code: String, val fieldPath: String) : RuntimeException()
