package dev.factweek.technology.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface FactProposalRepository : JpaRepository<FactProposalEntity, UUID> {
    fun existsBySourceDocumentId(sourceDocumentId: UUID): Boolean
}
