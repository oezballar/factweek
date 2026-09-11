package dev.factweek.ingestion

import java.time.Instant
import java.util.UUID

/** Public read API for successfully retrieved source content. */
interface SourceDocuments {
    fun findFetchedForFactProposals(maximum: Int): List<FetchedSourceDocument>
}

data class FetchedSourceDocument(
    val id: UUID,
    val sourceUrl: String,
    val textContent: String,
    val contentSha256: String,
    val fetchedAt: Instant,
)
