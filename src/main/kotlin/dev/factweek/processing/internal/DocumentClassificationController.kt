package dev.factweek.processing.internal

import dev.factweek.processing.DocumentClassification
import dev.factweek.processing.DocumentClassifications
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.TypeMismatchException
import java.util.UUID

@RestController
internal class DocumentClassificationController(
    private val classifications: DocumentClassifications,
) {
    @PostMapping("/api/v1/processing/documents/{documentId}/classification")
    fun classify(@PathVariable documentId: UUID): DocumentClassification = classifications.classify(documentId)

    @GetMapping("/api/v1/processing/documents/{documentId}/classification")
    fun find(@PathVariable documentId: UUID): DocumentClassification =
        classifications.find(documentId) ?: throw DocumentClassificationNotFoundException()

    @ExceptionHandler(SourceDocumentNotFoundException::class, DocumentClassificationNotFoundException::class)
    fun notFound(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Source document or classification was not found")

    @ExceptionHandler(SourceDocumentNotFetchedException::class)
    fun notFetched(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Source document has not been fetched successfully")

    @ExceptionHandler(ArticleSectionClassificationException::class)
    fun classificationFailure(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Article classification failed")

    @ExceptionHandler(ArticleSectionClassifierUnavailableException::class)
    fun classifierUnavailable(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Article section classifier is not available")

    @ExceptionHandler(TypeMismatchException::class)
    fun invalidPath(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid document id is required")
}

internal class DocumentClassificationNotFoundException : RuntimeException()
