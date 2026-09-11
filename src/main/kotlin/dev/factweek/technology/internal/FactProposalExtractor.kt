package dev.factweek.technology.internal

import dev.factweek.technology.EntityReference
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import java.time.LocalDate
import java.util.UUID

/** Provider-neutral application port for the future Spring AI adapter. */
internal interface FactProposalExtractor {
    fun extract(request: FactProposalExtractionRequest): List<ExtractedFactProposal>
}

internal data class FactProposalExtractionRequest(
    val sourceDocumentId: UUID,
    val sourceUrl: String,
    val textContent: String,
    val contentSha256: String,
)

internal data class ExtractedFactProposal(
    val statement: String,
    val category: TechnologyCategory,
    val entities: List<EntityReference>,
    val occurredOn: LocalDate?,
    val evidenceText: String,
    val evidenceLevel: EvidenceLevel,
    val extractionModel: String,
    val extractionSchemaVersion: String,
)
