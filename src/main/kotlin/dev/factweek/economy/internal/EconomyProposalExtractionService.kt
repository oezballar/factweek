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
import java.util.UUID

@Service
internal class EconomyProposalExtractionService(
    private val documents: SourceDocuments,
    private val classifications: DocumentClassifications,
    private val extractorProvider: ObjectProvider<EconomyProposalExtractor>,
    private val writer: EconomyProposalExtractionWriter,
) : EconomyProposalExtractions {
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
            throw exception
        } catch (exception: RuntimeException) {
            throw EconomyExtractionModelException(exception)
        }
        return try {
            val proposals = writer.store(extracted)
            EconomyProposalExtractionResult(sourceDocumentId, proposals.size, proposals)
        } catch (exception: InvalidEconomyFactProposalException) {
            throw EconomyExtractionInvalidResponseException(exception)
        } catch (exception: InvalidEconomyFactPublicationException) {
            throw EconomyExtractionInvalidResponseException(exception)
        }
    }
}

@Service
internal class EconomyProposalExtractionWriter(private val proposals: EconomyFactProposals) {
    @Transactional
    fun store(commands: List<CreateEconomyFactProposal>) = commands.map(proposals::create)
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
internal class EconomyExtractionInvalidResponseException(cause: Throwable? = null) : RuntimeException(cause)
