package dev.factweek.processing.internal

import dev.factweek.processing.SectionId
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/** Strict local validation; provider structured output is a complementary safeguard. */
internal class ArticleSectionClassificationResponseParser(
    private val objectMapper: JsonMapper,
) {
    fun parse(response: String?): List<SectionId> {
        if (response.isNullOrBlank()) {
            throw ArticleSectionClassificationException("The model returned no classification")
        }
        val root = try {
            objectMapper.createParser(response).use { parser ->
                val parsed = objectMapper.readTree(parser)
                if (parser.nextToken() != null) {
                    throw ArticleSectionClassificationException("The model returned trailing classification content")
                }
                parsed
            }
        } catch (exception: ArticleSectionClassificationException) {
            throw exception
        } catch (exception: JacksonException) {
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
        return sections.values().map { section ->
            if (!section.isString) {
                throw ArticleSectionClassificationException("The model returned an invalid section key")
            }
            try {
                SectionId(section.asString())
            } catch (exception: IllegalArgumentException) {
                throw ArticleSectionClassificationException("The model returned an invalid section key", exception)
            }
        }.toSet().sortedBy { it.value }
    }
}
