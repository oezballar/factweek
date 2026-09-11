package dev.factweek.technology.internal

import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.technology.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CandidateSourceTypeMapperTest {
    @Test
    fun `maps every candidate source type explicitly`() {
        val expected = mapOf(
            CandidateSourceType.NEWS_REPORT to SourceType.NEWS_REPORT,
            CandidateSourceType.PRIMARY_DOCUMENT to SourceType.PRIMARY_DOCUMENT,
            CandidateSourceType.PAPER to SourceType.PAPER,
            CandidateSourceType.DATASET to SourceType.DATASET,
            CandidateSourceType.REPOSITORY to SourceType.REPOSITORY,
            CandidateSourceType.REGULATOR to SourceType.REGULATOR,
        )

        assertEquals(CandidateSourceType.entries.toSet(), expected.keys)
        assertEquals(expected, CandidateSourceType.entries.associateWith { it.toTechnologySourceType() })
    }
}
