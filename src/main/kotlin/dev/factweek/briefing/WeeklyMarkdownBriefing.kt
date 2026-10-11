package dev.factweek.briefing

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class WeeklyMarkdownBriefing(
    private val economy: WeeklyEconomyBriefing,
    private val technology: WeeklyTechnologyBriefing,
    private val renderer: WeeklyMarkdownRenderer,
    private val clock: Clock,
) {
    fun between(from: LocalDate, to: LocalDate, maximumPerSection: Int): String {
        if (maximumPerSection !in 1..50) throw InvalidWeeklyMarkdownBriefingRequestException()
        val generatedAt = clock.instant()
        val economyBriefing = economy.between(from, to, emptySet(), maximumPerSection)
        val technologyBriefing = technology.between(from, to, emptySet(), maximumPerSection)
        return renderer.render(from, to, generatedAt, maximumPerSection, economyBriefing.facts, technologyBriefing.facts)
    }
}

internal class InvalidWeeklyMarkdownBriefingRequestException : RuntimeException()
