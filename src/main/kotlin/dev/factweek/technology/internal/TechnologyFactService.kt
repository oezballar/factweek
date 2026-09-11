package dev.factweek.technology.internal

import dev.factweek.technology.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
                sources = command.sources.map { SourceValue(it.url, it.publisher, it.sourceType) }.toMutableList(),
            ),
        ).toDomain()
    }

    @Transactional(readOnly = true)
    override fun occurredBetween(from: LocalDate, to: LocalDate): List<TechnologyFact> =
        repository.findAllByOccurredOnBetweenOrderByOccurredOnDescIdAsc(from, to).map { it.toDomain() }
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
