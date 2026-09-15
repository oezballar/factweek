package dev.factweek.processing.internal

import dev.factweek.processing.SectionId
import java.util.UUID

internal interface ArticleSectionClassifier {
    fun classify(request: ArticleSectionClassificationRequest): List<SectionId>
}

internal data class ArticleSectionClassificationRequest(
    val documentId: UUID,
    val sourceUrl: String,
    val textContent: String,
    val supportedSections: List<ConfiguredSection>,
)

internal data class ConfiguredSection(
    val id: SectionId,
    val description: String,
)

internal class ArticleSectionClassificationException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

internal class ArticleSectionClassifierUnavailableException : RuntimeException()
