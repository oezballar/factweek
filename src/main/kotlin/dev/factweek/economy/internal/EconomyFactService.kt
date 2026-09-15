package dev.factweek.economy.internal

import dev.factweek.economy.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
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
}

internal object EconomyFactPolicy {
    private const val MAX_STATEMENT_LENGTH = 1000
    private const val MAX_ENTITY_NAME_LENGTH = 255
    private const val MAX_GEOGRAPHY_NAME_LENGTH = 255
    private val countryCode = Regex("[A-Z]{2}")

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
        if (command.statement.isEmpty() || command.statement.length > MAX_STATEMENT_LENGTH || command.sources.isEmpty()) invalid()
        if (!isPublishableEvidence(command.evidenceLevel)) invalid()
        if (command.sources.any { it.url.isBlank() || it.publisher.isBlank() }) invalid()
        if (command.entities.any { it.name.isBlank() || it.name.length > MAX_ENTITY_NAME_LENGTH }) invalid()
        command.geography?.let(::validateGeography)
        validateEventShape(command)
    }

    private fun validateEventShape(command: PublishEconomyFact) {
        if (command.eventType == EconomyEventType.INDICATOR_VALUE_REPORTED) {
            if (command.measurement == null || command.referencePeriod == null) invalid()
            validatePeriod(command.referencePeriod)
            if (command.measurement.unit == EconomyMeasurementUnit.COUNT && command.measurement.value.stripTrailingZeros().scale() > 0) invalid()
        } else if (command.measurement != null || command.referencePeriod != null) {
            invalid()
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

    private fun isPublishableEvidence(level: EconomyEvidenceLevel): Boolean = when (level) {
        EconomyEvidenceLevel.REPORTED, EconomyEvidenceLevel.DOCUMENTED -> false
        EconomyEvidenceLevel.PRIMARY_CONFIRMED, EconomyEvidenceLevel.INDEPENDENTLY_CONFIRMED -> true
    }

    private fun invalid(): Nothing = throw InvalidEconomyFactPublicationException()
}

internal class InvalidEconomyFactPublicationException : RuntimeException()
