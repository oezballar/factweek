package dev.factweek.technology.internal

import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
import dev.factweek.technology.EvidenceLevel
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/technology/fact-proposals")
internal class FactProposalReviewController(
    private val reviews: FactProposalReviewService,
) {
    @PostMapping("/{proposalId}/accept")
    fun accept(
        @PathVariable proposalId: UUID,
        @Valid @RequestBody request: AcceptFactProposalRequest,
    ): AcceptedFactProposalReview = reviews.accept(proposalId, request.toCommand())

    @PostMapping("/{proposalId}/reject")
    fun reject(
        @PathVariable proposalId: UUID,
        @Valid @RequestBody request: RejectFactProposalRequest,
    ): RejectedFactProposalReview = reviews.reject(proposalId, request.reason ?: "")

    @ExceptionHandler(InvalidFactProposalReviewRequestException::class, MethodArgumentNotValidException::class)
    fun invalidRequest(): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "Invalid fact-proposal review request")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableRequestBody(): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "A valid fact-proposal review request body is required")

    @ExceptionHandler(FactProposalNotFoundException::class)
    fun notFound(): ProblemDetail = problem(HttpStatus.NOT_FOUND, "Fact proposal was not found")

    @ExceptionHandler(InvalidReviewedEvidenceDecisionException::class)
    fun invalidEvidenceDecision(): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "The selected evidence level is not allowed for this single-source proposal")
        problem.title = "Evidence decision cannot be accepted"
        return problem
    }

    @ExceptionHandler(
        FactProposalReviewConflictException::class,
        OptimisticLockingFailureException::class,
        DataIntegrityViolationException::class,
    )
    fun conflict(): ProblemDetail = problem(HttpStatus.CONFLICT, "Fact proposal has already been decided")

    private fun problem(status: HttpStatus, detail: String): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail)
}

internal data class AcceptFactProposalRequest(
    @field:NotNull val eventType: TechnologyEventType?,
    @field:NotNull val readiness: TechnologyReadiness?,
    @field:NotNull val evidenceLevel: EvidenceLevel?,
    val occurredOn: LocalDate? = null,
) {
    fun toCommand(): AcceptFactProposal = AcceptFactProposal(
        eventType ?: throw InvalidFactProposalReviewRequestException(),
        readiness ?: throw InvalidFactProposalReviewRequestException(),
        evidenceLevel ?: throw InvalidFactProposalReviewRequestException(),
        occurredOn,
    )
}

internal data class RejectFactProposalRequest(
    @field:NotBlank @field:Size(max = 1000) val reason: String? = null,
)
