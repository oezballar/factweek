package dev.factweek.ingestion.internal

import dev.factweek.ingestion.CandidateCaptureCommand
import dev.factweek.ingestion.CandidateCaptureResult
import dev.factweek.ingestion.CandidateCaptures
import dev.factweek.ingestion.CandidateDiscoveryProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

@RestController
@RequestMapping("/api/v1/ingestion/candidates")
internal class ManualCandidateController(
    private val candidateCaptures: CandidateCaptures,
) {
    @PostMapping
    fun capture(@Valid @RequestBody request: ManualCandidateCaptureRequest): ResponseEntity<CandidateCaptureResult> {
        val result = candidateCaptures.capture(request.toCommand())
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result)
    }

    @ExceptionHandler(
        InvalidCandidateCaptureException::class,
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
    )
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid manual candidate request is required")

    @ExceptionHandler(CandidateCaptureConflictException::class)
    fun conflict(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The canonical URL already exists with different candidate metadata")
}

internal data class ManualCandidateCaptureRequest(
    @field:NotBlank val sourceUrl: String?,
    @field:NotBlank val title: String?,
    @field:NotBlank val publisher: String?,
    @field:NotNull val publishedAt: Instant?,
    @field:NotBlank val language: String?,
) {
    fun toCommand(): CandidateCaptureCommand = CandidateCaptureCommand(
        sourceUrl = try {
            URI(sourceUrl)
        } catch (_: IllegalArgumentException) {
            throw InvalidCandidateCaptureException()
        },
        title = title ?: throw InvalidCandidateCaptureException(),
        publisher = publisher ?: throw InvalidCandidateCaptureException(),
        publishedAt = publishedAt,
        language = language,
        discoveryProvider = CandidateDiscoveryProvider.MANUAL,
    )
}
