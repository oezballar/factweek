package dev.factweek.briefing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class BriefingWindow(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
) {
    companion object {
        fun current(clock: Clock): BriefingWindow {
            val generatedAt = clock.instant()
            val to = generatedAt.atZone(clock.zone).toLocalDate()

            return BriefingWindow(
                from = to.minusDays(6),
                to = to,
                generatedAt = generatedAt,
            )
        }
    }
}
