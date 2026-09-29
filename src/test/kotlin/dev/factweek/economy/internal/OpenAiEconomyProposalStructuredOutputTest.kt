package dev.factweek.economy.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

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
