package dev.factweek.ingestion

import java.time.Instant
import java.util.UUID

/**
 * Public read API for successfully retrieved source content.
 * A document that reached FETCHED is immutable through regular application write paths.
 */
interface SourceDocuments {
    /** Returns whether a candidate with this source-document identity exists, regardless of fetch status. */
    fun existsById(id: UUID): Boolean

    /**
     * Returns at most [maximum] fetched documents whose ids are not in [excludedSourceDocumentIds].
     * Exclusion is applied by the database before the limit.
     */
    fun findFetchedForFactProposals(
        maximum: Int,
        excludedSourceDocumentIds: Set<UUID>,
    ): List<FetchedSourceDocument>

    /**
     * Reads the next bounded page in the established fetched-at/id order.
     * The default preserves existing implementations that only need the first page.
     */
    fun findFetchedForFactProposals(
        maximum: Int,
        excludedSourceDocumentIds: Set<UUID>,
        page: Int,
    ): List<FetchedSourceDocument> {
        require(page >= 0) { "Page must not be negative" }
        return if (page == 0) findFetchedForFactProposals(maximum, excludedSourceDocumentIds) else emptyList()
    }

    /** Returns one successfully fetched document for a review, without exposing a JPA entity. */
    fun findFetchedById(id: UUID): FetchedSourceDocument?
}

data class FetchedSourceDocument(
    val id: UUID,
    val sourceUrl: String,
    val textContent: String,
    val contentSha256: String,
    val fetchedAt: Instant,
    val publisher: String,
    val sourceType: CandidateSourceType,
    /** Optional publication timestamp supplied by the candidate's discovery adapter. */
    val publishedAt: Instant? = null,
)
