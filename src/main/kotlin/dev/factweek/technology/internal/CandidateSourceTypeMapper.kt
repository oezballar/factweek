package dev.factweek.technology.internal

import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.provenance.SourceType

internal fun CandidateSourceType.toProvenanceSourceType(): SourceType = when (this) {
    CandidateSourceType.NEWS_REPORT -> SourceType.NEWS_REPORT
    CandidateSourceType.PRIMARY_DOCUMENT -> SourceType.PRIMARY_DOCUMENT
    CandidateSourceType.PAPER -> SourceType.PAPER
    CandidateSourceType.DATASET -> SourceType.DATASET
    CandidateSourceType.REPOSITORY -> SourceType.REPOSITORY
    CandidateSourceType.REGULATOR -> SourceType.REGULATOR
}
