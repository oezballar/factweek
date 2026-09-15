package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.*

@RestController
internal class WeeklyEconomyBriefingController(private val briefing: WeeklyEconomyBriefing) {
    @GetMapping("/api/v1/briefings/economy/current")
    fun current(@RequestParam(required = false) categories: Set<EconomyCategory>?, @RequestParam(defaultValue = "10") maximum: Int) = briefing.current(categories.orEmpty(), maximum)
    @ExceptionHandler(InvalidWeeklyEconomyBriefingRequestException::class, TypeMismatchException::class)
    fun invalid(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid economy briefing request")
}
