package dev.factweek.ingestion

import java.time.Instant
import java.util.UUID

/** Public read API for successfully retrieved source content. */
interface SourceDocuments {
    /**
     * Returns at most [maximum] fetched documents whose ids are not in [excludedSourceDocumentIds].
     * Exclusion is applied by the database before the limit.
     */
    fun findFetchedForFactProposals(
        maximum: Int,
        excludedSourceDocumentIds: Set<UUID>,
    ): List<FetchedSourceDocument>

    /** Returns one successfully fetched document for a review, without exposing a JPA entity. */
    fun findFetchedById(id: UUID): FetchedSourceDocument?
}

data class FetchedSourceDocument(
    val id: UUID,
    val sourceUrl: String,
    val textContent: String,
    val contentSha256: String,
    val fetchedAt: Instant,
)
