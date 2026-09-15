package dev.factweek.processing.internal

import dev.factweek.FactweekApplication
import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.ingestion.FetchedSourceDocument
import dev.factweek.ingestion.SourceDocuments
import dev.factweek.processing.DocumentClassifications
import dev.factweek.processing.SectionId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import java.sql.SQLException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "factweek.article-classification.openai.enabled=false",
        "factweek.article-classification.version=article-section-classification-v2",
    ],
)
@ContextConfiguration(classes = [DocumentClassificationPersistenceTest.ProcessingTestConfiguration::class])
class DocumentClassificationPersistenceTest {
    @Autowired private lateinit var classifications: DocumentClassifications
    @Autowired private lateinit var writer: DocumentClassificationWriter
    @Autowired private lateinit var repository: DocumentSectionClassificationRepository
    @Autowired private lateinit var settings: ArticleSectionClassificationSettings
    @Autowired private lateinit var classifier: ControlledClassifier
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val documentIds = mutableListOf<UUID>()

    @BeforeEach
    fun resetClassifier() {
        classifier.calls.set(0)
        classifier.result = listOf(SectionId("technology"))
    }

    @AfterEach
    fun cleanup() {
        documentIds.forEach { documentId ->
            jdbc.update("delete from document_section_classification where source_document_id = ?", documentId)
            jdbc.update("delete from source_document where candidate_id = ?", documentId)
            jdbc.update("delete from news_candidate where id = ?", documentId)
        }
    }

    @Test
    fun `round trips a classification with multiple sections through PostgreSQL`() {
        val documentId = insertFetchedDocument()
        val classifiedAt = Instant.parse("2026-09-15T10:00:00.123456Z")

        writer.insert(
            DocumentSectionClassificationEntity(
                sourceDocumentId = documentId,
                classificationVersion = "article-section-classification-v1",
                classifiedAt = classifiedAt,
                sections = linkedSetOf("technology", "economy"),
            ),
        )

        val stored = repository.findBySourceDocumentIdAndClassificationVersion(
            documentId,
            "article-section-classification-v1",
        )!!.toDomain()

        assertEquals(documentId, stored.documentId)
        assertEquals("article-section-classification-v1", stored.classificationVersion)
        assertEquals(classifiedAt, stored.classifiedAt)
        assertEquals(listOf(SectionId("economy"), SectionId("technology")), stored.sections)
        assertEquals(1, count("document_section_classification", documentId))
        assertEquals(2, count("document_section_classification_section", documentId))
    }

    @Test
    fun `persists and reuses a successful empty classification`() {
        val documentId = insertFetchedDocument()
        classifier.result = emptyList()

        val first = classifications.classify(documentId)
        val stored = classifications.find(documentId)
        val repeated = classifications.classify(documentId)

        assertNotNull(stored)
        assertEquals(emptyList<SectionId>(), first.sections)
        assertEquals(first, stored)
        assertEquals(first, repeated)
        assertEquals(1, classifier.calls.get())
        assertEquals(1, count("document_section_classification", documentId))
        assertEquals(0, count("document_section_classification_section", documentId))
    }

    @Test
    fun `keeps classification versions separate and reuses the current version`() {
        val documentId = insertFetchedDocument()
        val oldTime = Instant.parse("2026-09-14T10:00:00Z")
        writer.insert(
            DocumentSectionClassificationEntity(
                sourceDocumentId = documentId,
                classificationVersion = "article-section-classification-v1",
                classifiedAt = oldTime,
                sections = linkedSetOf("technology"),
            ),
        )
        classifier.result = listOf(SectionId("economy"))

        val current = classifications.classify(documentId)
        val repeated = classifications.classify(documentId)
        val old = repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1")!!.toDomain()

        assertEquals(listOf(SectionId("technology")), old.sections)
        assertEquals(listOf(SectionId("economy")), current.sections)
        assertEquals(current, repeated)
        assertEquals(1, classifier.calls.get())
        assertEquals(2, count("document_section_classification", documentId))
        assertEquals(1, count("document_section_classification_section", documentId, "article-section-classification-v1"))
        assertEquals(1, count("document_section_classification_section", documentId, "article-section-classification-v2"))
    }

    @Test
    fun `rolls back header when a section row violates its database constraint`() {
        val documentId = insertFetchedDocument()

        assertThrows(RuntimeException::class.java) {
            writer.insert(
                DocumentSectionClassificationEntity(
                    sourceDocumentId = documentId,
                    classificationVersion = "article-section-classification-v1",
                    classifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
                    sections = linkedSetOf("x".repeat(65)),
                ),
            )
        }

        assertEquals(0, count("document_section_classification", documentId))
        assertEquals(0, count("document_section_classification_section", documentId))
        assertEquals(null, repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1"))
    }

    @Test
    fun `propagates a source document foreign key violation without persisting a classification`() {
        val missingDocumentId = UUID.randomUUID()

        val failure = assertThrows(RuntimeException::class.java) {
            writer.insert(
                DocumentSectionClassificationEntity(
                    sourceDocumentId = missingDocumentId,
                    classificationVersion = "article-section-classification-v1",
                    classifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
                    sections = linkedSetOf("technology"),
                ),
            )
        }

        assertTrue(sqlStates(failure).contains("23503"))
        assertEquals(0, count("document_section_classification", missingDocumentId))
        assertEquals(0, count("document_section_classification_section", missingDocumentId))
        assertFalse(sqlStates(failure).contains("23505"))
    }

    @Test
    fun `service does not mistake a foreign key failure for a concurrent classification`() {
        val missingDocumentId = UUID.randomUUID()
        val service = DocumentClassificationService(
            sourceDocuments = object : SourceDocuments {
                override fun existsById(id: UUID): Boolean = true

                override fun findFetchedForFactProposals(
                    maximum: Int,
                    excludedSourceDocumentIds: Set<UUID>,
                ): List<FetchedSourceDocument> = emptyList()

                override fun findFetchedById(id: UUID): FetchedSourceDocument = FetchedSourceDocument(
                    id = missingDocumentId,
                    sourceUrl = "https://example.org/missing",
                    textContent = "Article content",
                    contentSha256 = "a".repeat(64),
                    fetchedAt = Instant.parse("2026-09-15T10:00:00Z"),
                    publisher = "Example",
                    sourceType = CandidateSourceType.PRIMARY_DOCUMENT,
                )
            },
            classifierProvider = StaticListableBeanFactory().also {
                it.addBean("classifier", ControlledClassifier())
            }.getBeanProvider(ArticleSectionClassifier::class.java),
            settings = settings,
            repository = repository,
            writer = writer,
            clock = java.time.Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
        )

        val failure = assertThrows(RuntimeException::class.java) {
            service.classify(missingDocumentId)
        }

        assertTrue(sqlStates(failure).contains("23503"))
        assertEquals(0, count("document_section_classification", missingDocumentId))
        assertEquals(0, count("document_section_classification_section", missingDocumentId))
    }

    @Test
    fun `deleting a classification cascades sections but retains its source document`() {
        val documentId = insertFetchedDocument()
        writer.insert(
            DocumentSectionClassificationEntity(
                sourceDocumentId = documentId,
                classificationVersion = "article-section-classification-v1",
                classifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
                sections = linkedSetOf("technology", "economy"),
            ),
        )
        val before = jdbc.queryForMap(
            "select text_content, content_sha256 from source_document where candidate_id = ?",
            documentId,
        )

        repository.delete(repository.findBySourceDocumentIdAndClassificationVersion(documentId, "article-section-classification-v1")!!)
        repository.flush()

        assertEquals(0, count("document_section_classification", documentId))
        assertEquals(0, count("document_section_classification_section", documentId))
        val after = jdbc.queryForMap(
            "select text_content, content_sha256 from source_document where candidate_id = ?",
            documentId,
        )
        assertEquals(before, after)
    }

    private fun insertFetchedDocument(): UUID {
        val id = UUID.randomUUID()
        documentIds += id
        val now = OffsetDateTime.ofInstant(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)
        jdbc.update(
            """insert into news_candidate
               (id, canonical_url, title, publisher, source_domain, published_at, language, discovery_provider, source_type, fetched_at, status)
               values (?, ?, 'title', 'publisher', 'example.org', ?, 'en', 'MANUAL', 'PRIMARY_DOCUMENT', ?, 'DISCOVERED')""",
            id,
            "https://example.org/$id",
            now,
            now,
        )
        jdbc.update(
            """insert into source_document
               (candidate_id, source_url, status, text_content, content_sha256, fetched_at, last_attempt_at, attempt_count)
               values (?, ?, 'FETCHED', 'Article content', ?, ?, ?, 1)""",
            id,
            "https://example.org/$id",
            "a".repeat(64),
            now,
            now,
        )
        return id
    }

    private fun count(table: String, documentId: UUID, version: String? = null): Int =
        if (version == null) {
            jdbc.queryForObject("select count(*) from $table where source_document_id = ?", Int::class.java, documentId)!!
        } else {
            jdbc.queryForObject(
                "select count(*) from $table where source_document_id = ? and classification_version = ?",
                Int::class.java,
                documentId,
                version,
            )!!
        }

    private fun sqlStates(exception: Throwable): List<String> =
        generateSequence(exception) { it.cause }
            .filterIsInstance<SQLException>()
            .mapNotNull { it.sqlState }
            .toList()

    @TestConfiguration(proxyBeanMethods = false)
    internal class ProcessingTestConfiguration {
        @Bean
        fun classifier(): ControlledClassifier = ControlledClassifier()
    }
}

internal class ControlledClassifier : ArticleSectionClassifier {
    val calls = AtomicInteger()
    var result: List<SectionId> = listOf(SectionId("technology"))

    override fun classify(request: ArticleSectionClassificationRequest): List<SectionId> {
        calls.incrementAndGet()
        return result
    }
}
