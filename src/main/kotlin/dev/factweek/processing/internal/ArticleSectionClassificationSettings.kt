package dev.factweek.processing.internal

import dev.factweek.processing.SectionId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
internal class ArticleSectionClassificationSettings(
    @Value("\${factweek.article-classification.openai.model:gpt-5-mini}") val model: String,
    @Value("\${factweek.article-classification.version:article-section-classification-v1}") val version: String,
    @Value("\${factweek.article-classification.sections.technology.description:Concrete technological developments, research, and technical applications.}") technologyDescription: String,
    @Value("\${factweek.article-classification.sections.economy.description:Concrete economic events and published economic indicators.}") economyDescription: String,
) {
    val sections = listOf(
        ConfiguredSection(SectionId("technology"), technologyDescription.trim()),
        ConfiguredSection(SectionId("economy"), economyDescription.trim()),
    )

    init {
        require(model.isNotBlank()) { "Article classification model must not be blank" }
        require(version.isNotBlank()) { "Article classification version must not be blank" }
        require(sections.map { it.id }.toSet().size == sections.size) { "Article classification section ids must be unique" }
        require(sections.all { it.description.isNotBlank() }) { "Article classification section descriptions must not be blank" }
    }
}
