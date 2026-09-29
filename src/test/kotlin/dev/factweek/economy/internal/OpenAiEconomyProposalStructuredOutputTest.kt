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
        for (name in listOf("occurredOn", "referencePeriod", "geography", "measurement")) {
            val types = proposal.path("properties").path(name).path("type")
                .iterator().asSequence().map(JsonNode::asString).toSet()
            assertTrue("null" in types, "$name must be nullable")
        }
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
    }
}
