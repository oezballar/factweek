package dev.factweek.processing.internal

import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.ingestion.FetchedSourceDocument
import dev.factweek.ingestion.SourceDocuments
import dev.factweek.processing.SectionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.support.StaticListableBeanFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class DocumentClassificationServiceTest {
    @Test
    fun `returns existing classification without calling the model`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val documentId = UUID.randomUUID()
        val existing = DocumentSectionClassificationEntity(
            documentId,
            "article-section-classification-v1",
            Instant.parse("2026-09-15T10:00:00Z"),
            linkedSetOf("economy"),
        )
        `when`(repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1"))
            .thenReturn(existing)
        val classifier = mock(ArticleSectionClassifier::class.java)

        val result = service(repository, classifier).classify(documentId)

        assertEquals(listOf(SectionId("economy")), result.sections)
        verify(classifier, never()).classify(anyRequest())
    }

    @Test
    fun `returns a stable sorted deduplicated successful empty capable classification`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val documentId = UUID.randomUUID()
        val classifier = mock(ArticleSectionClassifier::class.java)
        `when`(classifier.classify(anyRequest())).thenReturn(
            listOf(SectionId("technology"), SectionId("economy"), SectionId("technology")),
        )
        `when`(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer { invocation -> invocation.arguments[0] }
        `when`(repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1"))
            .thenReturn(
                null,
                DocumentSectionClassificationEntity(
                    documentId,
                    "article-section-classification-v1",
                    Instant.parse("2026-09-15T10:00:00Z"),
                    linkedSetOf("economy", "technology"),
                ),
            )

        val result = service(repository, classifier).classify(documentId)

        assertEquals(listOf(SectionId("economy"), SectionId("technology")), result.sections)
        verify(classifier).classify(anyRequest())
    }

    @Test
    fun `does not create a successful classification after classifier failure`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val classifier = mock(ArticleSectionClassifier::class.java)
        `when`(classifier.classify(anyRequest()))
            .thenThrow(ArticleSectionClassificationException("invalid response"))

        assertThrows(ArticleSectionClassificationException::class.java) {
            service(repository, classifier).classify(UUID.randomUUID())
        }
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `rejects an unsupported section returned by a classifier before writing`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val classifier = mock(ArticleSectionClassifier::class.java)
        `when`(classifier.classify(anyRequest())).thenReturn(listOf(SectionId("politics")))
        val writer = mock(DocumentClassificationWriter::class.java)

        assertThrows(ArticleSectionClassificationException::class.java) {
            service(repository, classifier, writer).classify(UUID.randomUUID())
        }

        verify(writer, never()).insert(anyEntity())
    }

    @Test
    fun `reuses an existing classification when no classifier is available`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val documentId = UUID.randomUUID()
        val existing = DocumentSectionClassificationEntity(
            documentId,
            "article-section-classification-v1",
            Instant.parse("2026-09-15T10:00:00Z"),
            linkedSetOf(),
        )
        `when`(repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1"))
            .thenReturn(existing)

        val result = serviceWithoutClassifier(repository).classify(documentId)

        assertEquals(existing.toDomain(), result)
    }

    @Test
    fun `reports unavailable classifier only for a document without a stored result`() {
        val repository = mock(DocumentSectionClassificationRepository::class.java)
        val documentId = UUID.randomUUID()
        `when`(repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1"))
            .thenReturn(null)

        assertThrows(ArticleSectionClassifierUnavailableException::class.java) {
            serviceWithoutClassifier(repository).classify(documentId)
        }
    }

    private fun service(
        repository: DocumentSectionClassificationRepository,
        classifier: ArticleSectionClassifier,
        writer: DocumentClassificationWriter = mock(DocumentClassificationWriter::class.java),
    ): DocumentClassificationService {
        val sourceDocuments = FakeSourceDocuments()
        val beanFactory = StaticListableBeanFactory()
        beanFactory.addBean("classifier", classifier)
        val provider = beanFactory.getBeanProvider(ArticleSectionClassifier::class.java)
        return DocumentClassificationService(
            sourceDocuments,
            provider,
            settings(),
            repository,
            writer,
            Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
        )
    }

    private fun serviceWithoutClassifier(repository: DocumentSectionClassificationRepository): DocumentClassificationService =
        DocumentClassificationService(
            FakeSourceDocuments(),
            StaticListableBeanFactory().getBeanProvider(ArticleSectionClassifier::class.java),
            settings(),
            repository,
            mock(DocumentClassificationWriter::class.java),
            Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
        )

    private fun anyRequest(): ArticleSectionClassificationRequest =
        org.mockito.ArgumentMatchers.any(ArticleSectionClassificationRequest::class.java)
            ?: ArticleSectionClassificationRequest(
                documentId = UUID.randomUUID(),
                sourceUrl = "https://example.org",
                textContent = "",
                supportedSections = emptyList(),
            )

    private fun anyEntity(): DocumentSectionClassificationEntity =
        org.mockito.ArgumentMatchers.any(DocumentSectionClassificationEntity::class.java)
            ?: DocumentSectionClassificationEntity(
                UUID.randomUUID(),
                "test",
                Instant.EPOCH,
            )

    private fun settings(): ArticleSectionClassificationSettings = ArticleSectionClassificationSettings(
        version = "article-section-classification-v1",
        sections = listOf(
            ArticleSectionClassificationSettings.SectionProperties().apply {
                id = "technology"
                description = "Technology"
            },
            ArticleSectionClassificationSettings.SectionProperties().apply {
                id = "economy"
                description = "Economy"
            },
        ),
    ).also { it.validate() }

    private class FakeSourceDocuments : SourceDocuments {
        override fun existsById(id: UUID): Boolean = true

        override fun findFetchedForFactProposals(
            maximum: Int,
            excludedSourceDocumentIds: Set<UUID>,
        ): List<FetchedSourceDocument> = emptyList()

        override fun findFetchedById(id: UUID): FetchedSourceDocument = FetchedSourceDocument(
            id = id,
            sourceUrl = "https://example.org/article",
            textContent = "Article text",
            contentSha256 = "a".repeat(64),
            fetchedAt = Instant.parse("2026-09-15T09:00:00Z"),
            publisher = "Example",
            sourceType = CandidateSourceType.NEWS_REPORT,
        )
    }
}
