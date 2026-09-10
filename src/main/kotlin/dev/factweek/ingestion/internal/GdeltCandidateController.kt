package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidate
import dev.factweek.ingestion.GdeltCandidates
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
internal class GdeltCandidateController(private val candidates: GdeltCandidates) {
    @GetMapping("/api/v1/ingestion/gdelt/candidates")
    fun candidates(
        @RequestParam(defaultValue = "technology") query: String,
        @RequestParam(defaultValue = "25") maximum: Int,
    ): List<GdeltCandidate> = candidates.findTechnologyCandidates(query, maximum)
}
