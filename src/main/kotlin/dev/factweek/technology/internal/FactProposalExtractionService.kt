package dev.factweek.technology.internal

import dev.factweek.ingestion.SourceDocuments
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale
import java.util.UUID

@Service
internal class FactProposalExtractionService(
    private val sourceDocuments: SourceDocuments,
    private val extractorProvider: ObjectProvider<FactProposalExtractor>,
    private val proposals: FactProposalRepository,
    private val attempts: FactProposalExtractionAttemptRepository,
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
        val metadata = extractor.metadata.validated()
        val processedSourceDocumentIds = attempts.findProcessedSourceDocumentIds(metadata.schemaVersion).toSet()
        val selected = sourceDocuments.findFetchedForFactProposals(maximum, processedSourceDocumentIds)
        var proposed = 0
        var rejected = 0
        var skipped = 0

        selected.forEach { sourceDocument ->
            if (attempts.claim(UUID.randomUUID(), sourceDocument.id, metadata.model, metadata.schemaVersion, clock.instant()) == 0) {
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
            val seenStatements = mutableSetOf<String>()
            extracted.forEach { proposal ->
                val normalizedStatement = normalizeWhitespace(proposal.statement)
                if (!isValid(sourceDocument.textContent, proposal) || !seenStatements.add(normalizedStatement)) {
                    rejected++
                    return@forEach
                }
                proposals.save(
                    FactProposalEntity(
                        sourceDocumentId = sourceDocument.id,
                        statement = normalizedStatement,
                        category = proposal.category,
                        occurredOn = proposal.occurredOn,
                        evidenceText = normalizeWhitespace(proposal.evidenceText),
                        evidenceLevel = proposal.evidenceLevel,
                        extractionModel = metadata.model,
                        extractionSchemaVersion = metadata.schemaVersion,
                        createdAt = clock.instant(),
                        entities = proposal.entities.map { EntityValue(it.name.trim(), it.type) }.toMutableList(),
                    ),
                )
                proposed++
            }
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

    private fun isValid(sourceText: String, proposal: ExtractedFactProposal): Boolean {
        val statement = normalizeWhitespace(proposal.statement)
        val evidence = normalizeWhitespace(proposal.evidenceText)
        return statement.isNotBlank() && statement.length <= 1000 &&
            evidence.isNotBlank() && evidence.length <= 2000 &&
            normalizeForComparison(sourceText).contains(normalizeForComparison(evidence)) &&
            proposal.entities.all { it.name.isNotBlank() && it.name.length <= 255 }
    }

    private fun FactProposalExtractionMetadata.validated(): FactProposalExtractionMetadata {
        val normalizedModel = normalizeWhitespace(model)
        val normalizedSchemaVersion = normalizeWhitespace(schemaVersion)
        require(normalizedModel.isNotBlank() && normalizedModel.length <= 255) { "Invalid extraction model metadata" }
        require(normalizedSchemaVersion.isNotBlank() && normalizedSchemaVersion.length <= 128) { "Invalid extraction schema metadata" }
        return FactProposalExtractionMetadata(normalizedModel, normalizedSchemaVersion)
    }

    private fun normalizeWhitespace(value: String): String = value.trim().replace(WHITESPACE, " ")

    private fun normalizeForComparison(value: String): String = normalizeWhitespace(value).lowercase(Locale.ROOT)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val logger = LoggerFactory.getLogger(FactProposalExtractionService::class.java)
    }
}
