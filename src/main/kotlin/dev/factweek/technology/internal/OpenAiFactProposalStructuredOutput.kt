package dev.factweek.technology.internal

import dev.factweek.technology.EntityType
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import org.springframework.ai.converter.BeanOutputConverter

/**
 * OpenAI Strict Structured Outputs require every object property to be required and
 * reject generated Kotlin schemas where nullable properties are merely optional.
 */
internal class OpenAiFactProposalStructuredOutput : BeanOutputConverter<OpenAiFactProposalResponse>(
    OpenAiFactProposalResponse::class.java,
) {
    override fun generateSchema(): String = strictSchema

    companion object {
        val strictSchema = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "proposals": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "statement": { "type": "string" },
                      "category": { "type": "string", "enum": [${TechnologyCategory.entries.jsonValues()}] },
                      "entities": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "additionalProperties": false,
                          "properties": {
                            "name": { "type": "string" },
                            "type": { "type": "string", "enum": [${EntityType.entries.jsonValues()}] }
                          },
                          "required": ["name", "type"]
                        }
                      },
                      "occurredOn": { "type": ["string", "null"] },
                      "evidenceText": { "type": "string" },
                      "evidenceLevel": { "type": "string", "enum": [${EvidenceLevel.entries.jsonValues()}] }
                    },
                    "required": ["statement", "category", "entities", "occurredOn", "evidenceText", "evidenceLevel"]
                  }
                }
              },
              "required": ["proposals"]
            }
        """.trimIndent()

        private fun <T : Enum<T>> Iterable<T>.jsonValues(): String = joinToString(", ") { "\"${it.name}\"" }
    }
}
