package dev.factweek.ingestion.internal

import java.net.URI

internal interface SourceContentFetcher {
    fun fetch(sourceUrl: URI): SourceContentFetchResult
}

internal data class SourceContentFetchResult(
    val finalUrl: URI,
    val mediaType: String,
    val httpStatus: Int,
    val extractedText: String,
    val contentSha256: String,
)

internal class SourceContentFetchException(val reason: SourceContentFailureReason) : RuntimeException()
