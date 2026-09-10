package dev.factweek.ingestion.internal

internal enum class SourceContentFailureReason {
    UNSAFE_URL,
    HTTP_ERROR,
    UNSUPPORTED_MEDIA_TYPE,
    CONTENT_TOO_LARGE,
    EMPTY_CONTENT,
    TIMEOUT,
    NETWORK_ERROR,
    TOO_MANY_REDIRECTS,
}
