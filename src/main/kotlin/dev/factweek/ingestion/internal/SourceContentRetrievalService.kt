package dev.factweek.ingestion.internal

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.net.URI

@Service
internal class SourceContentRetrievalService(
    private val candidates: NewsCandidateRepository,
    private val documents: SourceDocumentRepository,
    private val fetcher: SourceContentFetcher,
    private val persistence: SourceDocumentPersistenceService,
    meterRegistry: MeterRegistry,
) {
    private val fetchedCounter = meterRegistry.counter("factweek.ingestion.source-content.fetched")
    private val failedCounter = meterRegistry.counter("factweek.ingestion.source-content.failed")
    private val skippedCounter = meterRegistry.counter("factweek.ingestion.source-content.skipped")

    fun retrieve(maximum: Int, retryFailed: Boolean): SourceContentRetrievalResult {
        if (maximum !in 1..25) throw InvalidSourceContentFetchRequestException()
        val selected = candidates.findAll(
            Sort.by(Sort.Order.asc("publishedAt").nullsLast(), Sort.Order.asc("id")),
        ).asSequence()
            .filter { documents.findById(it.id).orElse(null)?.status != SourceDocumentStatus.FETCHED }
            .take(maximum)
            .toList()

        var fetched = 0
        var failed = 0
        var skipped = 0
        selected.forEach { candidate ->
            val existing = documents.findById(candidate.id).orElse(null)
            if (existing?.status == SourceDocumentStatus.FAILED && !retryFailed) {
                skipped++
                return@forEach
            }
            try {
                persistence.recordSuccess(candidate, fetcher.fetch(URI(candidate.canonicalUrl)))
                fetched++
            } catch (exception: SourceContentFetchException) {
                persistence.recordFailure(candidate, exception.reason)
                failed++
            } catch (_: Exception) {
                persistence.recordFailure(candidate, SourceContentFailureReason.NETWORK_ERROR)
                failed++
            }
        }

        fetchedCounter.increment(fetched.toDouble())
        failedCounter.increment(failed.toDouble())
        skippedCounter.increment(skipped.toDouble())
        val result = SourceContentRetrievalResult(maximum, selected.size, fetched, failed, skipped)
        logger.atInfo()
            .addKeyValue("selectedCount", result.selectedCount)
            .addKeyValue("fetchedCount", result.fetchedCount)
            .addKeyValue("failedCount", result.failedCount)
            .addKeyValue("skippedCount", result.skippedCount)
            .log("source_content_retrieval_completed")
        return result
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SourceContentRetrievalService::class.java)
    }
}
