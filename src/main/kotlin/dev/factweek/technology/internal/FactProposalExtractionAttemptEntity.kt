package dev.factweek.technology.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fact_proposal_extraction_attempt")
internal class FactProposalExtractionAttemptEntity(
    @Id val id: UUID,
    @Column(name = "source_document_id", nullable = false) val sourceDocumentId: UUID,
    @Column(name = "extraction_model", nullable = false, length = 255) val extractionModel: String,
    @Column(name = "extraction_schema_version", nullable = false, length = 128) val extractionSchemaVersion: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: FactProposalExtractionAttemptStatus,
    @Column(name = "claimed_at", nullable = false) var claimedAt: Instant,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "failure_reason", length = 64) var failureReason: String? = null,
)
