package dev.factweek.ingestion.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
internal class SourceDocumentPersistenceService(
    private val repository: SourceDocumentRepository,
    private val clock: Clock,
) {
    @Transactional
    fun recordSuccess(candidate: NewsCandidateEntity, result: SourceContentFetchResult) {
        val now = clock.instant()
        val existing = repository.findById(candidate.id).orElse(null)
        if (existing?.status == SourceDocumentStatus.FETCHED) return
        val document = existing ?: SourceDocumentEntity(
            candidate = candidate,
            sourceUrl = result.finalUrl.toASCIIString(),
            status = SourceDocumentStatus.FETCHED,
            lastAttemptAt = now,
            attemptCount = 0,
        )
        document.sourceUrl = result.finalUrl.toASCIIString()
        document.status = SourceDocumentStatus.FETCHED
        document.mediaType = result.mediaType
        document.httpStatus = result.httpStatus
        document.textContent = result.extractedText
        document.contentSha256 = result.contentSha256
        document.fetchedAt = now
        document.lastAttemptAt = now
        document.attemptCount += 1
        document.failureReason = null
        repository.save(document)
    }

    @Transactional
    fun recordFailure(candidate: NewsCandidateEntity, reason: SourceContentFailureReason) {
        val now = clock.instant()
        val document = repository.findById(candidate.id).orElseGet {
            SourceDocumentEntity(candidate = candidate, sourceUrl = candidate.canonicalUrl, status = SourceDocumentStatus.FAILED, lastAttemptAt = now, attemptCount = 0)
        }
        if (document.status == SourceDocumentStatus.FETCHED) return
        document.status = SourceDocumentStatus.FAILED
        document.lastAttemptAt = now
        document.attemptCount += 1
        document.failureReason = reason
        repository.save(document)
    }
}
