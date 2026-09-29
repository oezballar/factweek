package dev.factweek.economy.internal

import dev.factweek.economy.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import dev.factweek.provenance.SourceReference
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
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
    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: UUID, @Valid @RequestBody request: AcceptEconomyFactProposalRequest): EconomyFactProposal =
        proposals.accept(id, AcceptEconomyFactProposal(
            evidenceLevel = requireNotNull(request.evidenceLevel),
            additionalSources = request.additionalSources,
        ))

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: UUID, @Valid @RequestBody request: RejectEconomyFactProposalRequest): EconomyFactProposal =
        proposals.reject(id, request.reason ?: "")

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
    @ExceptionHandler(
        EconomyFactProposalConflictException::class,
        OptimisticLockingFailureException::class,
        PessimisticLockingFailureException::class,
    )
    fun conflict() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Economy fact proposal has already been decided or its source document is unavailable")
}

internal data class AcceptEconomyFactProposalRequest(
    @field:NotNull val evidenceLevel: EconomyEvidenceLevel?,
    val additionalSources: List<SourceReference> = emptyList(),
)

internal data class RejectEconomyFactProposalRequest(
    @field:NotBlank val reason: String? = null,
)
