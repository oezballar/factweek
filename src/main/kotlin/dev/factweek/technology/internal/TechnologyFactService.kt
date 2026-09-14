package dev.factweek.technology.internal

import dev.factweek.technology.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset

@Service
@Transactional
internal class TechnologyFactService(
    private val repository: TechnologyFactRepository,
) : TechnologyFacts {
    override fun publish(command: PublishTechnologyFact): TechnologyFact {
        TechnologyFactPolicy.validate(command)

        return repository.save(
            TechnologyFactEntity(
                statement = command.statement.trim(),
                category = command.category,
                eventType = command.eventType,
                readiness = command.readiness,
                evidenceLevel = command.evidenceLevel,
                occurredOn = command.occurredOn,
                entities = command.entities.map { EntityValue(it.name, it.type) }.toMutableList(),
                sources = command.sources.map { SourceValue(it.url, it.publisher, it.sourceType, it.publishedAt) }.toMutableList(),
            ),
        ).toDomain()
    }

    @Transactional(readOnly = true)
    override fun occurredBetween(from: LocalDate, to: LocalDate): List<TechnologyFact> =
        repository.findAllByOccurredOnBetweenOrderByOccurredOnDescIdAsc(from, to).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun relevantForBriefingBetween(from: LocalDate, to: LocalDate): List<BriefingRelevantTechnologyFact> {
        val sourceFrom = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val sourceToExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return repository.findAllRelevantForBriefing(from, to, sourceFrom, sourceToExclusive)
            .mapNotNull(::toBriefingRelevantFact)
            .filter { it.reference.date in from..to }
    }

    private fun toBriefingRelevantFact(entity: TechnologyFactEntity): BriefingRelevantTechnologyFact? {
        val reference = entity.occurredOn?.let { BriefingReference(it, BriefingReferenceBasis.OCCURRED_ON) }
            ?: entity.sources.mapNotNull { it.publishedAt }.minOrNull()?.let { publishedAt ->
                BriefingReference(publishedAt.atZone(ZoneOffset.UTC).toLocalDate(), BriefingReferenceBasis.SOURCE_PUBLISHED_AT)
            }
            ?: return null
        return BriefingRelevantTechnologyFact(entity.toDomain(), reference)
    }
}

internal object TechnologyFactPolicy {
    fun validate(command: PublishTechnologyFact) {
        require(command.statement.isNotBlank()) { "A fact statement must not be blank" }
        require(command.sources.isNotEmpty()) { "A published fact needs at least one source" }
        require(command.evidenceLevel >= EvidenceLevel.PRIMARY_CONFIRMED) {
            "A published fact needs primary evidence or stronger"
        }
    }
}
