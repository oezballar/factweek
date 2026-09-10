package dev.factweek.ingestion.internal

internal data class SourceContentRetrievalResult(
    val requestedMaximum: Int,
    val selectedCount: Int,
    val fetchedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
) {
    init {
        require(fetchedCount + failedCount + skippedCount == selectedCount)
    }
}
