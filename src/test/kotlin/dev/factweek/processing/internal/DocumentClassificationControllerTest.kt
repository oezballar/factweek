package dev.factweek.processing.internal

import dev.factweek.processing.DocumentClassification
import dev.factweek.processing.DocumentClassifications
import dev.factweek.processing.SectionId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class DocumentClassificationControllerTest {
    private lateinit var classifications: RecordingClassifications
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        classifications = RecordingClassifications()
        mockMvc = MockMvcBuilders.standaloneSetup(DocumentClassificationController(classifications)).build()
    }

    @Test
    fun `post returns a flat sorted classification without source content`() {
        val documentId = UUID.randomUUID()
        classifications.classification = classification(documentId, listOf("technology", "economy"))

        mockMvc.perform(post("/api/v1/processing/documents/$documentId/classification"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.classificationVersion").value("article-section-classification-v1"))
            .andExpect(jsonPath("$.classifiedAt").value("2026-09-15T10:00:00Z"))
            .andExpect(jsonPath("$.sections[0]").value("economy"))
            .andExpect(jsonPath("$.sections[1]").value("technology"))
            .andExpect(jsonPath("$.textContent").doesNotExist())
            .andExpect(jsonPath("$.rawResponse").doesNotExist())
            .andExpect(jsonPath("$.summary").doesNotExist())
            .andExpect(jsonPath("$.narrative").doesNotExist())
    }

    @Test
    fun `get returns an existing empty classification without invoking classify`() {
        val documentId = UUID.randomUUID()
        classifications.classification = classification(documentId, emptyList())

        mockMvc.perform(get("/api/v1/processing/documents/$documentId/classification"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sections").isArray)
            .andExpect(jsonPath("$.sections").isEmpty)

        org.junit.jupiter.api.Assertions.assertEquals(0, classifications.classifyCalls)
    }

    @Test
    fun `repeated post returns existing result`() {
        val documentId = UUID.randomUUID()
        classifications.classification = classification(documentId, listOf("technology"))

        mockMvc.perform(post("/api/v1/processing/documents/$documentId/classification"))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/processing/documents/$documentId/classification"))
            .andExpect(status().isOk)

        org.junit.jupiter.api.Assertions.assertEquals(2, classifications.classifyCalls)
    }

    @Test
    fun `maps classification failures to bad gateway problem details`() {
        classifications.failure = ArticleSectionClassificationException("model response invalid")

        mockMvc.perform(post("/api/v1/processing/documents/${UUID.randomUUID()}/classification"))
            .andExpect(status().isBadGateway)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.detail").value("Article classification failed"))
    }

    @Test
    fun `maps unavailable classifier to service unavailable problem detail`() {
        classifications.failure = ArticleSectionClassifierUnavailableException()

        mockMvc.perform(post("/api/v1/processing/documents/${UUID.randomUUID()}/classification"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.detail").value("Article section classifier is not available"))
    }

    @Test
    fun `maps missing results and invalid ids to problem details`() {
        mockMvc.perform(get("/api/v1/processing/documents/${UUID.randomUUID()}/classification"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))

        mockMvc.perform(post("/api/v1/processing/documents/not-a-uuid/classification"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `maps missing and not fetched source documents to their established problem details`() {
        classifications.failure = SourceDocumentNotFoundException()
        mockMvc.perform(post("/api/v1/processing/documents/${UUID.randomUUID()}/classification"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))

        classifications.failure = SourceDocumentNotFetchedException()
        mockMvc.perform(post("/api/v1/processing/documents/${UUID.randomUUID()}/classification"))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
    }

    private fun classification(documentId: UUID, sections: List<String>) = DocumentClassification(
        documentId = documentId,
        classificationVersion = "article-section-classification-v1",
        classifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
        sections = sections.map(::SectionId).sortedBy { it.value },
    )

    private class RecordingClassifications : DocumentClassifications {
        var classification: DocumentClassification? = null
        var failure: RuntimeException? = null
        var classifyCalls = 0

        override fun classify(documentId: UUID): DocumentClassification {
            classifyCalls++
            failure?.let { throw it }
            return classification ?: throw SourceDocumentNotFoundException()
        }

        override fun find(documentId: UUID): DocumentClassification? = classification
    }
}
