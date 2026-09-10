package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidates
import dev.factweek.ingestion.GdeltImportResult
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
internal class GdeltImportService(
    private val candidates: GdeltCandidates,
    private val persistenceService: CandidatePersistenceService,
    meterRegistry: MeterRegistry,
) {
    private val discoveredCounter = meterRegistry.counter("factweek.ingestion.gdelt.candidates.discovered")
    private val storedCounter = meterRegistry.counter("factweek.ingestion.gdelt.candidates.stored")
    private val existingCounter = meterRegistry.counter("factweek.ingestion.gdelt.candidates.existing")

    fun import(query: String, from: Instant, to: Instant, maximum: Int): GdeltImportResult {
        validate(query, from, to, maximum)
        val discovered = candidates.findTechnologyCandidates(query, from, to, maximum)
        val outcomes = discovered.map { persistenceService.storeDiscoveredWithOutcome(it).outcome }
        val storedCount = outcomes.count { it == CandidatePersistenceOutcome.STORED }
        val existingCount = outcomes.count { it == CandidatePersistenceOutcome.EXISTING }

        discoveredCounter.increment(discovered.size.toDouble())
        storedCounter.increment(storedCount.toDouble())
        existingCounter.increment(existingCount.toDouble())

        val result = GdeltImportResult(
            query = query,
            from = from,
            to = to,
            requestedMaximum = maximum,
            discoveredCount = discovered.size,
            storedCount = storedCount,
            existingCount = existingCount,
        )
        logger.atInfo()
            .addKeyValue("query", query)
            .addKeyValue("from", from)
            .addKeyValue("to", to)
            .addKeyValue("discoveredCount", result.discoveredCount)
            .addKeyValue("storedCount", result.storedCount)
            .addKeyValue("existingCount", result.existingCount)
            .log("gdelt_import_completed")
        return result
    }

    companion object {
        fun validate(query: String, from: Instant, to: Instant, maximum: Int) {
            if (query.isBlank() || !from.isBefore(to) || maximum !in 1..250 || Duration.between(from, to) > Duration.ofDays(7)) {
                throw InvalidGdeltImportRequestException()
            }
        }

        private val logger = LoggerFactory.getLogger(GdeltImportService::class.java)
    }
}
