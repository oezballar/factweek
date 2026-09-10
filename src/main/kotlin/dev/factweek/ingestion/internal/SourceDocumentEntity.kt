package dev.factweek.ingestion.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "source_document")
internal class SourceDocumentEntity(
    @Id
    @Column(name = "candidate_id")
    var candidateId: UUID? = null,
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    var candidate: NewsCandidateEntity,
    @Column(name = "source_url", nullable = false, length = 2000)
    var sourceUrl: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SourceDocumentStatus,
    @Column(name = "media_type", length = 255)
    var mediaType: String? = null,
    @Column(name = "http_status")
    var httpStatus: Int? = null,
    @Column(name = "text_content", columnDefinition = "TEXT")
    var textContent: String? = null,
    @Column(name = "content_sha256", length = 64)
    var contentSha256: String? = null,
    @Column(name = "fetched_at")
    var fetchedAt: Instant? = null,
    @Column(name = "last_attempt_at", nullable = false)
    var lastAttemptAt: Instant,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    var failureReason: SourceContentFailureReason? = null,
)
