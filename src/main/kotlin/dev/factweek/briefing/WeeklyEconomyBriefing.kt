package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import dev.factweek.economy.EconomyFact
import dev.factweek.economy.EconomyFacts
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class WeeklyEconomyBriefing(
    private val economyFacts: EconomyFacts,
    private val clock: Clock,
) {
    fun current(categories: Set<EconomyCategory>, maximum: Int): CurrentEconomyBriefing {
        return select(BriefingWindow.current(clock), categories, maximum)
    }

    fun between(from: java.time.LocalDate, to: java.time.LocalDate, categories: Set<EconomyCategory>, maximum: Int): CurrentEconomyBriefing {
        return select(BriefingWindow.explicit(from, to, clock), categories, maximum)
    }

    private fun select(window: BriefingWindow, categories: Set<EconomyCategory>, maximum: Int): CurrentEconomyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyEconomyBriefingRequestException()
        val appliedCategories = categories.ifEmpty { EconomyCategory.entries.toSet() }.sortedBy { it.name }
        val facts = economyFacts.relevantForBriefingBetween(window.from, window.to)
            .asSequence()
            .mapNotNull { fact ->
                deriveBriefingReference(fact.occurredOn, fact.sources)?.let { reference ->
                    Candidate(fact, reference)
                }
            }
            .filter { it.fact.category in appliedCategories }
            .sortedWith(compareByDescending<Candidate> { it.reference.date }.thenBy { it.fact.id })
            .take(maximum)
            .map { BriefingEconomyFact.from(it.fact, it.reference) }
            .toList()

        return CurrentEconomyBriefing(
            from = window.from,
            to = window.to,
            generatedAt = window.generatedAt,
            appliedCategories = appliedCategories,
            requestedMaximum = maximum,
            factCount = facts.size,
            facts = facts,
        )
    }

    private data class Candidate(
        val fact: EconomyFact,
        val reference: BriefingReference,
    )
}
internal class InvalidWeeklyEconomyBriefingRequestException : RuntimeException()
