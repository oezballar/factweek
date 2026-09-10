package dev.factweek.ingestion

interface GdeltCandidates {
    fun findTechnologyCandidates(query: String, maximum: Int = 100): List<GdeltCandidate>
}
