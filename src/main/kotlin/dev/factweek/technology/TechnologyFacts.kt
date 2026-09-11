package dev.factweek.technology

import java.time.LocalDate

/** Public API of the technology module. */
interface TechnologyFacts {
    fun publish(command: PublishTechnologyFact): TechnologyFact
    fun occurredBetween(from: LocalDate, to: LocalDate): List<TechnologyFact>
}

data class PublishTechnologyFact(
    val statement: String,
    val category: TechnologyCategory,
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val evidenceLevel: EvidenceLevel,
    val occurredOn: LocalDate,
    val entities: List<EntityReference>,
    val sources: List<SourceReference>,
)
