package dev.factweek.economy.internal

import dev.factweek.economy.CreateEconomyFactProposal
import dev.factweek.economy.EconomyProposalExtractionResult
import dev.factweek.economy.EconomyProposalExtractions
import dev.factweek.economy.EconomyFactProposals
import dev.factweek.ingestion.SourceDocuments
import dev.factweek.processing.DocumentClassifications
import dev.factweek.processing.SectionId
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

@Service
internal class EconomyProposalExtractionService(
    private val documents: SourceDocuments,
    private val classifications: DocumentClassifications,
    private val extractorProvider: ObjectProvider<EconomyProposalExtractor>,
    private val writer: EconomyProposalExtractionWriter,
) : EconomyProposalExtractions {
    private val logger = LoggerFactory.getLogger(EconomyProposalExtractionService::class.java)

    override fun extract(sourceDocumentId: UUID): EconomyProposalExtractionResult {
        val document = documents.findFetchedById(sourceDocumentId)
            ?: if (documents.existsById(sourceDocumentId)) throw EconomyExtractionDocumentNotFetchedException()
            else throw EconomyExtractionDocumentNotFoundException()
        val classification = classifications.find(sourceDocumentId)
            ?: throw EconomyExtractionClassificationNotFoundException()
        if (SectionId("economy") !in classification.sections) {
            throw EconomyExtractionClassificationConflictException()
        }
        val extractor = extractorProvider.ifAvailable ?: throw EconomyExtractorUnavailableException()
        val extracted = try {
            extractor.extract(sourceDocumentId, document.sourceUrl, document.textContent)
        } catch (exception: EconomyExtractionInvalidResponseException) {
            logFailure(sourceDocumentId, exception.phase, exception.code, exception.fieldPath, exception)
            throw exception
        } catch (exception: RuntimeException) {
            logFailure(sourceDocumentId, "provider", "MODEL_CALL_FAILED", null, exception)
            throw EconomyExtractionModelException(exception)
        }
        return try {
            val proposals = writer.store(extracted)
            EconomyProposalExtractionResult(sourceDocumentId, proposals.size, proposals)
        } catch (exception: EconomyExtractionInvalidResponseException) {
            logFailure(sourceDocumentId, exception.phase, exception.code, exception.fieldPath, exception)
            throw exception
        }
    }

    private fun logFailure(id: UUID, phase: String, code: String, fieldPath: String?, failure: Throwable) {
        val types = generateSequence(failure) { it.cause }.take(5)
            .map { it.javaClass.simpleName }.joinToString(",")
        logger.warn("economy_extraction_failed documentId={} phase={} code={} fieldPath={} exceptionTypes={}",
            id, phase, code, fieldPath ?: "-", types)
    }
}

@Service
internal class EconomyProposalExtractionWriter(private val proposals: EconomyFactProposals) {
    @Transactional
    fun store(commands: List<CreateEconomyFactProposal>) = commands.mapIndexed { index, command ->
        try {
            proposals.create(command)
        } catch (exception: InvalidEconomyFactProposalException) {
            throw EconomyExtractionInvalidResponseException(exception, "validation", "EVIDENCE_NOT_IN_SOURCE", "proposals[$index].evidenceText")
        } catch (exception: InvalidEconomyFactPublicationException) {
            throw EconomyExtractionInvalidResponseException(exception, "validation", "PROPOSAL_POLICY_FAILED", "proposals[$index]")
        }
    }
}

internal interface EconomyProposalExtractor {
    fun extract(sourceDocumentId: UUID, sourceUrl: String, articleText: String): List<CreateEconomyFactProposal>
}

internal class EconomyExtractionDocumentNotFoundException : RuntimeException()
internal class EconomyExtractionDocumentNotFetchedException : RuntimeException()
internal class EconomyExtractionClassificationNotFoundException : RuntimeException()
internal class EconomyExtractionClassificationConflictException : RuntimeException()
internal class EconomyExtractorUnavailableException : RuntimeException()
internal class EconomyExtractionModelException(cause: Throwable) : RuntimeException(cause)
internal class EconomyExtractionInvalidResponseException(
    cause: Throwable? = null,
    val phase: String = "conversion",
    val code: String = "INVALID_RESPONSE",
    val fieldPath: String? = null,
) : RuntimeException(cause)
