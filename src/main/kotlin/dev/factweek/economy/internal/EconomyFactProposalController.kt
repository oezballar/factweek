package dev.factweek.economy.internal

import dev.factweek.economy.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/economy/fact-proposals")
internal class EconomyFactProposalController(private val proposals: EconomyFactProposals) {
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateEconomyFactProposal): EconomyFactProposal = proposals.create(request)
    @GetMapping("/{id}") fun find(@PathVariable id: UUID): EconomyFactProposal = proposals.find(id) ?: throw EconomyFactProposalNotFoundException()
    @ExceptionHandler(
        InvalidEconomyFactProposalException::class,
        InvalidEconomyFactPublicationException::class,
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun invalid() = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid economy fact proposal")
    @ExceptionHandler(EconomyFactProposalNotFoundException::class)
    fun missing() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Economy fact proposal or source document was not found")
    @ExceptionHandler(EconomyFactProposalConflictException::class)
    fun conflict() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Source document has not been fetched successfully")
}
