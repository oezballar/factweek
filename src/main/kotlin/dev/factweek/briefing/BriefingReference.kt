package dev.factweek.briefing

import java.time.LocalDate

data class BriefingReference(
    val date: LocalDate,
    val basis: BriefingReferenceBasis,
)

enum class BriefingReferenceBasis {
    OCCURRED_ON,
    SOURCE_PUBLISHED_AT,
}
