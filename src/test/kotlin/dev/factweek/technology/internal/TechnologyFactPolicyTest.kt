package dev.factweek.technology.internal

import dev.factweek.technology.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TechnologyFactPolicyTest {
    @Test
    fun `rejects publication without primary evidence`() {
        val error = assertThrows<IllegalArgumentException> {
            TechnologyFactPolicy.validate(command(evidenceLevel = EvidenceLevel.DOCUMENTED))
        }

        assertEquals("A published fact needs primary evidence or stronger", error.message)
    }

    @Test
    fun `accepts a fact backed by a primary source`() {
        TechnologyFactPolicy.validate(command(evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED))
    }

    private fun command(evidenceLevel: EvidenceLevel) = PublishTechnologyFact(
        statement = "A research team published a reproducible efficiency result.",
        category = TechnologyCategory.ENERGY_AND_CLIMATE,
        eventType = TechnologyEventType.RESEARCH_RESULT_PUBLISHED,
        readiness = TechnologyReadiness.LAB_RESULT,
        evidenceLevel = evidenceLevel,
        occurredOn = LocalDate.of(2026, 9, 10),
        entities = emptyList(),
        sources = listOf(SourceReference("https://example.org/paper", "Example Journal", SourceType.PAPER)),
    )
}
