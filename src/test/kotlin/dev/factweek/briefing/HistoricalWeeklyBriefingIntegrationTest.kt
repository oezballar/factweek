package dev.factweek.briefing

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import dev.factweek.FactweekApplication
import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import dev.factweek.technology.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.jpa.hibernate.ddl-auto=validate", "spring.ai.model.chat=none", "factweek.fact-proposals.openai.enabled=false", "factweek.article-classification.openai.enabled=false"],
)
@Import(HistoricalWeeklyBriefingIntegrationTest.FixedClock::class)
@Testcontainers
class HistoricalWeeklyBriefingIntegrationTest {
    @Autowired private lateinit var economy: EconomyFacts
    @Autowired private lateinit var technology: TechnologyFacts
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var context: WebApplicationContext
    @Autowired private lateinit var mapper: JsonMapper
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        listOf("economy_fact_measurement", "economy_fact_entity", "economy_fact_source", "economy_fact",
            "technology_fact_entity", "technology_fact_source", "technology_fact").forEach {
            jdbc.update("delete from $it")
        }
    }

    @Test
    fun `economy HTTP selection retains reference precedence boundaries and current equivalence`() {
        val start = economyFact("start", LocalDate.parse("2026-09-21"))
        val end = economyFact("end", LocalDate.parse("2026-09-27"))
        economyFact("before", LocalDate.parse("2026-09-20"))
        economyFact("after", LocalDate.parse("2026-09-28"))
        economyFact("precedence", LocalDate.parse("2026-09-28"), "2026-09-24T12:00:00Z")
        val fallback = economyFact("utc fallback", null, "2026-09-27T23:30:00Z")
        economyFact("utc outside", null, "2026-09-28T00:00:00Z")
        economyFact("undated", null, null)
        val later = economyFact("accepted later", LocalDate.parse("2026-09-24"), "2026-10-03T00:00:00Z")
        economy.publish(PublishEconomyFact(
            statement = "Measured in Q3", category = EconomyCategory.PRICES_AND_INFLATION,
            eventType = EconomyEventType.INDICATOR_VALUE_REPORTED, evidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
            occurredOn = null,
            referencePeriod = EconomyReferencePeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-09-30"), EconomyReferencePeriodGranularity.QUARTER),
            measurement = EconomyMeasurement(BigDecimal("2.0"), EconomyMeasurementUnit.PERCENT),
            sources = listOf(source(null)),
        ))
        val explicit = response("economy")
        val current = response("economy", current = true)
        assertEquals(setOf(start, end, fallback, later), ids(explicit))
        assertEquals(ids(explicit), ids(current))
        assertEquals("2026-09-21", explicit["from"].asString())
        assertEquals("2026-09-27", explicit["to"].asString())
        assertEquals("2026-09-27T12:00:00Z", explicit["generatedAt"].asString())
        assertEquals(4, explicit["factCount"].asInt())
        assertEquals("SOURCE_PUBLISHED_AT", explicit["facts"].valueStream().toList().first { it["id"].asString() == fallback }["referenceDateBasis"].asString())
        assertEquals("2026-09-27", explicit["facts"].valueStream().toList().first { it["id"].asString() == fallback }["referenceDate"].asString())
        assertEquals("OCCURRED_ON", explicit["facts"].valueStream().toList().first { it["id"].asString() == later }["referenceDateBasis"].asString())
        mvc.perform(get("/api/v1/briefings/economy").param("from", "2026-09-21").param("to", "2026-09-27")
            .param("categories", "PRICES_AND_INFLATION").param("maximum", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.factCount").value(0))
        mvc.perform(get("/api/v1/briefings/economy").param("from", "2026-09-21").param("to", "2026-09-27")
            .param("categories", "MONETARY_POLICY").param("maximum", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.factCount").value(1))
        assertEquals(0, response("economy", "from=2025-01-01&to=2025-01-07")["factCount"].asInt())
    }

    @Test
    fun `technology HTTP selection retains reference precedence boundaries and current equivalence`() {
        val start = technologyFact("start", LocalDate.parse("2026-09-21"))
        val end = technologyFact("end", LocalDate.parse("2026-09-27"))
        technologyFact("before", LocalDate.parse("2026-09-20"))
        technologyFact("after", LocalDate.parse("2026-09-28"))
        technologyFact("precedence", LocalDate.parse("2026-09-28"), "2026-09-24T12:00:00Z")
        val fallback = technologyFact("utc fallback", null, "2026-09-27T23:30:00Z")
        technologyFact("utc outside", null, "2026-09-28T00:00:00Z")
        technologyFact("undated", null, null)
        val later = technologyFact("accepted later", LocalDate.parse("2026-09-24"), "2026-10-03T00:00:00Z")
        val explicit = response("technology")
        val current = response("technology", current = true)
        assertEquals(setOf(start, end, fallback, later), ids(explicit))
        assertEquals(ids(explicit), ids(current))
        assertEquals("2026-09-27T12:00:00Z", explicit["generatedAt"].asString())
        assertEquals("SOURCE_PUBLISHED_AT", explicit["facts"].valueStream().toList().first { it["id"].asString() == fallback }["referenceDateBasis"].asString())
        assertEquals("2026-09-27", explicit["facts"].valueStream().toList().first { it["id"].asString() == fallback }["referenceDate"].asString())
        assertEquals("OCCURRED_ON", explicit["facts"].valueStream().toList().first { it["id"].asString() == later }["referenceDateBasis"].asString())
        mvc.perform(get("/api/v1/briefings/technology").param("from", "2026-09-21").param("to", "2026-09-27")
            .param("categories", "ENERGY_AND_CLIMATE").param("maximum", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.factCount").value(0))
        mvc.perform(get("/api/v1/briefings/technology").param("from", "2026-09-21").param("to", "2026-09-27")
            .param("categories", "AI_AND_SOFTWARE").param("maximum", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.factCount").value(1))
        assertEquals(0, response("technology", "from=2025-01-01&to=2025-01-07")["factCount"].asInt())
    }

    @Test
    fun `both explicit endpoints return problem detail for invalid windows and filters`() {
        for (kind in listOf("economy", "technology")) {
            val path = "/api/v1/briefings/$kind"
            val invalid = listOf(
                emptyMap(), mapOf("from" to "2026-09-21"), mapOf("to" to "2026-09-27"),
                mapOf("from" to "2026-02-30", "to" to "2026-03-07"),
                mapOf("from" to "2026-09-27", "to" to "2026-09-21"),
                mapOf("from" to "2026-09-21", "to" to "2026-09-26"),
                mapOf("from" to "2026-09-21", "to" to "2026-09-28"),
            )
            for (params in invalid) {
                val request = get(path)
                params.forEach { (key, value) -> request.param(key, value) }
                mvc.perform(request).andExpect(status().isBadRequest)
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").isNotEmpty)
            }
            for (maximum in listOf("0", "51")) {
                mvc.perform(get(path).param("from", "2026-09-21").param("to", "2026-09-27").param("maximum", maximum))
                    .andExpect(status().isBadRequest)
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
            }
            mvc.perform(get(path).param("from", "2026-09-21").param("to", "2026-09-27").param("categories", "UNKNOWN"))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
        }
    }

    private fun economyFact(statement: String, occurredOn: LocalDate?, publishedAt: String? = "2026-09-24T12:00:00Z"): String =
        economy.publish(PublishEconomyFact(
            statement = statement, category = EconomyCategory.MONETARY_POLICY,
            eventType = EconomyEventType.MONETARY_POLICY_DECIDED,
            evidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
            occurredOn = occurredOn, sources = listOf(source(publishedAt)),
        )).id.toString()

    private fun technologyFact(statement: String, occurredOn: LocalDate?, publishedAt: String? = "2026-09-24T12:00:00Z"): String =
        technology.publish(PublishTechnologyFact(
            statement = statement, category = TechnologyCategory.AI_AND_SOFTWARE,
            eventType = TechnologyEventType.RESEARCH_RESULT_PUBLISHED,
            readiness = TechnologyReadiness.LAB_RESULT,
            evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
            occurredOn = occurredOn, entities = emptyList(), sources = listOf(source(publishedAt)),
        )).id.toString()

    private fun source(publishedAt: String?) = SourceReference(
        "https://example.org/source", "Example", SourceType.PRIMARY_DOCUMENT,
        publishedAt?.let(Instant::parse),
    )

    private fun response(kind: String, query: String = "from=2026-09-21&to=2026-09-27", current: Boolean = false): JsonNode {
        val path = if (current) "/api/v1/briefings/$kind/current" else "/api/v1/briefings/$kind"
        val request = get(path)
        if (!current) query.split('&').forEach { parameter ->
            val (key, value) = parameter.split('=')
            request.param(key, value)
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    private fun ids(response: JsonNode): Set<String> = response["facts"].valueStream().toList().map { it["id"].asString() }.toSet()

    @TestConfiguration
    class FixedClock {
        @Bean @Primary
        fun historicalBriefingClock(): Clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
    }
}
