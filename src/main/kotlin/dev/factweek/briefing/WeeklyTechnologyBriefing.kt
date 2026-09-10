package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
import dev.factweek.technology.TechnologyFacts
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class WeeklyTechnologyBriefing(
    private val technologyFacts: TechnologyFacts,
    private val clock: Clock,
) {
    fun current(categories: Set<TechnologyCategory>): List<TechnologyFact> {
        val today = LocalDate.now(clock)
        return technologyFacts.publishedBetween(today.minusDays(6), today)
            .filter { categories.isEmpty() || it.category in categories }
            .take(10)
    }
}
