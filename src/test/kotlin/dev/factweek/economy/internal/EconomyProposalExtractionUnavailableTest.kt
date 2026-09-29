package dev.factweek.economy.internal

import dev.factweek.FactweekApplication
import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateDiscoveryProvider
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.ai.model.chat=none", "factweek.economy.extraction.openai.enabled=false", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"],
)
@Testcontainers
class EconomyProposalExtractionUnavailableTest {
    @Autowired private lateinit var context: WebApplicationContext
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var documents: SourceDocumentPersistenceService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `disabled model keeps context available and returns service unavailable after preconditions`() {
        val sourcePathId = UUID.randomUUID()
        val candidate = candidates.storeDiscovered(CandidateCaptureCommand(
            sourceUrl = URI("https://example.org/$sourcePathId"),
            title = "Economy article",
            publisher = "Example",
            publishedAt = null,
            language = "de",
            discoveryProvider = CandidateDiscoveryProvider.MANUAL,
        ))
        documents.recordSuccess(candidate, SourceContentFetchResult(
            URI(candidate.canonicalUrl), "text/html", 200, "Economic article text", "a".repeat(64),
        ))
        val id = candidate.id
        jdbc.update(
            "insert into document_section_classification (source_document_id, classification_version, classified_at) values (?, ?, ?)",
            id, "article-section-classification-v1", OffsetDateTime.now(ZoneOffset.UTC),
        )
        jdbc.update(
            "insert into document_section_classification_section (source_document_id, classification_version, section_id) values (?, ?, 'economy')",
            id, "article-section-classification-v1",
        )
        val before = jdbc.queryForObject("select count(*) from economy_fact_proposal", Long::class.java)
        val mvc = MockMvcBuilders.webAppContextSetup(context).build()

        mvc.perform(post("/api/v1/economy/fact-proposals/extractions/{sourceDocumentId}", id))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(before, jdbc.queryForObject("select count(*) from economy_fact_proposal", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
