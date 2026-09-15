package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import dev.factweek.economy.EconomyFact
import dev.factweek.economy.EconomyFacts
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class WeeklyEconomyBriefing(private val economyFacts: EconomyFacts, private val clock: Clock) {
    fun current(categories: Set<EconomyCategory>, maximum: Int): CurrentEconomyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyEconomyBriefingRequestException()
        val window = BriefingWindow.current(clock)
        val applied = categories.ifEmpty { EconomyCategory.entries.toSet() }.sortedBy { it.name }
        val facts = economyFacts.relevantForBriefingBetween(window.from, window.to).asSequence()
            .mapNotNull { fact -> deriveBriefingReference(fact.occurredOn, fact.sources)?.let { Candidate(fact, it) } }
            .filter { it.fact.category in applied }.sortedWith(compareByDescending<Candidate> { it.reference.date }.thenBy { it.fact.id })
            .take(maximum).map { BriefingEconomyFact.from(it.fact, it.reference) }.toList()
        return CurrentEconomyBriefing(window.from, window.to, window.generatedAt, applied, maximum, facts.size, facts)
    }
    private data class Candidate(val fact: EconomyFact, val reference: BriefingReference)
}
internal class InvalidWeeklyEconomyBriefingRequestException : RuntimeException()
