package dev.factweek.economy

import dev.factweek.provenance.SourceReference
import java.time.LocalDate

interface EconomyFacts {
    fun publish(command: PublishEconomyFact): EconomyFact
}

data class PublishEconomyFact(
    val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val evidenceLevel: EconomyEvidenceLevel,
    val occurredOn: LocalDate? = null,
    val referencePeriod: EconomyReferencePeriod? = null,
    val geography: EconomyGeography? = null,
    val measurement: EconomyMeasurement? = null,
    val entities: List<EconomyEntityReference> = emptyList(),
    val sources: List<SourceReference>,
)
