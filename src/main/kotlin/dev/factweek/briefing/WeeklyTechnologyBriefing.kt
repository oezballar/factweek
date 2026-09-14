package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFacts
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class WeeklyTechnologyBriefing(
    private val technologyFacts: TechnologyFacts,
    private val clock: Clock,
) {
    fun current(categories: Set<TechnologyCategory>, maximum: Int): CurrentTechnologyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyTechnologyBriefingRequestException()
        val generatedAt = clock.instant()
        val today = generatedAt.atZone(clock.zone).toLocalDate()
        val from = today.minusDays(6)
        val appliedCategories = categories.ifEmpty { TechnologyCategory.entries.toSet() }.sortedBy { it.name }
        val facts = technologyFacts.relevantForBriefingBetween(from, today)
            .asSequence()
            .filter { it.fact.category in appliedCategories }
            .sortedWith(compareByDescending<dev.factweek.technology.BriefingRelevantTechnologyFact> { it.reference.date }.thenBy { it.fact.id })
            .take(maximum)
            .map { BriefingTechnologyFact.from(it.fact, it.reference.date, it.reference.basis) }
            .toList()
        return CurrentTechnologyBriefing(
            from = from,
            to = today,
            generatedAt = generatedAt,
            appliedCategories = appliedCategories,
            requestedMaximum = maximum,
            factCount = facts.size,
            facts = facts,
        )
    }
}
