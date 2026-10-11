package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklyMarkdownBriefingTest {
    @Test
    fun `failed department query propagates and cannot produce a partial report`() {
        val economy = mock(WeeklyEconomyBriefing::class.java)
        val technology = mock(WeeklyTechnologyBriefing::class.java)
        val renderer = mock(WeeklyMarkdownRenderer::class.java)
        val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
        val from = LocalDate.parse("2026-09-21")
        val to = LocalDate.parse("2026-09-27")
        `when`(economy.between(from, to, emptySet(), 10)).thenReturn(CurrentEconomyBriefing(
            from, to, clock.instant(), EconomyCategory.entries, 10, 0, emptyList(),
        ))
        val failure = IllegalStateException("technology query failed")
        `when`(technology.between(from, to, emptySet(), 10)).thenThrow(failure)

        val thrown = assertThrows<IllegalStateException> {
            WeeklyMarkdownBriefing(economy, technology, renderer, clock).between(from, to, 10)
        }

        assertSame(failure, thrown)
        verifyNoInteractions(renderer)
    }
}
