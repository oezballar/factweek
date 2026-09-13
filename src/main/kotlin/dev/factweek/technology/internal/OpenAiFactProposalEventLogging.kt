package dev.factweek.technology.internal

import org.slf4j.Logger
import java.util.UUID

internal object OpenAiFactProposalEventLogging {
    private const val NOT_AVAILABLE = "n/a"

    fun response(
        logger: Logger,
        sourceDocumentId: UUID,
        model: String,
        schemaVersion: String,
        outcome: String,
        durationMs: Long,
        metadata: OpenAiFactProposalResponseMetadata?,
    ) {
        logger.info(
            "openai_fact_proposal_response_processed sourceDocumentId={} model={} schemaVersion={} outcome={} durationMs={} finishReason={} promptTokens={} completionTokens={} totalTokens={}",
            sourceDocumentId,
            model,
            schemaVersion,
            outcome,
            durationMs,
            metadata?.finishReason ?: NOT_AVAILABLE,
            metadata?.promptTokens ?: NOT_AVAILABLE,
            metadata?.completionTokens ?: NOT_AVAILABLE,
            metadata?.totalTokens ?: NOT_AVAILABLE,
        )
    }

    fun extractionCompleted(
        logger: Logger,
        sourceDocumentId: UUID,
        model: String,
        schemaVersion: String,
        durationMs: Long,
        resultCount: Int,
    ) {
        logger.info(
            "openai_fact_proposal_extraction_completed sourceDocumentId={} model={} schemaVersion={} durationMs={} resultCount={}",
            sourceDocumentId,
            model,
            schemaVersion,
            durationMs,
            resultCount,
        )
    }
}
