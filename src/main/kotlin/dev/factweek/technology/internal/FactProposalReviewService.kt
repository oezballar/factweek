package dev.factweek.technology.internal

import dev.factweek.ingestion.SourceDocuments
import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.technology.SourceType
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
internal class FactProposalReviewService(
    private val proposals: FactProposalRepository,
    private val technologyFacts: TechnologyFactRepository,
    private val sourceDocuments: SourceDocuments,
    private val clock: Clock,
) {
    @Transactional
    fun accept(proposalId: UUID, command: AcceptFactProposal): AcceptedFactProposalReview {
        val proposal = findProposed(proposalId)
        val sourceDocument = sourceDocuments.findFetchedById(proposal.sourceDocumentId)
            ?: throw FactProposalReviewConflictException()
        val occurredOn = proposal.occurredOn ?: command.occurredOn
            ?: throw InvalidFactProposalReviewRequestException()
        val technologyFact = technologyFacts.save(
            TechnologyFactEntity(
                statement = proposal.statement,
                category = proposal.category,
                eventType = command.eventType,
                readiness = command.readiness,
                evidenceLevel = proposal.evidenceLevel,
                occurredOn = occurredOn,
                entities = proposal.entities.map { EntityValue(it.name, it.type) }.toMutableList(),
                sources = mutableListOf(
                    SourceValue(
                        sourceDocument.sourceUrl,
                        sourceDocument.publisher,
                        sourceDocument.sourceType.toTechnologySourceType(),
                    ),
                ),
            ),
        )
        val reviewedAt = clock.instant()
        proposal.status = FactProposalStatus.ACCEPTED
        proposal.technologyFactId = technologyFact.id
        proposal.reviewedAt = reviewedAt
        proposal.rejectionReason = null
        return AcceptedFactProposalReview(proposal.id, proposal.status, technologyFact.id, reviewedAt)
    }

    @Transactional
    fun reject(proposalId: UUID, reason: String): RejectedFactProposalReview {
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty() || normalizedReason.length > MAX_REJECTION_REASON_LENGTH) {
            throw InvalidFactProposalReviewRequestException()
        }
        val proposal = findProposed(proposalId)
        val reviewedAt = clock.instant()
        proposal.status = FactProposalStatus.REJECTED
        proposal.rejectionReason = normalizedReason
        proposal.reviewedAt = reviewedAt
        return RejectedFactProposalReview(proposal.id, proposal.status, normalizedReason, reviewedAt)
    }

    private fun findProposed(proposalId: UUID): FactProposalEntity {
        val proposal = proposals.findById(proposalId).orElseThrow(::FactProposalNotFoundException)
        if (proposal.status != FactProposalStatus.PROPOSED) throw FactProposalReviewConflictException()
        return proposal
    }

    private fun CandidateSourceType.toTechnologySourceType(): SourceType = when (this) {
        CandidateSourceType.NEWS_REPORT -> SourceType.NEWS_REPORT
        CandidateSourceType.PRIMARY_DOCUMENT -> SourceType.PRIMARY_DOCUMENT
        CandidateSourceType.PAPER -> SourceType.PAPER
        CandidateSourceType.DATASET -> SourceType.DATASET
        CandidateSourceType.REPOSITORY -> SourceType.REPOSITORY
        CandidateSourceType.REGULATOR -> SourceType.REGULATOR
    }

    private companion object {
        const val MAX_REJECTION_REASON_LENGTH = 1000
    }
}

internal data class AcceptFactProposal(
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val occurredOn: LocalDate? = null,
)

internal data class AcceptedFactProposalReview(
    val proposalId: UUID,
    val proposalStatus: FactProposalStatus,
    val technologyFactId: UUID,
    val reviewedAt: java.time.Instant,
)

internal data class RejectedFactProposalReview(
    val proposalId: UUID,
    val proposalStatus: FactProposalStatus,
    val rejectionReason: String,
    val reviewedAt: java.time.Instant,
)

internal class FactProposalNotFoundException : RuntimeException()
internal class FactProposalReviewConflictException : RuntimeException()
internal class InvalidFactProposalReviewRequestException : RuntimeException()
