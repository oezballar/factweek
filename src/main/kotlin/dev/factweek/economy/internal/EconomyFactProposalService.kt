package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.ingestion.SourceDocuments
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
internal class EconomyFactProposalService(
    private val proposals: EconomyFactProposalRepository,
    private val sourceDocuments: SourceDocuments,
    private val economyFacts: EconomyFacts,
    private val clock: Clock,
) : EconomyFactProposals {
    @Transactional
    override fun create(command: CreateEconomyFactProposal): EconomyFactProposal {
        val document = sourceDocuments.findFetchedById(command.sourceDocumentId) ?: throw documentUnavailable(command.sourceDocumentId)
        val normalized = EconomyFactPolicy.normalizeAndValidateProposal(command)
        if (!normalize(document.textContent).contains(normalize(normalized.evidenceText))) throw InvalidEconomyFactProposalException()
        val proposal = EconomyFactProposalEntity(
            sourceDocumentId = document.id, statement = normalized.statement, category = normalized.category,
            eventType = normalized.eventType, occurredOn = normalized.occurredOn, evidenceText = normalized.evidenceText,
            createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS), geographyKind = normalized.geography?.kind, geographyName = normalized.geography?.name,
            geographyCode = normalized.geography?.code, entities = normalized.entities.map { EconomyEntityValue(it.name, it.type) }.toMutableList(),
        )
        normalized.measurement?.let { measurement ->
            val period = requireNotNull(normalized.referencePeriod)
            proposal.measurement = EconomyFactProposalMeasurementEntity(
                proposal = proposal, value = measurement.value, unit = measurement.unit, releaseStatus = measurement.releaseStatus,
                seasonalAdjustment = measurement.seasonalAdjustment, valueBasis = measurement.valueBasis,
                referencePeriodFrom = period.from, referencePeriodTo = period.to, referencePeriodGranularity = period.granularity,
            )
        }
        return proposals.save(proposal).toDomain()
    }

    @Transactional(readOnly = true)
    override fun find(id: UUID): EconomyFactProposal? = proposals.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun accept(id: UUID, command: AcceptEconomyFactProposal): EconomyFactProposal {
        val proposal = findProposedForDecision(id)
        val document = sourceDocuments.findFetchedById(proposal.sourceDocumentId)
            ?: throw EconomyFactProposalConflictException()
        val captured = proposal.toDomain()
        val source = SourceReference(
            url = document.sourceUrl,
            publisher = document.publisher,
            sourceType = SourceType.valueOf(document.sourceType.name),
            publishedAt = document.publishedAt,
        )
        val fact = economyFacts.publish(
            PublishEconomyFact(
                statement = captured.statement,
                category = captured.category,
                eventType = captured.eventType,
                evidenceLevel = command.evidenceLevel,
                occurredOn = captured.occurredOn,
                referencePeriod = captured.referencePeriod,
                geography = captured.geography,
                measurement = captured.measurement,
                entities = captured.entities,
                sources = listOf(source) + command.additionalSources,
            ),
        )
        proposal.status = EconomyFactProposalStatus.ACCEPTED
        proposal.reviewedEvidenceLevel = command.evidenceLevel
        proposal.reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
        proposal.economyFactId = fact.id
        return proposal.toDomain()
    }

    @Transactional
    override fun reject(id: UUID, reason: String): EconomyFactProposal {
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty() || normalizedReason.length > 1000) {
            throw InvalidEconomyFactProposalException()
        }
        val proposal = findProposedForDecision(id)
        proposal.status = EconomyFactProposalStatus.REJECTED
        proposal.reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
        proposal.rejectionReason = normalizedReason
        return proposal.toDomain()
    }

    private fun findProposedForDecision(id: UUID): EconomyFactProposalEntity {
        val proposal = proposals.findForDecision(id) ?: throw EconomyFactProposalNotFoundException()
        if (proposal.status != EconomyFactProposalStatus.PROPOSED) throw EconomyFactProposalConflictException()
        return proposal
    }

    private fun documentUnavailable(id: UUID): RuntimeException = if (sourceDocuments.existsById(id)) EconomyFactProposalConflictException() else EconomyFactProposalNotFoundException()
    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}

internal class InvalidEconomyFactProposalException : RuntimeException()
internal class EconomyFactProposalNotFoundException : RuntimeException()
internal class EconomyFactProposalConflictException : RuntimeException()
