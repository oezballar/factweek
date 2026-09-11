package dev.factweek.technology.internal

import dev.factweek.ingestion.SourceDocuments
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
internal class FactProposalExtractionService(
    private val sourceDocuments: SourceDocuments,
    private val extractorProvider: ObjectProvider<FactProposalExtractor>,
    private val proposals: FactProposalRepository,
    private val clock: Clock,
    meterRegistry: MeterRegistry,
) {
    private val proposedCounter = meterRegistry.counter("factweek.technology.fact-proposals.proposed")
    private val rejectedCounter = meterRegistry.counter("factweek.technology.fact-proposals.rejected")
    private val skippedCounter = meterRegistry.counter("factweek.technology.fact-proposals.skipped")

    @Transactional
    fun extract(maximum: Int): FactProposalExtractionResult {
        if (maximum !in 1..25) throw InvalidFactProposalExtractionRequestException()
        val extractor = requireNotNull(extractorProvider.ifAvailable) { "No fact-proposal extractor is configured" }
        val selected = sourceDocuments.findFetchedForFactProposals(maximum)
        var proposed = 0
        var rejected = 0
        var skipped = 0

        selected.forEach { sourceDocument ->
            if (proposals.existsBySourceDocumentId(sourceDocument.id)) {
                skipped++
                return@forEach
            }
            val extracted = extractor.extract(
                FactProposalExtractionRequest(
                    sourceDocumentId = sourceDocument.id,
                    sourceUrl = sourceDocument.sourceUrl,
                    textContent = sourceDocument.textContent,
                    contentSha256 = sourceDocument.contentSha256,
                ),
            )
            val valid = extracted.filter(::isValid)
                .distinctBy { proposal -> proposal.statement.trim() to proposal.extractionSchemaVersion.trim() }
            if (valid.isEmpty()) {
                rejected++
                return@forEach
            }
            valid.forEach { proposal ->
                proposals.save(
                    FactProposalEntity(
                        sourceDocumentId = sourceDocument.id,
                        statement = proposal.statement.trim(),
                        category = proposal.category,
                        occurredOn = proposal.occurredOn,
                        evidenceText = proposal.evidenceText.trim(),
                        evidenceLevel = proposal.evidenceLevel,
                        extractionModel = proposal.extractionModel.trim(),
                        extractionSchemaVersion = proposal.extractionSchemaVersion.trim(),
                        createdAt = clock.instant(),
                        entities = proposal.entities.map { EntityValue(it.name.trim(), it.type) }.toMutableList(),
                    ),
                )
            }
            proposed++
        }

        proposedCounter.increment(proposed.toDouble())
        rejectedCounter.increment(rejected.toDouble())
        skippedCounter.increment(skipped.toDouble())
        val result = FactProposalExtractionResult(maximum, selected.size, proposed, rejected, skipped)
        logger.atInfo()
            .addKeyValue("selectedCount", result.selectedCount)
            .addKeyValue("proposedCount", result.proposedCount)
            .addKeyValue("rejectedCount", result.rejectedCount)
            .addKeyValue("skippedCount", result.skippedCount)
            .log("fact_proposal_extraction_completed")
        return result
    }

    private fun isValid(proposal: ExtractedFactProposal): Boolean =
        proposal.statement.isNotBlank() && proposal.statement.length <= 1000 &&
            proposal.evidenceText.isNotBlank() && proposal.evidenceText.length <= 2000 &&
            proposal.extractionModel.isNotBlank() && proposal.extractionModel.length <= 255 &&
            proposal.extractionSchemaVersion.isNotBlank() && proposal.extractionSchemaVersion.length <= 128 &&
            proposal.entities.all { it.name.isNotBlank() && it.name.length <= 255 }

    private companion object {
        val logger = LoggerFactory.getLogger(FactProposalExtractionService::class.java)
    }
}
