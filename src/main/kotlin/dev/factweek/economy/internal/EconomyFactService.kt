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
        if (command.sources.isEmpty()) invalid()
        validateEvidence(command.evidenceLevel, command.sources)
        if (command.sources.any { it.url.isBlank() || it.publisher.isBlank() }) invalid()
    }

    fun normalizeAndValidateProposal(command: dev.factweek.economy.CreateEconomyFactProposal): dev.factweek.economy.CreateEconomyFactProposal {
        val normalized = command.copy(statement = command.statement.trim(), evidenceText = command.evidenceText.trim(), geography = command.geography?.let(::normalizeGeography), entities = command.entities.map { it.copy(name = it.name.trim()) })
        if (normalized.evidenceText.isEmpty() || normalized.evidenceText.length > 2000) invalid()
        validateStructure(normalized.statement, normalized.eventType, normalized.referencePeriod, normalized.geography, normalized.measurement, normalized.entities)
        return normalized
    }

    private fun validateStructure(statement: String, eventType: EconomyEventType, referencePeriod: EconomyReferencePeriod?, geography: EconomyGeography?, measurement: EconomyMeasurement?, entities: List<EconomyEntityReference>) {
        if (statement.isEmpty() || statement.length > MAX_STATEMENT_LENGTH) invalid()
        if (entities.any { it.name.isBlank() || it.name.length > MAX_ENTITY_NAME_LENGTH }) invalid()
        geography?.let(::validateGeography)
        if (eventType == EconomyEventType.INDICATOR_VALUE_REPORTED) {
            if (measurement == null || referencePeriod == null) invalid()
            validatePeriod(referencePeriod); validateMeasurement(measurement)
        } else if (measurement != null || referencePeriod != null) invalid()
    }


    private fun validateMeasurement(measurement: EconomyMeasurement) {
        val normalized = measurement.value.stripTrailingZeros()
        val requiredScale = maxOf(normalized.scale(), 0)
        val integerDigits = maxOf(normalized.precision() - normalized.scale(), 0)
        if (integerDigits > 20 || requiredScale > 10) invalid()
        if (measurement.unit == EconomyMeasurementUnit.COUNT && requiredScale > 0) invalid()
    }

    private fun validateEvidence(level: EconomyEvidenceLevel, sources: List<dev.factweek.provenance.SourceReference>) {
        when (level) {
            EconomyEvidenceLevel.REPORTED, EconomyEvidenceLevel.DOCUMENTED -> invalid()
            EconomyEvidenceLevel.PRIMARY_CONFIRMED -> if (sources.none { it.sourceType in primarySourceTypes }) invalid()
            EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED -> {
                if (sources.none { it.sourceType in primarySourceTypes }) invalid()
                val urls = sources.map { it.url.trim() }.filter { it.isNotEmpty() }.toSet()
                val publishers = sources.map { it.publisher.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
                if (sources.size < 2 || urls.size < 2 || publishers.size < 2) invalid()
            }
        }
    }

    private fun validatePeriod(period: EconomyReferencePeriod) {
        if (period.from.isAfter(period.to)) invalid()
        val valid = when (period.granularity) {
            EconomyReferencePeriodGranularity.MONTH -> period.from.dayOfMonth == 1 && period.to == period.from.with(TemporalAdjusters.lastDayOfMonth())
            EconomyReferencePeriodGranularity.QUARTER -> period.from.dayOfMonth == 1 && period.from.monthValue in setOf(1, 4, 7, 10) &&
                period.to == period.from.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
            EconomyReferencePeriodGranularity.YEAR -> period.from == LocalDate.of(period.from.year, 1, 1) && period.to == LocalDate.of(period.from.year, 12, 31)
        }
        if (!valid) invalid()
    }

    private fun normalizeGeography(geography: EconomyGeography): EconomyGeography =
        geography.copy(name = geography.name.trim(), code = geography.code?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT))

    private fun validateGeography(geography: EconomyGeography) {
        if (geography.name.isEmpty() || geography.name.length > MAX_GEOGRAPHY_NAME_LENGTH) invalid()
        when (geography.kind) {
            EconomyGeographyKind.COUNTRY -> if (geography.code == null || !countryCode.matches(geography.code)) invalid()
            EconomyGeographyKind.REGION -> Unit
            EconomyGeographyKind.GLOBAL -> if (geography.code != null) invalid()
        }
    }

    private fun invalid(): Nothing = throw InvalidEconomyFactPublicationException()
}

internal class InvalidEconomyFactPublicationException : RuntimeException()
