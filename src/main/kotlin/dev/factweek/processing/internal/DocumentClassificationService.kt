package dev.factweek.processing.internal

import dev.factweek.ingestion.SourceDocuments
import dev.factweek.processing.DocumentClassification
import dev.factweek.processing.DocumentClassifications
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

@Service
internal class DocumentClassificationService(
    private val sourceDocuments: SourceDocuments,
    private val classifierProvider: ObjectProvider<ArticleSectionClassifier>,
    private val settings: ArticleSectionClassificationSettings,
    private val repository: DocumentSectionClassificationRepository,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : DocumentClassifications {
    override fun classify(documentId: UUID): DocumentClassification {
        find(documentId)?.let { return it }

        val document = sourceDocuments.findFetchedById(documentId)
            ?: throw documentUnavailable(documentId)
        val classifier = classifierProvider.ifAvailable
            ?: throw ArticleSectionClassificationException("No article section classifier is configured")
        val sections = classifier.classify(
            ArticleSectionClassificationRequest(
                documentId = document.id,
                sourceUrl = document.sourceUrl,
                textContent = document.textContent,
                supportedSections = settings.sections,
            ),
        )

        try {
            return requireNotNull(transactionTemplate.execute {
                repository.saveAndFlush(
                    DocumentSectionClassificationEntity(
                        sourceDocumentId = document.id,
                        classificationVersion = settings.version,
                        classifiedAt = clock.instant(),
                        sections = sections.map { it.value }.toCollection(linkedSetOf()),
                    ),
                ).toDomain()
            })
        } catch (_: DataIntegrityViolationException) {
            return find(documentId)
                ?: throw ArticleSectionClassificationException("Concurrent classification could not be read")
        }
    }

    override fun find(documentId: UUID): DocumentClassification? =
        repository.findBySourceDocumentIdAndClassificationVersion(documentId, settings.version)?.toDomain()

    private fun documentUnavailable(documentId: UUID): RuntimeException =
        if (sourceDocuments.existsById(documentId)) {
            SourceDocumentNotFetchedException()
        } else {
            SourceDocumentNotFoundException()
        }
}

internal class SourceDocumentNotFoundException : RuntimeException()
internal class SourceDocumentNotFetchedException : RuntimeException()
