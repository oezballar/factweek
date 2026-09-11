package dev.factweek.ingestion.internal

import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateCaptureResult
import dev.factweek.ingestion.CandidateCaptures
import dev.factweek.ingestion.CandidateDiscoveryProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.Locale

@Service
@Transactional
internal class CandidatePersistenceService(
    private val repository: NewsCandidateRepository,
    private val candidateWriter: CandidateWriter,
    private val urlNormalizer: CandidateUrlNormalizer,
    private val clock: Clock,
) : CandidateCaptures {
    /** Internal entity bridge for ingestion persistence collaborators. */
    fun storeDiscovered(command: CandidateCaptureCommand): NewsCandidateEntity =
        repository.findById(capture(command).candidateId).orElseThrow()

    override fun capture(command: CandidateCaptureCommand): CandidateCaptureResult {
        val normalized = normalize(command)
        repository.findByCanonicalUrl(normalized.canonicalUrl)?.let { existing ->
            return existingResult(existing, normalized)
        }

        val entity = NewsCandidateEntity(
            canonicalUrl = normalized.canonicalUrl,
            title = normalized.title,
            publisher = normalized.publisher,
            sourceDomain = normalized.sourceDomain,
            publishedAt = normalized.publishedAt,
            language = normalized.language,
            discoveryProvider = normalized.discoveryProvider,
            sourceType = normalized.sourceType,
            fetchedAt = clock.instant(),
            status = CandidateStatus.DISCOVERED,
        )

        return try {
            toResult(candidateWriter.insert(entity), created = true)
        } catch (_: DataIntegrityViolationException) {
            // The insert runs in REQUIRES_NEW, so a uniqueness race rolls back only that
            // insert and the caller can safely inspect the winner in this transaction.
            existingResult(
                requireNotNull(repository.findByCanonicalUrl(normalized.canonicalUrl)) {
                    "Candidate insert failed but no candidate exists for ${normalized.canonicalUrl}"
                },
                normalized,
            )
        }
    }

    private fun normalize(command: CandidateCaptureCommand): NormalizedCandidateCapture {
        val sourceUrl = urlNormalizer.normalize(command.sourceUrl)
        val title = command.title.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH }
            ?: throw InvalidCandidateCaptureException()
        val publisher = command.publisher.trim().takeIf { it.isNotEmpty() && it.length <= MAX_PUBLISHER_LENGTH }
            ?: throw InvalidCandidateCaptureException()
        val language = command.language?.trim()?.lowercase(Locale.ROOT)?.takeIf { LANGUAGE_CODE.matches(it) }
        if (command.language != null && language == null) throw InvalidCandidateCaptureException()
        if (command.publishedAt?.isAfter(clock.instant().plus(FUTURE_TIMESTAMP_TOLERANCE)) == true) {
            throw InvalidCandidateCaptureException()
        }
        return NormalizedCandidateCapture(
            canonicalUrl = sourceUrl.toASCIIString(),
            title = title,
            publisher = publisher,
            sourceDomain = sourceUrl.host.lowercase(Locale.ROOT).removePrefix("www."),
            publishedAt = command.publishedAt,
            language = language,
            discoveryProvider = command.discoveryProvider,
            sourceType = command.sourceType,
        )
    }

    private fun existingResult(existing: NewsCandidateEntity, incoming: NormalizedCandidateCapture): CandidateCaptureResult {
        if (incoming.discoveryProvider == CandidateDiscoveryProvider.MANUAL && !existing.matches(incoming)) {
            throw CandidateCaptureConflictException()
        }
        return toResult(existing, created = false)
    }

    private fun NewsCandidateEntity.matches(incoming: NormalizedCandidateCapture): Boolean =
        title == incoming.title && publisher == incoming.publisher &&
            publishedAt == incoming.publishedAt && language == incoming.language && sourceType == incoming.sourceType

    private fun toResult(candidate: NewsCandidateEntity, created: Boolean) = CandidateCaptureResult(
        candidateId = candidate.id,
        sourceUrl = java.net.URI(candidate.canonicalUrl),
        title = candidate.title,
        publisher = candidate.publisher,
        publishedAt = candidate.publishedAt,
        language = candidate.language,
        discoveryProvider = candidate.discoveryProvider,
        sourceType = candidate.sourceType,
        created = created,
    )

    private data class NormalizedCandidateCapture(
        val canonicalUrl: String,
        val title: String,
        val publisher: String,
        val sourceDomain: String,
        val publishedAt: java.time.Instant?,
        val language: String?,
        val discoveryProvider: CandidateDiscoveryProvider,
        val sourceType: dev.factweek.ingestion.CandidateSourceType,
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 1000
        const val MAX_PUBLISHER_LENGTH = 255
        val LANGUAGE_CODE = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")
        val FUTURE_TIMESTAMP_TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}

internal class InvalidCandidateCaptureException : RuntimeException()
internal class CandidateCaptureConflictException : RuntimeException()
