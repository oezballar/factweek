package dev.factweek.ingestion.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import java.util.UUID

internal interface SourceDocumentRepository : JpaRepository<SourceDocumentEntity, UUID> {
    fun findByStatusOrderByFetchedAtAscCandidateIdAsc(status: SourceDocumentStatus, pageable: Pageable): List<SourceDocumentEntity>
}
