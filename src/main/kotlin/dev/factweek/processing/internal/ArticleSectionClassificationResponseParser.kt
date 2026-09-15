package dev.factweek.processing.internal

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.factweek.processing.SectionId

/** Strict local validation; provider structured output is a complementary safeguard. */
internal class ArticleSectionClassificationResponseParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(response: String?): List<SectionId> {
        if (response.isNullOrBlank()) {
            throw ArticleSectionClassificationException("The model returned no classification")
        }
        val root = try {
            objectMapper.factory.createParser(response).use { parser ->
                val parsed = objectMapper.readTree<JsonNode>(parser)
                if (parser.nextToken() != null) {
                    throw ArticleSectionClassificationException("The model returned trailing classification content")
                }
                parsed
            }
        } catch (exception: ArticleSectionClassificationException) {
            throw exception
        } catch (exception: JsonProcessingException) {
            throw ArticleSectionClassificationException("The model returned an invalid section classification", exception)
        } catch (exception: IllegalArgumentException) {
            throw ArticleSectionClassificationException("The model returned an invalid section classification", exception)
        }

        if (!root.isObject || root.size() != 1 || !root.has("sections")) {
            throw ArticleSectionClassificationException("The model returned an invalid section classification")
        }
        val sections = root.get("sections")
        if (!sections.isArray) {
            throw ArticleSectionClassificationException("The model omitted sections")
        }
        return sections.map { section ->
            if (!section.isTextual) {
                throw ArticleSectionClassificationException("The model returned an invalid section key")
            }
            try {
                SectionId(section.textValue())
            } catch (exception: IllegalArgumentException) {
                throw ArticleSectionClassificationException("The model returned an invalid section key", exception)
            }
        }.toSet().sortedBy { it.value }
    }
}
