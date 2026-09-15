package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import dev.factweek.economy.EconomyEntityReference
import dev.factweek.economy.EconomyEventType
import dev.factweek.economy.EconomyEvidenceLevel
import dev.factweek.economy.EconomyFact
import dev.factweek.economy.EconomyGeography
import dev.factweek.economy.EconomyMeasurement
import dev.factweek.economy.EconomyReferencePeriod
import dev.factweek.provenance.SourceReference
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CurrentEconomyBriefing(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
    val appliedCategories: List<EconomyCategory>,
    val requestedMaximum: Int,
    val factCount: Int,
    val facts: List<BriefingEconomyFact>,
)

data class BriefingEconomyFact(
    val id: UUID,
    val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val evidenceLevel: EconomyEvidenceLevel,
    val occurredOn: LocalDate?,
    val referencePeriod: EconomyReferencePeriod?,
    val geography: EconomyGeography?,
    val measurement: EconomyMeasurement?,
    val entities: List<EconomyEntityReference>,
    val sources: List<SourceReference>,
    val referenceDate: LocalDate,
    val referenceDateBasis: BriefingReferenceBasis,
) {
    companion object {
        fun from(fact: EconomyFact, reference: BriefingReference): BriefingEconomyFact =
            BriefingEconomyFact(
                id = fact.id,
                statement = fact.statement,
                category = fact.category,
                eventType = fact.eventType,
                evidenceLevel = fact.evidenceLevel,
                occurredOn = fact.occurredOn,
                referencePeriod = fact.referencePeriod,
                geography = fact.geography,
                measurement = fact.measurement,
                entities = fact.entities,
                sources = fact.sources,
                referenceDate = reference.date,
                referenceDateBasis = reference.basis,
            )
    }
}
