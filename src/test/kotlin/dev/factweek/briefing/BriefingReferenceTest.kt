package dev.factweek.briefing

import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BriefingReferenceTest {
    @Test
    fun `derives occurred date before source publication dates`() {
        val result = deriveBriefingReference(
            occurredOn = LocalDate.of(2026, 9, 11),
            sources = listOf(source("2026-09-08T00:00:00Z")),
        )

        assertEquals(BriefingReference(LocalDate.of(2026, 9, 11), BriefingReferenceBasis.OCCURRED_ON), result)
    }

    @Test
    fun `uses earliest known source publication date in UTC`() {
        val result = deriveBriefingReference(
            occurredOn = null,
            sources = listOf(
                source(null),
                source("2026-09-10T00:30:00Z"),
                source("2026-09-09T23:59:59Z"),
            ),
        )

        assertEquals(
            BriefingReference(LocalDate.of(2026, 9, 9), BriefingReferenceBasis.SOURCE_PUBLISHED_AT),
            result,
        )
    }

    @Test
    fun `returns null without an event date or source publication date`() {
        assertNull(deriveBriefingReference(null, listOf(source(null))))
    }

    private fun source(publishedAt: String?): SourceReference =
        SourceReference(
            url = "https://example.org/source",
            publisher = "Example",
            sourceType = SourceType.PRIMARY_DOCUMENT,
            publishedAt = publishedAt?.let(Instant::parse),
        )
}
