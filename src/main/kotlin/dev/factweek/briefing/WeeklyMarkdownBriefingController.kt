package dev.factweek.briefing

import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
class WeeklyMarkdownBriefingController(private val briefing: WeeklyMarkdownBriefing) {
    @GetMapping("/api/v1/briefings/weekly.md")
    fun between(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "10") maximumPerSection: Int,
    ): ResponseEntity<String> {
        val (start, end) = BriefingWindow.requestedDates(from, to)
        val body = briefing.between(start, end, maximumPerSection)
        val filename = "factweek-${start}_${end}.md"
        return ResponseEntity.ok()
            .contentType(MediaType("text", "markdown", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(body)
    }

    @ExceptionHandler(InvalidBriefingWindowException::class)
    fun invalidWindow(error: InvalidBriefingWindowException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.message ?: "Ungültiges Zeitfenster")

    @ExceptionHandler(InvalidWeeklyMarkdownBriefingRequestException::class, TypeMismatchException::class)
    fun invalidMaximum(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "maximumPerSection muss zwischen 1 und 50 liegen")
}
