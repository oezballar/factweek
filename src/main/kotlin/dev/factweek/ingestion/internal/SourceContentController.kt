package dev.factweek.ingestion.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
internal class SourceContentController(
    private val retrievalService: SourceContentRetrievalService,
) {
    @PostMapping("/api/v1/ingestion/source-content/fetches")
    fun fetch(
        @RequestParam(defaultValue = "10") maximum: Int,
        @RequestParam(defaultValue = "false") retryFailed: Boolean,
    ): SourceContentRetrievalResult = retrievalService.retrieve(maximum, retryFailed)

    @ExceptionHandler(InvalidSourceContentFetchRequestException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid source-content fetch request")
}
