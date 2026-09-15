package dev.factweek.provenance

import java.time.Instant

data class SourceReference(
    val url: String,
    val publisher: String,
    val sourceType: SourceType,
    /** Publication timestamp of this concrete source, not the asserted event date. */
    val publishedAt: Instant? = null,
)

enum class SourceType { NEWS_REPORT, PRIMARY_DOCUMENT, PAPER, DATASET, REPOSITORY, REGULATOR }
