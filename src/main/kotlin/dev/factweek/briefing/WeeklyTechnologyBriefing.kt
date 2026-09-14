package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
import dev.factweek.technology.TechnologyFacts
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneOffset

@Service
class WeeklyTechnologyBriefing(
    private val technologyFacts: TechnologyFacts,
    private val clock: Clock,
) {
    fun current(categories: Set<TechnologyCategory>, maximum: Int): CurrentTechnologyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyTechnologyBriefingRequestException()
        val window = currentWindow()
        val appliedCategories = categories.ifEmpty { TechnologyCategory.entries.toSet() }.sortedBy { it.name }
        val facts = technologyFacts.relevantForBriefingBetween(window.from, window.to)
            .asSequence()
            .mapNotNull { fact -> briefingFact(fact) }
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

    private fun currentWindow(): BriefingWindow {
        val generatedAt = clock.instant()
        val to = generatedAt.atZone(clock.zone).toLocalDate()
        return BriefingWindow(to.minusDays(6), to, generatedAt)
    }

    private fun briefingFact(fact: TechnologyFact): BriefingFactCandidate? {
        val reference = fact.occurredOn?.let { BriefingReference(it, BriefingReferenceBasis.OCCURRED_ON) }
            ?: fact.sources.mapNotNull { it.publishedAt }.minOrNull()?.let { publishedAt ->
                BriefingReference(publishedAt.atZone(ZoneOffset.UTC).toLocalDate(), BriefingReferenceBasis.SOURCE_PUBLISHED_AT)
            }
            ?: return null
        return BriefingFactCandidate(fact, reference)
    }

    private data class BriefingFactCandidate(
        val fact: TechnologyFact,
        val reference: BriefingReference,
    )
}
