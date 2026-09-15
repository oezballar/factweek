package dev.factweek.briefing

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CurrentEconomyBriefing(val from: LocalDate, val to: LocalDate, val generatedAt: Instant, val appliedCategories: List<EconomyCategory>, val requestedMaximum: Int, val factCount: Int, val facts: List<BriefingEconomyFact>)
data class BriefingEconomyFact(
    val id: UUID, val statement: String, val category: EconomyCategory, val eventType: EconomyEventType, val evidenceLevel: EconomyEvidenceLevel,
    val occurredOn: LocalDate?, val referencePeriod: EconomyReferencePeriod?, val geography: EconomyGeography?, val measurement: EconomyMeasurement?,
    val entities: List<EconomyEntityReference>, val sources: List<SourceReference>, val referenceDate: LocalDate, val referenceDateBasis: BriefingReferenceBasis,
) { companion object { fun from(fact: EconomyFact, reference: BriefingReference) = BriefingEconomyFact(fact.id, fact.statement, fact.category, fact.eventType, fact.evidenceLevel, fact.occurredOn, fact.referencePeriod, fact.geography, fact.measurement, fact.entities, fact.sources, reference.date, reference.basis) } }
