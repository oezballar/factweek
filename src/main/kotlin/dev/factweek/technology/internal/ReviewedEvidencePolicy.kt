package dev.factweek.technology.internal

import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.technology.EvidenceLevel

internal object ReviewedEvidencePolicy {
    fun validate(sourceType: CandidateSourceType, evidenceLevel: EvidenceLevel) {
        val allowed = when (evidenceLevel) {
            EvidenceLevel.PRIMARY_CONFIRMED -> sourceType in setOf(
                CandidateSourceType.PRIMARY_DOCUMENT,
                CandidateSourceType.PAPER,
                CandidateSourceType.DATASET,
                CandidateSourceType.REPOSITORY,
                CandidateSourceType.REGULATOR,
            )
            EvidenceLevel.REPORTED,
            EvidenceLevel.DOCUMENTED,
            EvidenceLevel.INDEPENDENTLY_CONFIRMED,
            EvidenceLevel.PROVEN_IN_USE,
            -> false
        }
        if (!allowed) throw InvalidReviewedEvidenceDecisionException()
    }
}

internal class InvalidReviewedEvidenceDecisionException : RuntimeException()
