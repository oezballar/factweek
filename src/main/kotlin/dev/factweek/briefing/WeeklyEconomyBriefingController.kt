package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
internal class WeeklyEconomyBriefingController(
    private val briefing: WeeklyEconomyBriefing,
) {
    @GetMapping("/api/v1/briefings/economy/current")
    fun current(
        @RequestParam(required = false) categories: Set<EconomyCategory>?,
        @RequestParam(defaultValue = "10") maximum: Int,
    ): CurrentEconomyBriefing {
        if (maximum !in 1..50) throw InvalidWeeklyEconomyBriefingRequestException()
        return briefing.current(categories.orEmpty(), maximum)
    }

    @ExceptionHandler(InvalidWeeklyEconomyBriefingRequestException::class, TypeMismatchException::class)
    fun invalid(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid economy briefing request")
}
