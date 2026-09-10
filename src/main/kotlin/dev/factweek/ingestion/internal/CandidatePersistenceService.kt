package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.util.Locale

@Service
@Transactional
internal class CandidatePersistenceService(
    private val repository: NewsCandidateRepository,
    private val candidateWriter: CandidateWriter,
) {
    fun storeDiscovered(candidates: Collection<GdeltCandidate>): List<NewsCandidateEntity> =
        candidates.map { storeDiscovered(it) }

    fun storeDiscovered(candidate: GdeltCandidate): NewsCandidateEntity = storeDiscoveredWithOutcome(candidate).candidate

    fun storeDiscoveredWithOutcome(candidate: GdeltCandidate): CandidatePersistenceResult {
        val canonicalUrl = canonicalize(candidate.url)
        repository.findByCanonicalUrl(canonicalUrl)?.let {
            return CandidatePersistenceResult(it, CandidatePersistenceOutcome.EXISTING)
        }

        val entity = NewsCandidateEntity(
            canonicalUrl = canonicalUrl,
            title = candidate.title.trim(),
            sourceDomain = sourceDomain(candidate.url),
            publishedAt = candidate.discoveredAt,
            fetchedAt = Instant.now(),
            status = CandidateStatus.DISCOVERED,
        )

        return try {
            CandidatePersistenceResult(candidateWriter.insert(entity), CandidatePersistenceOutcome.STORED)
        } catch (_: DataIntegrityViolationException) {
            // A separate transaction is deliberately used for the insert. A unique-key
            // violation marks that transaction rollback-only, while this transaction can
            // still read and return the row inserted by the concurrent caller.
            CandidatePersistenceResult(
                requireNotNull(repository.findByCanonicalUrl(canonicalUrl)) {
                    "Candidate insert failed but no candidate exists for $canonicalUrl"
                },
                CandidatePersistenceOutcome.EXISTING,
            )
        }
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

internal data class CandidatePersistenceResult(
    val candidate: NewsCandidateEntity,
    val outcome: CandidatePersistenceOutcome,
)

internal enum class CandidatePersistenceOutcome {
    STORED,
    EXISTING,
}
