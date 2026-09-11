package dev.factweek.ingestion.internal

import org.springframework.stereotype.Component
import java.net.URI
import java.util.Locale

/** Syntax-only validation used at discovery time; public-address validation remains a fetch concern. */
@Component
internal class CandidateUrlNormalizer {
    fun normalize(url: URI): URI {
        val scheme = url.scheme?.lowercase(Locale.ROOT)
        if (!url.isAbsolute || scheme !in setOf("http", "https") || url.host.isNullOrBlank() ||
            url.userInfo != null || url.fragment != null
        ) throw InvalidCandidateCaptureException()

        val host = url.host.lowercase(Locale.ROOT)
        val port = when {
            url.port == -1 -> -1
            scheme == "http" && url.port == 80 -> -1
            scheme == "https" && url.port == 443 -> -1
            else -> url.port
        }
        val path = url.path?.takeIf { it.isNotBlank() } ?: "/"
        return try {
            URI(scheme, null, host, port, path, url.query, null).normalize()
        } catch (_: IllegalArgumentException) {
            throw InvalidCandidateCaptureException()
        }
    }
}
