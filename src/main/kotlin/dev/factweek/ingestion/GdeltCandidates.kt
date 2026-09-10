package dev.factweek.ingestion

import java.time.Instant

interface GdeltCandidates {
    fun findTechnologyCandidates(
        query: String,
        from: Instant,
        to: Instant,
        maximum: Int = 100,
    ): List<GdeltCandidate>
}
