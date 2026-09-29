package dev.factweek.economy

import java.util.UUID

interface EconomyProposalExtractions {
    fun extract(sourceDocumentId: UUID): EconomyProposalExtractionResult
}

data class EconomyProposalExtractionResult(
    val sourceDocumentId: UUID,
    val proposalCount: Int,
    val proposals: List<EconomyFactProposal>,
)
