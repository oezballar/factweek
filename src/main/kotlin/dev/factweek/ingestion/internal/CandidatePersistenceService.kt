package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.util.Locale

@Service
@Transactional
internal class CandidatePersistenceService(
    private val repository: NewsCandidateRepository,
) {
    fun storeDiscovered(candidates: Collection<GdeltCandidate>): List<NewsCandidateEntity> =
        candidates.map { storeDiscovered(it) }

    fun storeDiscovered(candidate: GdeltCandidate): NewsCandidateEntity {
        val canonicalUrl = canonicalize(candidate.url)
        return repository.findByCanonicalUrl(canonicalUrl) ?: repository.save(
            NewsCandidateEntity(
                canonicalUrl = canonicalUrl,
                title = candidate.title.trim(),
                sourceDomain = sourceDomain(candidate.url),
                publishedAt = candidate.discoveredAt,
                fetchedAt = Instant.now(),
                status = CandidateStatus.DISCOVERED,
            ),
        )
    }

    private fun canonicalize(url: URI): String {
        require(url.isAbsolute) { "Candidate URL must be absolute" }

        val scheme = requireNotNull(url.scheme).lowercase(Locale.ROOT)
        val host = requireNotNull(url.host) { "Candidate URL must contain a host" }.lowercase(Locale.ROOT)
        val port = when {
            url.port == -1 -> -1
            scheme == "http" && url.port == 80 -> -1
            scheme == "https" && url.port == 443 -> -1
            else -> url.port
        }
        val path = url.path?.takeIf { it.isNotBlank() } ?: "/"

        return URI(scheme, null, host, port, path, url.query, null).normalize().toASCIIString()
    }

    private fun sourceDomain(url: URI): String =
        requireNotNull(url.host) { "Candidate URL must contain a host" }
            .lowercase(Locale.ROOT)
            .removePrefix("www.")
}
