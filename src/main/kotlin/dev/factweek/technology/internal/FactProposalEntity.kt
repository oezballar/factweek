package dev.factweek.technology.internal

import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.TechnologyCategory
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.CollectionTable
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "fact_proposal",
    uniqueConstraints = [
        UniqueConstraint(
            name = "fact_proposal_statement_uk",
            columnNames = ["source_document_id", "statement", "extraction_schema_version"],
        ),
    ],
)
internal class FactProposalEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "source_document_id", nullable = false) val sourceDocumentId: UUID,
    @Column(nullable = false, length = 1000) val statement: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) val category: TechnologyCategory,
    @Column(name = "occurred_on") val occurredOn: LocalDate?,
    @Column(name = "evidence_text", nullable = false, length = 2000) val evidenceText: String,
    @Enumerated(EnumType.STRING) @Column(name = "evidence_level", nullable = false, length = 64) val evidenceLevel: EvidenceLevel,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val status: FactProposalStatus = FactProposalStatus.PROPOSED,
    @Column(name = "extraction_model", nullable = false, length = 255) val extractionModel: String,
    @Column(name = "extraction_schema_version", nullable = false, length = 128) val extractionSchemaVersion: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "rejection_reason", length = 1000) val rejectionReason: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fact_proposal_entity", joinColumns = [JoinColumn(name = "proposal_id")])
    val entities: MutableList<EntityValue> = mutableListOf(),
)
