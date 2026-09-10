package dev.factweek.ingestion

import java.net.URI
import java.time.Instant

data class GdeltCandidate(
    val title: String,
    val url: URI,
    val sourceCountry: String?,
    val language: String?,
    val discoveredAt: Instant?,
)
