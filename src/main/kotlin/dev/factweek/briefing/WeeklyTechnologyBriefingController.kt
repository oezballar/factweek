package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyFact
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
    ): List<TechnologyFact> = briefing.current(categories.orEmpty())
}
