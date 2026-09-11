package dev.factweek.technology.internal

internal data class FactProposalExtractionResult(
    val requestedMaximum: Int,
    val selectedCount: Int,
    val proposedCount: Int,
    val rejectedCount: Int,
    val skippedCount: Int,
) {
    init {
        require(requestedMaximum in 1..25)
        require(selectedCount >= 0)
        require(proposedCount >= 0)
        require(rejectedCount >= 0)
        require(skippedCount in 0..selectedCount)
    }
}
