package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/economy/facts")
internal class EconomyFactController(private val economyFacts: EconomyFacts) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(@Valid @RequestBody request: PublishEconomyFactRequest): EconomyFact = economyFacts.publish(request.toCommand())

    @ExceptionHandler(InvalidEconomyFactPublicationException::class, MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid economy fact publication request")
}

internal data class PublishEconomyFactRequest(
    @field:NotBlank val statement: String,
    val category: EconomyCategory,
    val eventType: EconomyEventType,
    val evidenceLevel: EconomyEvidenceLevel,
    val occurredOn: LocalDate? = null,
    val referencePeriod: EconomyReferencePeriod? = null,
    val geography: EconomyGeography? = null,
    val measurement: EconomyMeasurement? = null,
    val entities: List<EconomyEntityReference> = emptyList(),
    @field:NotEmpty val sources: List<SourceReference>,
) {
    fun toCommand() = PublishEconomyFact(
        statement, category, eventType, evidenceLevel, occurredOn, referencePeriod, geography, measurement, entities, sources,
    )
}
