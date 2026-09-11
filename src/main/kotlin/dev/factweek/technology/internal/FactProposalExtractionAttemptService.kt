package dev.factweek.technology.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Owns short transactions for claims and attempt state changes. */
@Service
internal class FactProposalExtractionAttemptService(
    private val attempts: FactProposalExtractionAttemptRepository,
    private val proposals: FactProposalRepository,
    private val clock: Clock,
    @Value("\${factweek.fact-proposals.claim-lease:15m}") private val claimLease: Duration,
) {
    init {
        require(!claimLease.isNegative && !claimLease.isZero) { "Fact-proposal claim lease must be positive" }
    }

    @Transactional(readOnly = true)
    fun findNonClaimableSourceDocumentIds(schemaVersion: String): Set<UUID> {
        val leaseExpiry = clock.instant().minus(claimLease)
        return attempts.findNonClaimableSourceDocumentIds(schemaVersion, leaseExpiry).toSet()
    }

    @Transactional
    fun tryClaim(sourceDocumentId: UUID, metadata: FactProposalExtractionMetadata): UUID? {
        val now = clock.instant()
        val id = UUID.randomUUID()
        return if (attempts.claim(id, sourceDocumentId, metadata.model, metadata.schemaVersion, now, now.minus(claimLease)) == 1) id else null
    }

    @Transactional
    fun complete(attemptId: UUID, proposalsToStore: List<FactProposalEntity>) {
        proposals.saveAll(proposalsToStore)
        check(attempts.complete(attemptId, clock.instant()) == 1) { "Fact-proposal extraction attempt is no longer claimed" }
    }

    @Transactional
    fun markFailed(attemptId: UUID) {
        attempts.fail(attemptId, clock.instant(), EXTRACTOR_FAILURE_REASON)
    }

    private companion object {
        const val EXTRACTOR_FAILURE_REASON = "EXTRACTOR_ERROR"
    }
}
