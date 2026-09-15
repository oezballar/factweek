package dev.factweek.briefing

import dev.factweek.provenance.SourceReference
import java.time.LocalDate
import java.time.ZoneOffset

data class BriefingReference(
    val date: LocalDate,
    val basis: BriefingReferenceBasis,
)

enum class BriefingReferenceBasis {
    OCCURRED_ON,
    SOURCE_PUBLISHED_AT,
}

internal fun deriveBriefingReference(occurredOn: LocalDate?, sources: List<SourceReference>): BriefingReference? =
    occurredOn?.let { BriefingReference(it, BriefingReferenceBasis.OCCURRED_ON) }
        ?: sources.mapNotNull { it.publishedAt }.minOrNull()?.let { BriefingReference(it.atZone(ZoneOffset.UTC).toLocalDate(), BriefingReferenceBasis.SOURCE_PUBLISHED_AT) }
