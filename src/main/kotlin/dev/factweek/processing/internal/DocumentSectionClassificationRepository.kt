package dev.factweek.processing.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface DocumentSectionClassificationRepository :
    JpaRepository<DocumentSectionClassificationEntity, DocumentSectionClassificationId> {
    fun findBySourceDocumentIdAndClassificationVersion(
        sourceDocumentId: UUID,
        classificationVersion: String,
    ): DocumentSectionClassificationEntity?
}
