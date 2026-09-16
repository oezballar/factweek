package dev.factweek.processing.internal

import dev.factweek.ingestion.SourceDocuments
import dev.factweek.processing.DocumentClassification
import dev.factweek.processing.DocumentClassifications
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
internal class DocumentClassificationService(
    private val sourceDocuments: SourceDocuments,
    private val classifierProvider: ObjectProvider<ArticleSectionClassifier>,
    private val settings: ArticleSectionClassificationSettings,
    private val repository: DocumentSectionClassificationRepository,
    private val writer: DocumentClassificationWriter,
    private val clock: Clock,
) : DocumentClassifications {
    override fun classify(documentId: UUID): DocumentClassification {
        find(documentId)?.let { return it }

        val document = sourceDocuments.findFetchedById(documentId)
            ?: throw documentUnavailable(documentId)
        val classifier = classifierProvider.ifAvailable
            ?: throw ArticleSectionClassifierUnavailableException()
        val sections = classifier.classify(
            ArticleSectionClassificationRequest(
                documentId = document.id,
                sourceUrl = document.sourceUrl,
                textContent = document.textContent,
                supportedSections = settings.configuredSections,
            ),
        )

        val normalizedSections = sections.map { section ->
            try {
                section.also {
                    require(settings.configuredSections.any { configured -> configured.id == it })
                }
            } catch (exception: IllegalArgumentException) {
                throw ArticleSectionClassificationException("The classifier returned an unsupported section key", exception)
            }
        }.toSet()
        val entity = DocumentSectionClassificationEntity(
            sourceDocumentId = document.id,
            classificationVersion = settings.version,
            classifiedAt = clock.instant(),
            sections = normalizedSections.map { it.value }.toCollection(linkedSetOf()),
        )
        try {
            writer.insert(entity)
            return find(documentId)
                ?: throw ArticleSectionClassificationException("Persisted classification could not be read")
        } catch (exception: RuntimeException) {
            if (!isDuplicateClassificationIdentity(exception)) throw exception
            return find(documentId)
                ?: throw ArticleSectionClassificationException("Concurrent classification could not be read")
        }
    }

    override fun find(documentId: UUID): DocumentClassification? =
        repository.findBySourceDocumentIdAndClassificationVersion(documentId, settings.version)?.toDomain()

    override fun findCurrentClassifiedDocumentIds(
        sectionId: dev.factweek.processing.SectionId,
        documentIds: Collection<UUID>,
    ): Set<UUID> =
        if (documentIds.isEmpty()) emptySet()
        else repository.findCurrentDocumentIdsBySection(settings.version, sectionId.value, documentIds)

    private fun documentUnavailable(documentId: UUID): RuntimeException =
        if (sourceDocuments.existsById(documentId)) {
            SourceDocumentNotFetchedException()
        } else {
            SourceDocumentNotFoundException()
        }

    private fun isDuplicateClassificationIdentity(exception: RuntimeException): Boolean =
        generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<org.hibernate.exception.ConstraintViolationException>()
            .any { constraintViolation ->
                constraintViolation.sqlState == "23505" &&
                    constraintViolation.constraintName == "document_section_classification_pk"
            }
}

internal class SourceDocumentNotFoundException : RuntimeException()
internal class SourceDocumentNotFetchedException : RuntimeException()
