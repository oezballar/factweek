package dev.factweek.technology.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
internal class FactProposalController(
    private val extractionService: FactProposalExtractionService,
) {
    @PostMapping("/api/v1/technology/fact-proposals/extractions")
    fun extract(@RequestParam(defaultValue = "10") maximum: Int): FactProposalExtractionResult {
        if (maximum !in 1..25) throw InvalidFactProposalExtractionRequestException()
        return extractionService.extract(maximum)
    }

    @ExceptionHandler(InvalidFactProposalExtractionRequestException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid fact-proposal extraction request")
}
