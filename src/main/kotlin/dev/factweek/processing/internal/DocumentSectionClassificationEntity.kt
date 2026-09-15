package dev.factweek.processing.internal

import dev.factweek.processing.DocumentClassification
import dev.factweek.processing.SectionId
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "document_section_classification")
@IdClass(DocumentSectionClassificationId::class)
internal class DocumentSectionClassificationEntity(
    @jakarta.persistence.Id
    @Column(name = "source_document_id")
    val sourceDocumentId: UUID,
    @jakarta.persistence.Id
    @Column(name = "classification_version", length = 128)
    val classificationVersion: String,
    @Column(name = "classified_at", nullable = false)
    val classifiedAt: Instant,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "document_section_classification_section",
        joinColumns = [
            JoinColumn(name = "source_document_id", referencedColumnName = "source_document_id"),
            JoinColumn(name = "classification_version", referencedColumnName = "classification_version"),
        ],
    )
    @Column(name = "section_id", nullable = false, length = 64)
    val sections: MutableSet<String> = linkedSetOf(),
) {
    fun toDomain(): DocumentClassification = DocumentClassification(
        documentId = sourceDocumentId,
        classificationVersion = classificationVersion,
        classifiedAt = classifiedAt,
        sections = sections.map(::SectionId).sortedBy { it.value },
    )
}

internal data class DocumentSectionClassificationId(
    val sourceDocumentId: UUID? = null,
    val classificationVersion: String? = null,
) : Serializable
