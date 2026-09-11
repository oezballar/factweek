package dev.factweek.technology.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class OpenAiFactProposalStructuredOutputTest {
    private val schema = JsonMapper.builder().build()
        .readTree(OpenAiFactProposalStructuredOutput().jsonSchema)

    @Test
    fun `schema sent through the structured-output converter is recursively OpenAI strict compatible`() {
        assertStrictObjectSchema(schema, "$")
    }

    @Test
    fun `optional occurrence date remains required and nullable`() {
        val occurrenceDate = schema.path("properties").path("proposals").path("items")
            .path("properties").path("occurredOn")

        assertTrue(occurrenceDate.path("type").iterator().asSequence().any { it.asString() == "string" })
        assertTrue(occurrenceDate.path("type").iterator().asSequence().any { it.asString() == "null" })
    }

    private fun assertStrictObjectSchema(node: JsonNode, path: String) {
        val properties = node.path("properties")
        if (!properties.isMissingNode) {
            val propertyNames = properties.propertyNames().asSequence().toSet()
            val required = node.path("required")
            assertTrue(required.isArray, "$path must declare required")
            val requiredNames = required.iterator().asSequence().map { requiredName ->
                assertTrue(requiredName.isString(), "$path required entries must be strings: $requiredName")
                requiredName.asString()
            }.toSet()
            assertEquals(propertyNames, requiredNames, "$path required must equal properties")
            assertFalse(node.path("additionalProperties").asBoolean(true), "$path must forbid additional properties")
            properties.properties().forEach { (name, child) -> assertStrictObjectSchema(child, "$path.properties.$name") }
        }
        node.path("items").takeUnless { it.isMissingNode }?.let { assertStrictObjectSchema(it, "$path.items") }
    }
}
