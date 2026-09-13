package dev.factweek.technology.internal

import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.technology.EvidenceLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ReviewedEvidencePolicyTest {
    @ParameterizedTest @MethodSource("matrix")
    fun `classifies every source type and evidence level explicitly`(sourceType: CandidateSourceType, evidenceLevel: EvidenceLevel, allowed: Boolean) {
        if (allowed) assertDoesNotThrow { ReviewedEvidencePolicy.validate(sourceType, evidenceLevel) }
        else assertThrows<InvalidReviewedEvidenceDecisionException> { ReviewedEvidencePolicy.validate(sourceType, evidenceLevel) }
    }
    companion object {
        private val expectedByEvidenceLevel = mapOf(
            EvidenceLevel.REPORTED to false,
            EvidenceLevel.DOCUMENTED to false,
            EvidenceLevel.PRIMARY_CONFIRMED to true,
            EvidenceLevel.INDEPENDENTLY_CONFIRMED to false,
            EvidenceLevel.PROVEN_IN_USE to false,
        )

        private val primaryConfirmedSourceTypes = setOf(
            CandidateSourceType.PRIMARY_DOCUMENT,
            CandidateSourceType.PAPER,
            CandidateSourceType.DATASET,
            CandidateSourceType.REPOSITORY,
            CandidateSourceType.REGULATOR,
        )

        private val expectedSourceTypes = primaryConfirmedSourceTypes + CandidateSourceType.NEWS_REPORT

        @JvmStatic
        fun matrix(): Stream<Array<Any>> {
            assertEquals(EvidenceLevel.entries.toSet(), expectedByEvidenceLevel.keys)
            assertEquals(CandidateSourceType.entries.toSet(), expectedSourceTypes)
            return CandidateSourceType.entries.flatMap { source ->
                EvidenceLevel.entries.map { level ->
                    arrayOf<Any>(
                        source,
                        level,
                        expectedByEvidenceLevel.getValue(level) && source in primaryConfirmedSourceTypes,
                    )
                }
            }.stream()
        }
    }
}
