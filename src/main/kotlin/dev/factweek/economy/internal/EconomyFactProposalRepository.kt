package dev.factweek.economy.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import java.util.UUID

internal interface EconomyFactProposalRepository : JpaRepository<EconomyFactProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proposal from EconomyFactProposalEntity proposal where proposal.id = :id")
    fun findForDecision(id: UUID): EconomyFactProposalEntity?
}
