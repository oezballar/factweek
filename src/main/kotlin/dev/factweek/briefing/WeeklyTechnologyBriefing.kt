package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
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
        val window = BriefingWindow.current(clock)
        val appliedCategories = categories.ifEmpty { TechnologyCategory.entries.toSet() }.sortedBy { it.name }
        val facts = technologyFacts.relevantForBriefingBetween(window.from, window.to)
            .asSequence()
            .mapNotNull { fact -> deriveBriefingReference(fact.occurredOn, fact.sources)?.let { BriefingFactCandidate(fact, it) } }
            .filter { it.fact.category in appliedCategories }
            .sortedWith(compareByDescending<BriefingFactCandidate> { it.reference.date }.thenBy { it.fact.id })
            .take(maximum)
            .map { BriefingTechnologyFact.from(it.fact, it.reference.date, it.reference.basis) }
            .toList()
        return CurrentTechnologyBriefing(
            from = window.from,
            to = window.to,
            generatedAt = window.generatedAt,
            appliedCategories = appliedCategories,
            requestedMaximum = maximum,
            factCount = facts.size,
            facts = facts,
        )
    }

    private data class BriefingFactCandidate(
        val fact: TechnologyFact,
        val reference: BriefingReference,
    )
}
