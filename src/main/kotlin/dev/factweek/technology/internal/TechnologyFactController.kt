package dev.factweek.technology.internal

import dev.factweek.technology.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/technology/facts")
internal class TechnologyFactController(private val technologyFacts: TechnologyFacts) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(@Valid @RequestBody request: PublishTechnologyFactRequest): TechnologyFact =
        technologyFacts.publish(request.toCommand())
}

internal data class PublishTechnologyFactRequest(
    @field:NotBlank val statement: String,
    val category: TechnologyCategory,
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val evidenceLevel: EvidenceLevel,
    val occurredOn: LocalDate,
    val entities: List<EntityReference> = emptyList(),
    @field:NotEmpty val sources: List<SourceReference>,
) {
    fun toCommand() = PublishTechnologyFact(
        statement, category, eventType, readiness, evidenceLevel, occurredOn, entities, sources,
    )
}
