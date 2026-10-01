package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class WeeklyTechnologyBriefingController(
    private val briefing: WeeklyTechnologyBriefing,
) {
    @GetMapping("/api/v1/briefings/technology/current")
    fun current(
        @RequestParam(required = false) categories: Set<TechnologyCategory>?,
        @RequestParam(defaultValue = "10") maximum: Int,
    ): CurrentTechnologyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyTechnologyBriefingRequestException()
        return briefing.current(categories.orEmpty(), maximum)
    }

    @GetMapping("/api/v1/briefings/technology")
    fun between(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) categories: Set<TechnologyCategory>?,
        @RequestParam(defaultValue = "10") maximum: Int,
    ): CurrentTechnologyBriefing {
        val (start, end) = BriefingWindow.requestedDates(from, to)
        return briefing.between(start, end, categories.orEmpty(), maximum)
    }

    @ExceptionHandler(InvalidBriefingWindowException::class)
    fun invalidWindow(error: InvalidBriefingWindowException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.message ?: "Invalid briefing window")

    @ExceptionHandler(InvalidWeeklyTechnologyBriefingRequestException::class, TypeMismatchException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid technology briefing request")
}

internal class InvalidWeeklyTechnologyBriefingRequestException : RuntimeException()
