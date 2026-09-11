package dev.factweek.technology.internal

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Database-backed claim for one source document and extraction schema.  The unique
 * constraint makes repeated and concurrent requests deterministic.
 */
internal interface FactProposalExtractionAttemptRepository : Repository<FactProposalEntity, UUID> {
    @Query(
        value = """
            SELECT source_document_id
            FROM fact_proposal_extraction_attempt
            WHERE extraction_schema_version = :schemaVersion
        """,
        nativeQuery = true,
    )
    fun findProcessedSourceDocumentIds(schemaVersion: String): List<UUID>

    @Modifying
    @Query(
        value = """
            INSERT INTO fact_proposal_extraction_attempt
                (id, source_document_id, extraction_model, extraction_schema_version, created_at)
            VALUES (:id, :sourceDocumentId, :model, :schemaVersion, :createdAt)
            ON CONFLICT (source_document_id, extraction_schema_version) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun claim(
        id: UUID,
        sourceDocumentId: UUID,
        model: String,
        schemaVersion: String,
        createdAt: Instant,
    ): Int

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM fact_proposal_extraction_attempt", nativeQuery = true)
    fun deleteAllAttempts(): Int
}
