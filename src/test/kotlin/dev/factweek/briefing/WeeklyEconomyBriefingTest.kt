package dev.factweek.briefing

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class WeeklyEconomyBriefingTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC)
    @Test fun `derives occurred and source publication references deterministically`() {
        val occurred = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 11), listOf(SourceReference("https://x", "X", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-08T00:00:00Z"))))
        val source = fact("00000000-0000-0000-0000-000000000002", null, listOf(SourceReference("https://later", "L", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-12T00:00:00Z")), SourceReference("https://early", "E", SourceType.NEWS_REPORT, Instant.parse("2026-09-09T23:59:59Z"))))
        val undated = fact("00000000-0000-0000-0000-000000000003", null, emptyList())
        val result = WeeklyEconomyBriefing(Fake(listOf(undated, source, occurred)), clock).current(emptySet(), 10)
        assertEquals(listOf(occurred.id, source.id), result.facts.map { it.id })
        assertEquals(BriefingReferenceBasis.OCCURRED_ON, result.facts[0].referenceDateBasis)
        assertEquals(LocalDate.of(2026, 9, 9), result.facts[1].referenceDate)
        assertEquals(BriefingReferenceBasis.SOURCE_PUBLISHED_AT, result.facts[1].referenceDateBasis)
    }
    @Test fun `filters categories before limit`() {
        val selected = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 10), emptyList(), EconomyCategory.MONETARY_POLICY)
        val excluded = fact("00000000-0000-0000-0000-000000000002", LocalDate.of(2026, 9, 13), emptyList(), EconomyCategory.PRICES_AND_INFLATION)
        val result = WeeklyEconomyBriefing(Fake(listOf(excluded, selected)), clock).current(setOf(EconomyCategory.MONETARY_POLICY), 1)
        assertEquals(listOf(selected.id), result.facts.map { it.id }); assertEquals(1, result.factCount)
    }
    private fun fact(id: String, occurred: LocalDate?, sources: List<SourceReference>, category: EconomyCategory = EconomyCategory.PRICES_AND_INFLATION) = EconomyFact(UUID.fromString(id), "Fact", category, EconomyEventType.MONETARY_POLICY_DECIDED, EconomyEvidenceLevel.PRIMARY_CONFIRMED, occurred, null, null, null, emptyList(), sources)
    private class Fake(private val facts: List<EconomyFact>) : EconomyFacts { override fun publish(command: PublishEconomyFact) = error("unused"); override fun relevantForBriefingBetween(from: LocalDate, to: LocalDate) = facts }
}
