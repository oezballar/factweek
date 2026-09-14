package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
import dev.factweek.technology.EntityReference
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.SourceReference
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
import java.time.Instant
import java.time.LocalDate

/** A factual, non-narrative view of reviewed technology facts for the current rolling window. */
data class CurrentTechnologyBriefing(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
    val appliedCategories: List<TechnologyCategory>,
    val requestedMaximum: Int,
    val factCount: Int,
    val facts: List<BriefingTechnologyFact>,
)

data class BriefingTechnologyFact(
    val id: java.util.UUID,
    val statement: String,
    val category: TechnologyCategory,
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val evidenceLevel: EvidenceLevel,
    val occurredOn: LocalDate?,
    val entities: List<EntityReference>,
    val sources: List<SourceReference>,
    val referenceDate: LocalDate,
    val referenceDateBasis: BriefingReferenceBasis,
) {
    companion object {
        fun from(fact: TechnologyFact, referenceDate: LocalDate, referenceDateBasis: BriefingReferenceBasis) =
            BriefingTechnologyFact(
                fact.id,
                fact.statement,
                fact.category,
                fact.eventType,
                fact.readiness,
                fact.evidenceLevel,
                fact.occurredOn,
                fact.entities,
                fact.sources,
                referenceDate,
                referenceDateBasis,
            )
    }
}
