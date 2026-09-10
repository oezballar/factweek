package dev.factweek.ingestion

import java.time.Instant

data class GdeltImportResult(
    val query: String,
    val from: Instant,
    val to: Instant,
    val requestedMaximum: Int,
    val discoveredCount: Int,
    val storedCount: Int,
    val existingCount: Int,
) {
    init {
        require(storedCount + existingCount == discoveredCount) {
            "Stored and existing candidate counts must equal the discovered count"
        }
    }
}
