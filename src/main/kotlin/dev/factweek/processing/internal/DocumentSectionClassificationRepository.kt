package dev.factweek.processing.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface DocumentSectionClassificationRepository :
    JpaRepository<DocumentSectionClassificationEntity, DocumentSectionClassificationId> {
    fun findBySourceDocumentIdAndClassificationVersion(
        sourceDocumentId: UUID,
        classificationVersion: String,
    ): DocumentSectionClassificationEntity?

    @Query(
        value = """
            select classification.source_document_id
            from document_section_classification classification
            join document_section_classification_section section
              on section.source_document_id = classification.source_document_id
             and section.classification_version = classification.classification_version
            where classification.classification_version = :version
              and section.section_id = :sectionId
              and classification.source_document_id in :documentIds
        """,
        nativeQuery = true,
    )
    fun findCurrentDocumentIdsBySection(
        @Param("version") version: String,
        @Param("sectionId") sectionId: String,
        @Param("documentIds") documentIds: Collection<UUID>,
    ): Set<UUID>
}
