package dev.factweek.economy.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class OpenAiEconomyProposalStructuredOutputTest {
    private val schema = JsonMapper.builder().build()
        .readTree(OpenAiEconomyProposalStructuredOutput().jsonSchema)

    @Test
    fun `schema requires all properties and forbids extras recursively`() {
        assertStrictObject(schema)
        val proposal = schema.path("properties").path("proposals").path("items")
        for (name in listOf("referencePeriod", "measurement")) {
            val types = proposal.path("properties").path(name).path("type")
                .iterator().asSequence().map(JsonNode::asString).toSet()
            assertTrue("null" in types, "$name must be nullable")
        }
        for (name in listOf("occurredOn", "geography")) {
            val branches = proposal.path("properties").path(name).path("anyOf")
            assertTrue(branches.any { it.path("type").asString() == "null" }, "$name must be nullable")
        }
        assertEquals(5, schema.path("properties").path("proposals").path("maxItems").asInt())
        assertFalse(schema.path("properties").path("proposals").has("minItems"))
        assertEquals("date", proposal.path("properties").path("occurredOn").path("anyOf")[0].path("format").asString())
        val period = proposal.path("properties").path("referencePeriod")
        assertEquals("date", period.path("properties").path("from").path("format").asString())
        assertEquals("date", period.path("properties").path("to").path("format").asString())
        val geography = proposal.path("properties").path("geography").path("anyOf")
        val byKind = geography.filter { it.path("type").asString() == "object" }
            .associateBy { it.path("properties").path("kind").path("enum")[0].asString() }
        assertEquals(setOf("COUNTRY", "REGION", "GLOBAL"), byKind.keys)
        assertEquals("string", byKind.getValue("COUNTRY").path("properties").path("code").path("type").asString())
        assertEquals(setOf("string", "null"), byKind.getValue("REGION").path("properties").path("code").path("type").iterator().asSequence().map(JsonNode::asString).toSet())
        assertEquals("null", byKind.getValue("GLOBAL").path("properties").path("code").path("type").asString())
    }

    @Test
    fun `invalid date identifies exact proposal field without exposing its value`() {
        var sentPrompt: OpenAiEconomyProposalPrompt? = null
        val extractor = OpenAiEconomyProposalExtractor(
            object : OpenAiEconomyProposalClient {
                override fun extract(prompt: OpenAiEconomyProposalPrompt): OpenAiEconomyProposalResponse {
                    sentPrompt = prompt
                    return OpenAiEconomyProposalResponse(
                        listOf(OpenAiEconomyProposalDto(
                            statement = "Example", category = "MONETARY_POLICY", eventType = "MONETARY_POLICY_DECIDED",
                            occurredOn = "SECRET_ARTICLE_MARKER", entities = emptyList(), evidenceText = "Evidence",
                        )),
                    )
                }
            },
            OpenAiEconomyProposalSettings("test-key", "test-model", 5, 8000),
        )
        val failure = assertThrows(EconomyExtractionInvalidResponseException::class.java) {
            extractor.extract(UUID.randomUUID(), "https://example.org", "Evidence")
        }
        assertEquals("conversion", failure.phase)
        assertEquals("INVALID_DATE", failure.code)
        assertEquals("proposals[0].occurredOn", failure.fieldPath)
        assertTrue(sentPrompt!!.systemInstruction.contains("YYYY-MM-DD"))
        val instruction = sentPrompt.systemInstruction
        for (rule in listOf("COUNTRY requires a code", "Germany/Deutschland: DE", "United States/Vereinigte Staaten: US", "REGION code is optional", "GLOBAL code must be null", "geography to null", "publisher's location", "complete calendar periods", "COUNT must be an integer", "1000 characters", "2000 characters", "255 characters")) {
            assertTrue(instruction.contains(rule), "missing prompt rule: $rule")
        }
    }

    private fun assertStrictObject(node: JsonNode) {
        val properties = node.path("properties")
        if (!properties.isMissingNode) {
            val names = properties.propertyNames().asSequence().toSet()
            val required = node.path("required").iterator().asSequence().map(JsonNode::asString).toSet()
            assertEquals(names, required)
            assertFalse(node.path("additionalProperties").asBoolean(true))
            properties.properties().forEach { (_, child) -> assertStrictObject(child) }
        }
        node.path("items").takeUnless { it.isMissingNode }?.let(::assertStrictObject)
        node.path("anyOf").forEach(::assertStrictObject)
    }
}
