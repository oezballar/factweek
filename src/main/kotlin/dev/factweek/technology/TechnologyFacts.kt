package dev.factweek.technology

import java.time.LocalDate

/** Public API of the technology module. */
interface TechnologyFacts {
    fun publish(command: PublishTechnologyFact): TechnologyFact
    fun occurredBetween(from: LocalDate, to: LocalDate): List<TechnologyFact>
    fun relevantForBriefingBetween(from: LocalDate, to: LocalDate): List<BriefingRelevantTechnologyFact>
}

data class PublishTechnologyFact(
    val statement: String,
    val category: TechnologyCategory,
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val evidenceLevel: EvidenceLevel,
    val occurredOn: LocalDate?,
    val entities: List<EntityReference>,
    val sources: List<SourceReference>,
)

/** A fact together with the non-persisted date that made it relevant to a briefing. */
data class BriefingRelevantTechnologyFact(
    val fact: TechnologyFact,
    val reference: BriefingReference,
)

data class BriefingReference(
    val date: LocalDate,
    val basis: BriefingReferenceBasis,
)

enum class BriefingReferenceBasis {
    OCCURRED_ON,
    SOURCE_PUBLISHED_AT,
}
