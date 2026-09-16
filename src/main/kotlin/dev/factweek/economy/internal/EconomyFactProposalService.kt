package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.ingestion.SourceDocuments
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
internal class EconomyFactProposalService(
    private val proposals: EconomyFactProposalRepository,
    private val sourceDocuments: SourceDocuments,
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
            createdAt = clock.instant(), geographyKind = normalized.geography?.kind, geographyName = normalized.geography?.name,
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

    private fun documentUnavailable(id: UUID): RuntimeException = if (sourceDocuments.existsById(id)) EconomyFactProposalConflictException() else EconomyFactProposalNotFoundException()
    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}

internal class InvalidEconomyFactProposalException : RuntimeException()
internal class EconomyFactProposalNotFoundException : RuntimeException()
internal class EconomyFactProposalConflictException : RuntimeException()
