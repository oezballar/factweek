package dev.factweek.processing.internal

import dev.factweek.processing.SectionId
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "factweek.article-classification")
internal class ArticleSectionClassificationSettings(
    var version: String = "",
    var sections: List<SectionProperties> = emptyList(),
    var openai: OpenAiProperties = OpenAiProperties(),
) {
    lateinit var configuredSections: List<ConfiguredSection>
        private set

    @PostConstruct
    fun validate() {
        require(version.isNotBlank() && version.length <= 128) { "Article classification version must be non-empty and at most 128 characters" }
        require(sections.isNotEmpty()) { "Article classification sections must not be empty" }
        configuredSections = sections.map {
            require(it.description.isNotBlank()) { "Article classification section descriptions must not be blank" }
            val sectionId = try {
                SectionId(it.id)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Article classification section id is invalid", exception)
            }
            ConfiguredSection(sectionId, it.description.trim())
        }
        require(configuredSections.map { it.id }.toSet().size == configuredSections.size) { "Article classification section ids must be unique" }
        require(openai.model.isNotBlank()) { "Article classification model must not be blank" }
    }

    class SectionProperties {
        var id: String = ""
        var description: String = ""
    }

    class OpenAiProperties {
        var enabled: Boolean = false
        var model: String = "gpt-5-mini"
    }
}
