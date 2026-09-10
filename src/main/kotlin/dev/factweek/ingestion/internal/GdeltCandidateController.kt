package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.GdeltCandidates
import dev.factweek.ingestion.GdeltImportResult
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
internal class GdeltCandidateController(
    private val candidates: GdeltCandidates,
    private val importService: GdeltImportService,
    private val clock: Clock,
) {
    @GetMapping("/api/v1/ingestion/gdelt/candidates")
    fun candidates(
        @RequestParam(defaultValue = "technology") query: String,
        @RequestParam(defaultValue = "25") maximum: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): List<GdeltCandidate> {
        val window = window(from, to)
        GdeltImportService.validate(query, window.from, window.to, maximum)
        return candidates.findTechnologyCandidates(query, window.from, window.to, maximum)
    }

    @PostMapping("/api/v1/ingestion/gdelt/imports")
    fun import(
        @RequestParam query: String,
        @RequestParam(defaultValue = "100") maximum: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): GdeltImportResult {
        val window = window(from, to)
        GdeltImportService.validate(query, window.from, window.to, maximum)
        return importService.import(query, window.from, window.to, maximum)
    }

    @ExceptionHandler(InvalidGdeltImportRequestException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid GDELT import request")

    @ExceptionHandler(GdeltRequestException::class)
    fun gdeltUnavailable(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Unable to retrieve GDELT candidates")

    private fun window(from: Instant?, to: Instant?): ImportWindow {
        if ((from == null) != (to == null)) {
            throw InvalidGdeltImportRequestException()
        }
        if (from != null && to != null) {
            return ImportWindow(from, to)
        }
        val resolvedTo = to ?: clock.instant()
        return ImportWindow(from ?: resolvedTo.minus(7, ChronoUnit.DAYS), resolvedTo)
    }

    private data class ImportWindow(val from: Instant, val to: Instant)
}
