package dev.factweek.economy

import dev.factweek.provenance.SourceReference
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface EconomyFactProposals {
    fun create(command: CreateEconomyFactProposal): EconomyFactProposal
    fun find(id: UUID): EconomyFactProposal?
    fun accept(id: UUID, command: AcceptEconomyFactProposal): EconomyFactProposal
    fun reject(id: UUID, reason: String): EconomyFactProposal
}

data class AcceptEconomyFactProposal(
    val evidenceLevel: EconomyEvidenceLevel,
    val additionalSources: List<SourceReference> = emptyList(),
)

data class CreateEconomyFactProposal(
    val sourceDocumentId: UUID,
    val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val occurredOn: LocalDate? = null,
    val referencePeriod: EconomyReferencePeriod? = null,
    val geography: EconomyGeography? = null,
    val measurement: EconomyMeasurement? = null,
    val entities: List<EconomyEntityReference> = emptyList(),
    val evidenceText: String,
)

data class EconomyFactProposal(
    val id: UUID,
    val sourceDocumentId: UUID,
    val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val occurredOn: LocalDate?,
    val referencePeriod: EconomyReferencePeriod?,
    val geography: EconomyGeography?,
    val measurement: EconomyMeasurement?,
    val entities: List<EconomyEntityReference>,
    val evidenceText: String,
    val createdAt: Instant,
    val status: EconomyFactProposalStatus,
    val reviewedEvidenceLevel: EconomyEvidenceLevel?,
    val reviewedAt: Instant?,
    val rejectionReason: String?,
    val economyFactId: UUID?,
)

enum class EconomyFactProposalStatus { PROPOSED, ACCEPTED, REJECTED }
