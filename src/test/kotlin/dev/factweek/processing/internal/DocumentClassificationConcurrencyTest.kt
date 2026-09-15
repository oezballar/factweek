package dev.factweek.processing.internal

import dev.factweek.FactweekApplication
import dev.factweek.processing.DocumentClassifications
import dev.factweek.processing.SectionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["factweek.article-classification.openai.enabled=false"],
)
@ContextConfiguration(classes = [DocumentClassificationConcurrencyTest.ProcessingTestConfiguration::class])
class DocumentClassificationConcurrencyTest {
    @Autowired private lateinit var classifications: DocumentClassifications
    @Autowired private lateinit var repository: DocumentSectionClassificationRepository
    @Autowired private lateinit var classifier: CoordinatedClassifier
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `concurrent first classifications persist and return one immutable winner`() {
        val documentId = insertFetchedDocument()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(Callable { classifications.classify(documentId) })
            val second = executor.submit(Callable { classifications.classify(documentId) })

            assertTrue(classifier.reached.await(10, TimeUnit.SECONDS), "both calls must reach the classifier")
            classifier.release.countDown()

            val firstResult = first.get(10, TimeUnit.SECONDS)
            val secondResult = second.get(10, TimeUnit.SECONDS)
            val stored = classifications.find(documentId)!!

            assertEquals(2, classifier.calls)
            assertFalse(classifier.transactionWasActive)
            assertEquals(firstResult, secondResult)
            assertEquals(stored, firstResult)
            assertEquals(1, jdbc.queryForObject("select count(*) from document_section_classification where source_document_id = ?", Int::class.java, documentId))
            assertEquals(1, jdbc.queryForObject("select count(*) from document_section_classification_section where source_document_id = ?", Int::class.java, documentId))
            assertTrue(stored.sections in listOf(listOf(SectionId("technology")), listOf(SectionId("economy"))))

            val repeated = classifications.classify(documentId)
            assertEquals(stored, repeated)
            assertEquals(2, classifier.calls)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            repository.deleteAll()
            jdbc.update("delete from source_document where candidate_id = ?", documentId)
            jdbc.update("delete from news_candidate where id = ?", documentId)
        }
    }

    private fun insertFetchedDocument(): UUID {
        val id = UUID.randomUUID()
        val now = Instant.parse("2026-09-15T10:00:00Z")
        jdbc.update(
            """insert into news_candidate
               (id, canonical_url, title, publisher, source_domain, published_at, language, discovery_provider, source_type, fetched_at, status)
               values (?, ?, 'title', 'publisher', 'example.org', ?, 'en', 'MANUAL', 'PRIMARY_DOCUMENT', ?, 'DISCOVERED')""",
            id, "https://example.org/$id", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC), OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
        )
        jdbc.update(
            """insert into source_document
               (candidate_id, source_url, status, text_content, content_sha256, fetched_at, last_attempt_at, attempt_count)
               values (?, ?, 'FETCHED', 'Article content', ?, ?, ?, 1)""",
            id, "https://example.org/$id", "a".repeat(64), OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC), OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
        )
        return id
    }

    @TestConfiguration(proxyBeanMethods = false)
    internal class ProcessingTestConfiguration {
        @Bean
        fun classifier(): CoordinatedClassifier = CoordinatedClassifier()
    }
}

internal class CoordinatedClassifier : ArticleSectionClassifier {
    val reached = CountDownLatch(2)
    val release = CountDownLatch(1)
    @Volatile var transactionWasActive = false
    @Volatile var calls = 0

    override fun classify(request: ArticleSectionClassificationRequest): List<SectionId> {
        transactionWasActive = transactionWasActive || TransactionSynchronizationManager.isActualTransactionActive()
        val call = synchronized(this) { ++calls }
        reached.countDown()
        check(release.await(10, TimeUnit.SECONDS)) { "test release timed out" }
        return if (call == 1) listOf(SectionId("technology")) else listOf(SectionId("economy"))
    }
}
