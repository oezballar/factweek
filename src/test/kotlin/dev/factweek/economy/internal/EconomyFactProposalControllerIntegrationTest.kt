package dev.factweek.economy.internal

import dev.factweek.FactweekApplication
import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateDiscoveryProvider
import dev.factweek.ingestion.internal.CandidatePersistenceService
import dev.factweek.ingestion.internal.SourceContentFetchResult
import dev.factweek.ingestion.internal.SourceDocumentPersistenceService
import dev.factweek.ingestion.SourceDocuments
import dev.factweek.ingestion.internal.SourceContentFailureReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Instant
import java.math.BigDecimal

@SpringBootTest(classes = [FactweekApplication::class], webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.ai.model.chat=none", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"])
@Testcontainers
class EconomyFactProposalControllerIntegrationTest {
    private lateinit var mvc: MockMvc
    @Autowired private lateinit var context: WebApplicationContext
    @Autowired private lateinit var candidates: CandidatePersistenceService
    @Autowired private lateinit var sourceDocuments: SourceDocumentPersistenceService
    @Autowired private lateinit var sourceDocumentReads: SourceDocuments
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach fun clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.update("delete from economy_fact_proposal_measurement"); jdbc.update("delete from economy_fact_proposal_entity"); jdbc.update("delete from economy_fact_proposal"); jdbc.update("delete from source_document"); jdbc.update("delete from news_candidate")
    }

    @Test fun `captures an event by post and retrieves it by get`() {
        val candidate = fetched("http-event", "Evidence passage for the rate decision. SECRET ARTICLE TEXT")
        val body = """{"sourceDocumentId":"${candidate.id}","statement":"The central bank made a rate decision.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","occurredOn":"2026-09-10","evidenceText":"Evidence passage for the rate decision."}"""
        val response = mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().response.contentAsString
        val id = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(response)!!.groupValues[1]
        mvc.perform(get("/api/v1/economy/fact-proposals/{id}", id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.sourceDocumentId").value(candidate.id.toString()))
            .andExpect(jsonPath("$.statement").value("The central bank made a rate decision.")).andExpect(jsonPath("$.category").value("MONETARY_POLICY"))
            .andExpect(jsonPath("$.eventType").value("MONETARY_POLICY_DECIDED")).andExpect(jsonPath("$.occurredOn").value("2026-09-10"))
            .andExpect(jsonPath("$.evidenceText").value("Evidence passage for the rate decision.")).andExpect(jsonPath("$.createdAt").isNotEmpty)
            .andExpect(jsonPath("$.status").value("PROPOSED")).andExpect(jsonPath("$.entities").isEmpty)
            .andExpect(jsonPath("$.measurement").doesNotExist()).andExpect(jsonPath("$.reviewedAt").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET ARTICLE TEXT"))))
        assertEquals(1L, jdbc.queryForObject("select count(*) from economy_fact_proposal where source_document_id = ?", Long::class.java, candidate.id))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    @Test fun `returns bad request for a real proposal structure error without saving`() {
        val candidate = fetched("http-invalid", "Evidence passage for an invalid indicator.")
        val body = """{"sourceDocumentId":"${candidate.id}","statement":"An indicator was reported.","category":"PRICES_AND_INFLATION","eventType":"INDICATOR_VALUE_REPORTED","evidenceText":"Evidence passage for an invalid indicator."}"""
        mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Evidence passage for an invalid indicator."))))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact_proposal where source_document_id = ?", Long::class.java, candidate.id))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact_proposal_measurement", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    @Test fun `captures an indicator by post and retrieves all fields by get`() {
        val candidate = fetched("http-indicator", "Inflation evidence passage. ARTICLE TEXT NOT FOR RESPONSE")
        val factsBefore = jdbc.queryForObject("select count(*) from economy_fact", Long::class.java)!!
        val body = """{"sourceDocumentId":"${candidate.id}","statement":"Inflation was reported for August 2026.","category":"PRICES_AND_INFLATION","eventType":"INDICATOR_VALUE_REPORTED","referencePeriod":{"from":"2026-08-01","to":"2026-08-31","granularity":"MONTH"},"geography":{"kind":"COUNTRY","name":"Germany","code":"DE"},"measurement":{"value":2.1234567891,"unit":"PERCENT","releaseStatus":"FINAL","seasonalAdjustment":"ADJUSTED","valueBasis":"REAL"},"entities":[{"name":"Statistical Authority","type":"STATISTICAL_AUTHORITY"},{"name":"Inflation","type":"INDICATOR"}],"evidenceText":"Inflation evidence passage."}"""
        val postResponse = mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(postResponse)!!.groupValues[1]
        assertEquals(0, BigDecimal("2.1234567891").compareTo(BigDecimal(Regex("\\\"value\\\":([0-9.]+)").find(postResponse)!!.groupValues[1])))
        val getResponse = mvc.perform(get("/api/v1/economy/fact-proposals/{id}", id)).andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceDocumentId").value(candidate.id.toString()))
            .andExpect(jsonPath("$.category").value("PRICES_AND_INFLATION")).andExpect(jsonPath("$.eventType").value("INDICATOR_VALUE_REPORTED"))
            .andExpect(jsonPath("$.measurement.unit").value("PERCENT")).andExpect(jsonPath("$.measurement.releaseStatus").value("FINAL"))
            .andExpect(jsonPath("$.measurement.seasonalAdjustment").value("ADJUSTED")).andExpect(jsonPath("$.measurement.valueBasis").value("REAL"))
            .andExpect(jsonPath("$.referencePeriod.from").value("2026-08-01")).andExpect(jsonPath("$.referencePeriod.to").value("2026-08-31"))
            .andExpect(jsonPath("$.referencePeriod.granularity").value("MONTH")).andExpect(jsonPath("$.geography.code").value("DE"))
            .andExpect(jsonPath("$.entities").isArray).andExpect(jsonPath("$.occurredOn").doesNotExist()).andExpect(jsonPath("$.status").value("PROPOSED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ARTICLE TEXT NOT FOR RESPONSE")))).andReturn().response.contentAsString
        assertEquals(0, BigDecimal("2.1234567891").compareTo(BigDecimal(Regex("\\\"value\\\":([0-9.]+)").find(getResponse)!!.groupValues[1])))
        assertEquals(1L, jdbc.queryForObject("select count(*) from economy_fact_proposal where source_document_id = ?", Long::class.java, candidate.id))
        assertEquals(factsBefore, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
        assertEquals("Inflation evidence passage. ARTICLE TEXT NOT FOR RESPONSE", jdbc.queryForObject("select text_content from source_document where candidate_id = ?", String::class.java, candidate.id))
    }

    @Test fun `returns problem detail for an invalid proposal id path`() {
        val proposalsBefore = jdbc.queryForObject("select count(*) from economy_fact_proposal", Long::class.java)!!
        val factsBefore = jdbc.queryForObject("select count(*) from economy_fact", Long::class.java)!!
        mvc.perform(get("/api/v1/economy/fact-proposals/not-a-uuid"))
            .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(proposalsBefore, jdbc.queryForObject("select count(*) from economy_fact_proposal", Long::class.java))
        assertEquals(factsBefore, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    @Test fun `returns not found for an unknown source document`() {
        val before = proposalCount()
        mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(eventJson(java.util.UUID.randomUUID(), "Evidence")))
            .andExpect(status().isNotFound).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(before, proposalCount()); assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    @Test fun `returns not found for an unknown proposal`() {
        val before = proposalCount()
        mvc.perform(get("/api/v1/economy/fact-proposals/{id}", java.util.UUID.randomUUID()))
            .andExpect(status().isNotFound).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(before, proposalCount()); assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    @Test fun `returns conflict for a known document that was not fetched`() {
        val candidate = candidates.storeDiscovered(CandidateCaptureCommand(URI("https://example.org/not-fetched"), "not-fetched", "Example", Instant.parse("2026-09-10T00:00:00Z"), "de", CandidateDiscoveryProvider.MANUAL))
        sourceDocuments.recordFailure(candidate, SourceContentFailureReason.HTTP_ERROR)
        assertEquals(true, sourceDocumentReads.existsById(candidate.id)); assertEquals(null, sourceDocumentReads.findFetchedById(candidate.id))
        val before = proposalCount()
        mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(eventJson(candidate.id, "Evidence")))
            .andExpect(status().isConflict).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(before, proposalCount()); assertEquals(1L, jdbc.queryForObject("select count(*) from source_document where candidate_id = ?", Long::class.java, candidate.id))
    }

    @Test fun `rejects syntactically invalid json`() {
        assertBadPost("{\"sourceDocumentId\":")
    }

    @Test fun `rejects invalid document uuid`() {
        assertBadPost(eventJson("not-a-uuid", "Evidence"))
    }

    @Test fun `rejects unknown enum value`() {
        val candidate = fetched("unknown-enum", "Evidence")
        assertBadPost(eventJson(candidate.id, "Evidence").replace("MONETARY_POLICY", "UNKNOWN_CATEGORY"))
    }

    @Test fun `rejects empty and whitespace evidence passages`() {
        val candidate = fetched("empty-evidence", "Evidence")
        listOf("", "   ").forEach { evidence -> assertBadPost(eventJson(candidate.id, evidence)) }
    }

    @Test fun `rejects an evidence passage absent from the article`() {
        val candidate = fetched("absent-evidence", "Actual article evidence")
        assertBadPost(eventJson(candidate.id, "Not contained in article"))
    }

    private fun assertBadPost(body: String) {
        val before = proposalCount()
        mvc.perform(post("/api/v1/economy/fact-proposals").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").isNotEmpty)
        assertEquals(before, proposalCount())
        assertEquals(0L, jdbc.queryForObject("select count(*) from economy_fact", Long::class.java))
    }

    private fun proposalCount() = jdbc.queryForObject("select count(*) from economy_fact_proposal", Long::class.java)!!
    private fun eventJson(documentId: Any, evidence: String) = """{"sourceDocumentId":"$documentId","statement":"A policy decision.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","evidenceText":"$evidence"}"""

    private fun fetched(path: String, text: String) = candidates.storeDiscovered(CandidateCaptureCommand(URI("https://example.org/$path"), path, "Example", Instant.parse("2026-09-10T00:00:00Z"), "de", CandidateDiscoveryProvider.MANUAL)).also { sourceDocuments.recordSuccess(it, SourceContentFetchResult(URI(it.canonicalUrl), "text/html", 200, text, "a".repeat(64))) }
    companion object { @Container @JvmStatic @ServiceConnection val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres")) }
}
