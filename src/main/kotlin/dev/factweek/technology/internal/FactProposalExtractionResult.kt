package dev.factweek.technology.internal

internal data class FactProposalExtractionResult(
    val requestedMaximum: Int,
    val selectedCount: Int,
    val proposedCount: Int,
    val rejectedCount: Int,
    val skippedCount: Int,
) {
    init {
        require(proposedCount + rejectedCount + skippedCount == selectedCount)
    }
}
