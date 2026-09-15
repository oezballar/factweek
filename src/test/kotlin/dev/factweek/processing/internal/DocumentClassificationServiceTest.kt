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
import org.springframework.transaction.support.TransactionTemplate
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

    private fun service(
        repository: DocumentSectionClassificationRepository,
        classifier: ArticleSectionClassifier,
    ): DocumentClassificationService {
        val sourceDocuments = FakeSourceDocuments()
        val beanFactory = StaticListableBeanFactory()
        beanFactory.addBean("classifier", classifier)
        val provider = beanFactory.getBeanProvider(ArticleSectionClassifier::class.java)
        val transactionTemplate = TransactionTemplate(
            object : org.springframework.transaction.PlatformTransactionManager {
                override fun getTransaction(definition: org.springframework.transaction.TransactionDefinition?) =
                    org.springframework.transaction.support.SimpleTransactionStatus()

                override fun commit(status: org.springframework.transaction.TransactionStatus) = Unit

                override fun rollback(status: org.springframework.transaction.TransactionStatus) = Unit
            },
        )
        return DocumentClassificationService(
            sourceDocuments,
            provider,
            ArticleSectionClassificationSettings("gpt-5-mini", "article-section-classification-v1", "Technology", "Economy"),
            repository,
            transactionTemplate,
            Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
        )
    }

    private fun anyRequest(): ArticleSectionClassificationRequest =
        org.mockito.ArgumentMatchers.any(ArticleSectionClassificationRequest::class.java)
            ?: ArticleSectionClassificationRequest(
                documentId = UUID.randomUUID(),
                sourceUrl = "https://example.org",
                textContent = "",
                supportedSections = emptyList(),
            )

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
