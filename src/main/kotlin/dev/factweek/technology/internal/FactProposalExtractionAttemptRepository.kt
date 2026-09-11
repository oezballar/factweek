package dev.factweek.technology.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

internal interface FactProposalExtractionAttemptRepository : JpaRepository<FactProposalExtractionAttemptEntity, UUID> {
    @Query(
        value = """
            SELECT source_document_id
            FROM fact_proposal_extraction_attempt
            WHERE extraction_schema_version = :schemaVersion
              AND (
                status = 'COMPLETED'
                OR (status = 'CLAIMED' AND claimed_at >= :leaseExpiry)
              )
        """,
        nativeQuery = true,
    )
    fun findNonClaimableSourceDocumentIds(schemaVersion: String, leaseExpiry: Instant): List<UUID>

    @Modifying
    @Query(
        value = """
            INSERT INTO fact_proposal_extraction_attempt
                (id, source_document_id, extraction_model, extraction_schema_version, created_at, status, claimed_at, completed_at, failure_reason)
            VALUES (:id, :sourceDocumentId, :model, :schemaVersion, :claimedAt, 'CLAIMED', :claimedAt, NULL, NULL)
            ON CONFLICT (source_document_id, extraction_schema_version) DO UPDATE
            SET id = EXCLUDED.id,
                extraction_model = EXCLUDED.extraction_model,
                status = 'CLAIMED',
                claimed_at = EXCLUDED.claimed_at,
                completed_at = NULL,
                failure_reason = NULL
            WHERE fact_proposal_extraction_attempt.status = 'FAILED'
               OR (
                    fact_proposal_extraction_attempt.status = 'CLAIMED'
                    AND fact_proposal_extraction_attempt.claimed_at < :leaseExpiry
               )
        """,
        nativeQuery = true,
    )
    fun claim(
        id: UUID,
        sourceDocumentId: UUID,
        model: String,
        schemaVersion: String,
        claimedAt: Instant,
        leaseExpiry: Instant,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE FactProposalExtractionAttemptEntity attempt
        SET attempt.status = dev.factweek.technology.internal.FactProposalExtractionAttemptStatus.COMPLETED,
            attempt.completedAt = :completedAt,
            attempt.failureReason = NULL
        WHERE attempt.id = :id AND attempt.status = dev.factweek.technology.internal.FactProposalExtractionAttemptStatus.CLAIMED
        """,
    )
    fun complete(id: UUID, completedAt: Instant): Int

    @Modifying
    @Query(
        """
        UPDATE FactProposalExtractionAttemptEntity attempt
        SET attempt.status = dev.factweek.technology.internal.FactProposalExtractionAttemptStatus.FAILED,
            attempt.completedAt = :completedAt,
            attempt.failureReason = :failureReason
        WHERE attempt.id = :id AND attempt.status = dev.factweek.technology.internal.FactProposalExtractionAttemptStatus.CLAIMED
        """,
    )
    fun fail(id: UUID, completedAt: Instant, failureReason: String): Int
}
