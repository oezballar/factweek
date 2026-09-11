package dev.factweek.ingestion

import java.net.URI
import java.time.Instant
import java.util.UUID

/** Public write API for recording a discovered candidate, independent of its discovery provider. */
interface CandidateCaptures {
    fun capture(command: CandidateCaptureCommand): CandidateCaptureResult
}

data class CandidateCaptureCommand(
    val sourceUrl: URI,
    val title: String,
    val publisher: String,
    val publishedAt: Instant?,
    val language: String?,
    val discoveryProvider: CandidateDiscoveryProvider,
    val sourceType: CandidateSourceType = CandidateSourceType.NEWS_REPORT,
)

data class CandidateCaptureResult(
    val candidateId: UUID,
    val sourceUrl: URI,
    val title: String,
    val publisher: String,
    val publishedAt: Instant?,
    val language: String?,
    val discoveryProvider: CandidateDiscoveryProvider,
    val sourceType: CandidateSourceType = CandidateSourceType.NEWS_REPORT,
    val created: Boolean,
)

enum class CandidateDiscoveryProvider {
    GDELT,
    MANUAL,
}

enum class CandidateSourceType { NEWS_REPORT, PRIMARY_DOCUMENT, PAPER, DATASET, REPOSITORY, REGULATOR }
