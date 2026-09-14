package dev.factweek.briefing

import dev.factweek.technology.EntityReference
import dev.factweek.technology.EntityType
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.BriefingReference
import dev.factweek.technology.BriefingReferenceBasis
import dev.factweek.technology.BriefingRelevantTechnologyFact
import dev.factweek.technology.PublishTechnologyFact
import dev.factweek.technology.SourceReference
import dev.factweek.technology.SourceType
import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyFact
import dev.factweek.technology.TechnologyFacts
import dev.factweek.technology.TechnologyReadiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class WeeklyTechnologyBriefingTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `current on Sunday covers Monday through Sunday inclusively`() {
        val lowerBoundary = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 7))
        val upperBoundary = fact("00000000-0000-0000-0000-000000000002", LocalDate.of(2026, 9, 13))
        val technologyFacts = RecordingTechnologyFacts(listOf(lowerBoundary, upperBoundary))

        val result = WeeklyTechnologyBriefing(technologyFacts, clock).current(emptySet(), 10)

        assertEquals(LocalDate.of(2026, 9, 7), technologyFacts.from)
        assertEquals(LocalDate.of(2026, 9, 13), technologyFacts.to)
        assertEquals(LocalDate.of(2026, 9, 7), result.from)
        assertEquals(LocalDate.of(2026, 9, 13), result.to)
        assertEquals(Instant.parse("2026-09-13T12:00:00Z"), result.generatedAt)
        assertEquals(listOf(upperBoundary, lowerBoundary), result.facts.map { it.toTechnologyFact() })
        assertEquals(BriefingReferenceBasis.OCCURRED_ON, result.facts.first().referenceDateBasis)
        assertEquals(2, result.factCount)
        assertEquals(TechnologyCategory.entries.sortedBy { it.name }, result.appliedCategories)
    }

    @Test
    fun `categories are filtered before deterministic sorting and maximum`() {
        val excluded = fact("00000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 13), TechnologyCategory.ROBOTICS_AND_AUTOMATION)
        val second = fact("00000000-0000-0000-0000-000000000003", LocalDate.of(2026, 9, 12), TechnologyCategory.ENERGY_AND_CLIMATE)
        val first = fact("00000000-0000-0000-0000-000000000002", LocalDate.of(2026, 9, 12), TechnologyCategory.AI_AND_SOFTWARE)
        val laterTie = fact("00000000-0000-0000-0000-000000000004", LocalDate.of(2026, 9, 12), TechnologyCategory.ENERGY_AND_CLIMATE)
        val briefing = WeeklyTechnologyBriefing(RecordingTechnologyFacts(listOf(excluded, laterTie, second, first)), clock)

        val result = briefing.current(
            setOf(TechnologyCategory.ENERGY_AND_CLIMATE, TechnologyCategory.AI_AND_SOFTWARE),
            2,
        )

        assertEquals(
            listOf(TechnologyCategory.AI_AND_SOFTWARE, TechnologyCategory.ENERGY_AND_CLIMATE),
            result.appliedCategories,
        )
        assertEquals(listOf(first, second), result.facts.map { it.toTechnologyFact() })
        assertEquals(2, result.factCount)
        assertEquals(2, result.requestedMaximum)
    }

    @Test
    fun `empty result keeps complete metadata`() {
        val result = WeeklyTechnologyBriefing(RecordingTechnologyFacts(emptyList()), clock)
            .current(setOf(TechnologyCategory.MEDICINE_AND_BIOTECH), 4)

        assertEquals(emptyList<BriefingTechnologyFact>(), result.facts)
        assertEquals(0, result.factCount)
        assertEquals(4, result.requestedMaximum)
        assertEquals(listOf(TechnologyCategory.MEDICINE_AND_BIOTECH), result.appliedCategories)
    }

    @Test
    fun `facts retain their entities and sources`() {
        val fact = fact("00000000-0000-0000-0000-000000000005", LocalDate.of(2026, 9, 10))

        val result = WeeklyTechnologyBriefing(RecordingTechnologyFacts(listOf(fact)), clock).current(emptySet(), 10)

        assertEquals(fact.entities, result.facts.single().entities)
        assertEquals(fact.sources, result.facts.single().sources)
    }

    @Test
    fun `undated facts are excluded from date-based briefings`() {
        val dated = fact("00000000-0000-0000-0000-000000000006", LocalDate.of(2026, 9, 10))
        val undated = fact("00000000-0000-0000-0000-000000000007", null)

        val result = WeeklyTechnologyBriefing(RecordingTechnologyFacts(listOf(undated, dated)), clock).current(emptySet(), 10)

        assertEquals(listOf(dated), result.facts.map { it.toTechnologyFact() })
    }

    @Test
    fun `undated facts use the earliest source publication as their briefing reference`() {
        val sourcePublishedFact = fact(
            "00000000-0000-0000-0000-000000000008",
            null,
            sources = listOf(
                SourceReference("https://example.org/later", "Example", SourceType.PAPER, Instant.parse("2026-09-11T00:00:00Z")),
                SourceReference("https://example.org/earlier", "Example", SourceType.PAPER, Instant.parse("2026-09-08T23:59:59Z")),
            ),
        )
        val fullyUndated = fact("00000000-0000-0000-0000-000000000009", null)

        val result = WeeklyTechnologyBriefing(RecordingTechnologyFacts(listOf(fullyUndated, sourcePublishedFact)), clock).current(emptySet(), 10)

        assertEquals(listOf(sourcePublishedFact), result.facts.map { it.toTechnologyFact() })
        assertEquals(LocalDate.of(2026, 9, 8), result.facts.single().referenceDate)
        assertEquals(BriefingReferenceBasis.SOURCE_PUBLISHED_AT, result.facts.single().referenceDateBasis)
    }

    private fun fact(
        id: String,
        occurredOn: LocalDate?,
        category: TechnologyCategory = TechnologyCategory.AI_AND_SOFTWARE,
        sources: List<SourceReference> = listOf(SourceReference("https://example.org/source", "Example", SourceType.NEWS_REPORT)),
    ) =
        TechnologyFact(
            id = UUID.fromString(id),
            statement = "A concrete technology fact.",
            category = category,
            eventType = TechnologyEventType.TECHNOLOGY_DEPLOYED,
            readiness = TechnologyReadiness.PRODUCTION_USE,
            evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
            occurredOn = occurredOn,
            entities = listOf(EntityReference("Example technology", EntityType.TECHNOLOGY)),
            sources = sources,
        )

    private class RecordingTechnologyFacts(
        private val facts: List<TechnologyFact>,
    ) : TechnologyFacts {
        lateinit var from: LocalDate
        lateinit var to: LocalDate

        override fun publish(command: PublishTechnologyFact): TechnologyFact = error("Not used by a briefing")

        override fun occurredBetween(from: LocalDate, to: LocalDate): List<TechnologyFact> {
            this.from = from
            this.to = to
            return facts
        }

        override fun relevantForBriefingBetween(from: LocalDate, to: LocalDate): List<BriefingRelevantTechnologyFact> {
            this.from = from
            this.to = to
            return facts.mapNotNull { fact ->
                val reference = fact.occurredOn?.let {
                    BriefingReference(it, BriefingReferenceBasis.OCCURRED_ON)
                } ?: fact.sources.mapNotNull { it.publishedAt }.minOrNull()?.let {
                    BriefingReference(it.atZone(ZoneOffset.UTC).toLocalDate(), BriefingReferenceBasis.SOURCE_PUBLISHED_AT)
                } ?: return@mapNotNull null
                BriefingRelevantTechnologyFact(fact, reference)
            }
        }
    }

    private fun BriefingTechnologyFact.toTechnologyFact() = TechnologyFact(
        id,
        statement,
        category,
        eventType,
        readiness,
        evidenceLevel,
        occurredOn,
        entities,
        sources,
    )
}
