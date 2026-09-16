package dev.factweek.processing

import java.time.Instant
import java.util.UUID

@JvmInline
value class SectionId(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid section id" }
    }
}

data class DocumentClassification(
    val documentId: UUID,
    val classificationVersion: String,
    val classifiedAt: Instant,
    val sections: List<SectionId>,
)

interface DocumentClassifications {
    fun classify(documentId: UUID): DocumentClassification
    fun find(documentId: UUID): DocumentClassification?

    /** Returns the current-version subset of [documentIds] assigned to [sectionId], without classifying. */
    fun findCurrentClassifiedDocumentIds(sectionId: SectionId, documentIds: Collection<UUID>): Set<UUID> = emptySet()
}
