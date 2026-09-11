package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
import java.time.Instant
import java.time.LocalDate

/** A factual, non-narrative view of reviewed technology facts for the current rolling window. */
data class CurrentTechnologyBriefing(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
    val appliedCategories: List<TechnologyCategory>,
    val requestedMaximum: Int,
    val factCount: Int,
    val facts: List<TechnologyFact>,
)
