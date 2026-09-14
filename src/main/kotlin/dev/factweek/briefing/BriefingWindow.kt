package dev.factweek.briefing

import java.time.Instant
import java.time.LocalDate

data class BriefingWindow(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
)
