package dev.factweek.economy.internal

import dev.factweek.economy.EconomyProposalExtractionResult
import dev.factweek.economy.EconomyProposalExtractions
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

@RestController
internal class EconomyProposalExtractionController(private val extractions: EconomyProposalExtractions) {
    @PostMapping("/api/v1/economy/fact-proposals/extractions/{sourceDocumentId}")
    fun extract(@PathVariable sourceDocumentId: UUID): EconomyProposalExtractionResult =
        extractions.extract(sourceDocumentId)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun invalidId() = problem(HttpStatus.BAD_REQUEST, "A valid source document ID is required")

    @ExceptionHandler(EconomyExtractionDocumentNotFoundException::class, EconomyExtractionClassificationNotFoundException::class)
    fun notFound() = problem(HttpStatus.NOT_FOUND, "Source document or current classification was not found")

    @ExceptionHandler(EconomyExtractionDocumentNotFetchedException::class, EconomyExtractionClassificationConflictException::class)
    fun conflict() = problem(HttpStatus.CONFLICT, "Source document is not fetched or is not classified as economy")

    @ExceptionHandler(EconomyExtractorUnavailableException::class)
    fun unavailable() = problem(HttpStatus.SERVICE_UNAVAILABLE, "Economy proposal extractor is not available")

    @ExceptionHandler(EconomyExtractionModelException::class, EconomyExtractionInvalidResponseException::class)
    fun extractionFailed() = problem(HttpStatus.BAD_GATEWAY, "Economy proposal extraction failed")

    private fun problem(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail)
}
