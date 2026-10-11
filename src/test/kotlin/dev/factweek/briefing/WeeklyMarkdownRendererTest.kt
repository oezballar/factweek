package dev.factweek.briefing

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import dev.factweek.technology.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WeeklyMarkdownRendererTest {
    private val renderer = WeeklyMarkdownRenderer()
    private val from = LocalDate.parse("2026-09-21")
    private val to = LocalDate.parse("2026-09-27")
    private val now = Instant.parse("2026-10-11T12:34:56Z")

    @Test
    fun `renders both sections precision sources and evidence without changing statements`() {
        val economy = economyFact().copy(
            statement = "The deficit was 3.00 percent of GDP.",
            measurement = EconomyMeasurement(BigDecimal("3.00"), EconomyMeasurementUnit.PERCENT,
                EconomyReleaseStatus.REVISED, EconomySeasonalAdjustment.ADJUSTED, EconomyValueBasis.NOMINAL),
            referencePeriod = EconomyReferencePeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"), EconomyReferencePeriodGranularity.QUARTER),
            geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "United States", "US"),
            entities = listOf(EconomyEntityReference("Current account", EconomyEntityType.INDICATOR)),
        )
        val technology = technologyFact()
        val result = renderer.render(from, to, now, 10, listOf(economy), listOf(technology))

        assertTrue(result.startsWith("# Wochenrückblick: Wirtschaft und Technologie\n"))
        assertTrue(result.contains("2026-10-11T12:34:56Z (UTC)"))
        assertTrue(result.contains("Wirtschaft 1, Technologie 1; Limit je Ressort: 10"))
        assertTrue(result.contains("The deficit was 3\\.00 percent of GDP\\."))
        assertTrue(result.contains("3.00 %"))
        assertTrue(result.contains("01.04.2026 bis 30.06.2026 (Quartal)"))
        assertTrue(result.contains("United States (Land, US)"))
        assertTrue(result.contains("[BEA](<https://example.org/bea>)"))
        assertTrue(result.contains("[Google Research](<https://example.org/canvas>)"))
        assertTrue(result.contains("veröffentlicht am 24.09.2026"))
        assertTrue(result.contains("Anhand einer Primärquelle geprüft"))
        assertTrue(result.contains("Forschungsergebnis veröffentlicht"))
        assertTrue(result.contains("Laborergebnis"))
        assertTrue(result.contains("| Wirtschaft | ${economy.id} | 2026-09-24 | Quellenpublikation (UTC) |"))
        assertTrue(result.contains("| Technologie | ${technology.id} | 2026-09-24 | Quellenpublikation (UTC) |"))
        assertEquals(result, renderer.render(from, to, now, 10, listOf(economy), listOf(technology)))
    }

    @Test
    fun `omits missing optional fields and renders empty sections`() {
        val bare = economyFact().copy(referencePeriod = null, geography = null, measurement = null, entities = emptyList(), sources = emptyList())
        val oneEmpty = renderer.render(from, to, now, 5, listOf(bare), emptyList())
        assertEquals(1, Regex("Keine veröffentlichten Facts für dieses Zeitfenster gefunden\\.").findAll(oneEmpty).count())
        assertFalse(oneEmpty.contains("**Messwert:**"))
        assertFalse(oneEmpty.contains("**Bezugszeitraum:**"))
        assertFalse(oneEmpty.contains("null"))
        val bothEmpty = renderer.render(from, to, now, 5, emptyList(), emptyList())
        assertEquals(2, Regex("Keine veröffentlichten Facts für dieses Zeitfenster gefunden\\.").findAll(bothEmpty).count())
        assertTrue(bothEmpty.contains("Wirtschaft 0, Technologie 0"))
    }

    @Test
    fun `escapes stored markdown and HTML while retaining Unicode and line breaks`() {
        val malicious = economyFact().copy(
            statement = "# Äpfel *stark* ~~alt~~ <script>alert(1)</script>\n[click](javascript:evil) & mehr",
            geography = EconomyGeography(EconomyGeographyKind.REGION, "<b>Nord</b> *Ost*"),
            sources = listOf(
                SourceReference("javascript:alert(1)", "<img src=x> [Fake]", SourceType.NEWS_REPORT, now),
                SourceReference("https://example.org/a(b)", "A *B*", SourceType.PRIMARY_DOCUMENT, now),
                SourceReference("https://example.org/has space", "Bad URL", SourceType.NEWS_REPORT, null),
            ),
        )
        val result = renderer.render(from, to, now, 10, listOf(malicious), emptyList())
        assertTrue(result.contains("\\# Äpfel \\*stark\\* \\~\\~alt\\~\\~ &lt;script&gt;alert\\(1\\)&lt;/script&gt;  \n\\[click\\]\\(javascript:evil\\) &amp; mehr"))
        assertTrue(result.contains("&lt;b&gt;Nord&lt;/b&gt; \\*Ost\\*"))
        assertTrue(result.contains("&lt;img src\\=x&gt; \\[Fake\\]"))
        assertTrue(result.contains("[A \\*B\\*](<https://example.org/a(b)>)"))
        assertFalse(result.contains("](javascript:alert(1))"))
        assertFalse(result.contains("](https://example.org/has space)"))
        assertTrue(result.contains("Original-URL, nicht verlinkt:"))
        assertFalse(result.contains("<script>"))
    }

    private fun economyFact() = BriefingEconomyFact(
        UUID.fromString("00000000-0000-0000-0000-000000000001"), "Economy statement.",
        EconomyCategory.MACROECONOMIC_ACTIVITY, EconomyEventType.INDICATOR_VALUE_REPORTED,
        EconomyEvidenceLevel.PRIMARY_CONFIRMED, null, null, null, null, emptyList(),
        listOf(SourceReference("https://example.org/bea", "BEA", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-24T12:00:00Z"))),
        LocalDate.parse("2026-09-24"), BriefingReferenceBasis.SOURCE_PUBLISHED_AT,
    )

    private fun technologyFact() = BriefingTechnologyFact(
        UUID.fromString("00000000-0000-0000-0000-000000000002"), "CANVAS is a framework.",
        TechnologyCategory.AI_AND_SOFTWARE, TechnologyEventType.RESEARCH_RESULT_PUBLISHED,
        TechnologyReadiness.LAB_RESULT, EvidenceLevel.PRIMARY_CONFIRMED, null,
        listOf(EntityReference("CANVAS", EntityType.TECHNOLOGY)),
        listOf(SourceReference("https://example.org/canvas", "Google Research", SourceType.PRIMARY_DOCUMENT, Instant.parse("2026-09-24T19:40:00Z"))),
        LocalDate.parse("2026-09-24"), BriefingReferenceBasis.SOURCE_PUBLISHED_AT,
    )
}
