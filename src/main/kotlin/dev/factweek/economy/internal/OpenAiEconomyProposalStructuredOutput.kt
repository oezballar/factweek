package dev.factweek.economy.internal

import dev.factweek.economy.*
import org.springframework.ai.converter.BeanOutputConverter

internal class OpenAiEconomyProposalStructuredOutput : BeanOutputConverter<OpenAiEconomyProposalResponse>(
    OpenAiEconomyProposalResponse::class.java,
) {
    override fun generateSchema(): String = strictSchema

    companion object {
        val strictSchema = """
            {
              "type": "object", "additionalProperties": false,
              "properties": {
                "proposals": {"type": "array", "maxItems": 5, "items": {
                  "type": "object", "additionalProperties": false,
                  "properties": {
                    "statement": {"type": "string"},
                    "category": {"type": "string", "enum": [${EconomyCategory.entries.jsonValues()}]},
                    "eventType": {"type": "string", "enum": [${EconomyEventType.entries.jsonValues()}]},
                    "occurredOn": {"anyOf": [{"type": "string", "format": "date"}, {"type": "null"}]},
                    "referencePeriod": {"type": ["object", "null"], "additionalProperties": false,
                      "properties": {
                        "from": {"type": "string", "format": "date"}, "to": {"type": "string", "format": "date"},
                        "granularity": {"type": "string", "enum": [${EconomyReferencePeriodGranularity.entries.jsonValues()}]}
                      }, "required": ["from", "to", "granularity"]},
                    "geography": {"anyOf": [
                      {"type": "null"},
                      {"type": "object", "additionalProperties": false,
                        "properties": {"kind": {"type": "string", "enum": ["COUNTRY"]}, "name": {"type": "string"}, "code": {"type": "string"}},
                        "required": ["kind", "name", "code"]},
                      {"type": "object", "additionalProperties": false,
                        "properties": {"kind": {"type": "string", "enum": ["REGION"]}, "name": {"type": "string"}, "code": {"type": ["string", "null"]}},
                        "required": ["kind", "name", "code"]},
                      {"type": "object", "additionalProperties": false,
                        "properties": {"kind": {"type": "string", "enum": ["GLOBAL"]}, "name": {"type": "string"}, "code": {"type": "null"}},
                        "required": ["kind", "name", "code"]}
                    ]},
                    "measurement": {"type": ["object", "null"], "additionalProperties": false,
                      "properties": {
                        "value": {"type": "string"},
                        "unit": {"type": "string", "enum": [${EconomyMeasurementUnit.entries.jsonValues()}]},
                        "releaseStatus": {"type": ["string", "null"], "enum": [${EconomyReleaseStatus.entries.jsonValues()}, null]},
                        "seasonalAdjustment": {"type": ["string", "null"], "enum": [${EconomySeasonalAdjustment.entries.jsonValues()}, null]},
                        "valueBasis": {"type": ["string", "null"], "enum": [${EconomyValueBasis.entries.jsonValues()}, null]}
                      }, "required": ["value", "unit", "releaseStatus", "seasonalAdjustment", "valueBasis"]},
                    "entities": {"type": "array", "items": {
                      "type": "object", "additionalProperties": false,
                      "properties": {
                        "name": {"type": "string"},
                        "type": {"type": "string", "enum": [${EconomyEntityType.entries.jsonValues()}]}
                      }, "required": ["name", "type"]}},
                    "evidenceText": {"type": "string"}
                  },
                  "required": ["statement", "category", "eventType", "occurredOn", "referencePeriod", "geography", "measurement", "entities", "evidenceText"]
                }}
              }, "required": ["proposals"]
            }
        """.trimIndent()

        private fun <T : Enum<T>> Iterable<T>.jsonValues(): String = joinToString(", ") { "\"${it.name}\"" }
    }
}
