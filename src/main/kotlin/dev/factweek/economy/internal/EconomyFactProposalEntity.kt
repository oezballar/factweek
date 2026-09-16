package dev.factweek.economy.internal

import dev.factweek.economy.*
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "economy_fact_proposal")
internal class EconomyFactProposalEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "source_document_id", nullable = false) val sourceDocumentId: UUID,
    @Column(nullable = false, length = 1000) val statement: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val category: EconomyCategory,
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) val eventType: EconomyEventType,
    @Column(name = "occurred_on") val occurredOn: LocalDate?,
    @Column(name = "evidence_text", nullable = false, length = 2000) val evidenceText: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: EconomyFactProposalStatus = EconomyFactProposalStatus.PROPOSED,
    @Enumerated(EnumType.STRING) @Column(name = "reviewed_evidence_level") var reviewedEvidenceLevel: EconomyEvidenceLevel? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "rejection_reason") var rejectionReason: String? = null,
    @Column(name = "economy_fact_id") var economyFactId: UUID? = null,
    @Version @Column(nullable = false) var version: Long = 0,
    @Enumerated(EnumType.STRING) @Column(name = "geography_kind") val geographyKind: EconomyGeographyKind? = null,
    @Column(name = "geography_name", length = 255) val geographyName: String? = null,
    @Column(name = "geography_code", length = 32) val geographyCode: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "economy_fact_proposal_entity", joinColumns = [JoinColumn(name = "proposal_id")])
    val entities: MutableList<EconomyEntityValue> = mutableListOf(),
) {
    @OneToOne(mappedBy = "proposal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var measurement: EconomyFactProposalMeasurementEntity? = null

    fun toDomain() = EconomyFactProposal(id, sourceDocumentId, statement, category, eventType, occurredOn,
        measurement?.toReferencePeriod(), geographyKind?.let { EconomyGeography(it, requireNotNull(geographyName), geographyCode) },
        measurement?.toMeasurement(), entities.map { EconomyEntityReference(it.name, it.type) }, evidenceText, createdAt,
        status, reviewedEvidenceLevel, reviewedAt, rejectionReason, economyFactId)
}

@Entity
@Table(name = "economy_fact_proposal_measurement")
internal class EconomyFactProposalMeasurementEntity(
    @Id @Column(name = "proposal_id") var proposalId: UUID? = null,
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id") var proposal: EconomyFactProposalEntity,
    @Column(name = "measurement_value", nullable = false, columnDefinition = "NUMERIC") val value: java.math.BigDecimal,
    @Enumerated(EnumType.STRING) @Column(name = "measurement_unit", nullable = false) val unit: EconomyMeasurementUnit,
    @Enumerated(EnumType.STRING) @Column(name = "release_status") val releaseStatus: EconomyReleaseStatus? = null,
    @Enumerated(EnumType.STRING) @Column(name = "seasonal_adjustment") val seasonalAdjustment: EconomySeasonalAdjustment? = null,
    @Enumerated(EnumType.STRING) @Column(name = "value_basis") val valueBasis: EconomyValueBasis? = null,
    @Column(name = "reference_period_from", nullable = false) val referencePeriodFrom: LocalDate,
    @Column(name = "reference_period_to", nullable = false) val referencePeriodTo: LocalDate,
    @Enumerated(EnumType.STRING) @Column(name = "reference_period_granularity", nullable = false) val referencePeriodGranularity: EconomyReferencePeriodGranularity,
) {
    fun toMeasurement() = EconomyMeasurement(value, unit, releaseStatus, seasonalAdjustment, valueBasis)
    fun toReferencePeriod() = EconomyReferencePeriod(referencePeriodFrom, referencePeriodTo, referencePeriodGranularity)
}
