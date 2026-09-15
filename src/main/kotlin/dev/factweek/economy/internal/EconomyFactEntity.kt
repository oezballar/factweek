package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import jakarta.persistence.*
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "economy_fact")
internal class EconomyFactEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 1000) val statement: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val category: EconomyCategory,
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) val eventType: EconomyEventType,
    @Enumerated(EnumType.STRING) @Column(name = "evidence_level", nullable = false) val evidenceLevel: EconomyEvidenceLevel,
    @Column(name = "occurred_on") val occurredOn: LocalDate?,
    @Enumerated(EnumType.STRING) @Column(name = "geography_kind") val geographyKind: EconomyGeographyKind? = null,
    @Column(name = "geography_name") val geographyName: String? = null,
    @Column(name = "geography_code") val geographyCode: String? = null,
    @ElementCollection(fetch = FetchType.EAGER) @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "economy_fact_entity", joinColumns = [JoinColumn(name = "fact_id")])
    val entities: MutableList<EconomyEntityValue> = mutableListOf(),
    @ElementCollection(fetch = FetchType.EAGER) @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "economy_fact_source", joinColumns = [JoinColumn(name = "fact_id")])
    val sources: MutableList<EconomySourceValue> = mutableListOf(),
) {
    @OneToOne(mappedBy = "fact", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var measurement: EconomyMeasurementEntity? = null

    fun toDomain() = EconomyFact(
        id, statement, category, eventType, evidenceLevel, occurredOn,
        measurement?.toReferencePeriod(),
        geographyKind?.let { EconomyGeography(it, requireNotNull(geographyName), geographyCode) },
        measurement?.toMeasurement(),
        entities.map { EconomyEntityReference(it.name, it.type) },
        sources.map { SourceReference(it.url, it.publisher, it.sourceType, it.publishedAt) },
    )
}

@Embeddable
internal data class EconomyEntityValue(
    @Column(name = "entity_name", nullable = false) val name: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "entity_type", nullable = false) val type: EconomyEntityType = EconomyEntityType.INDICATOR,
)

@Embeddable
internal data class EconomySourceValue(
    @Column(name = "source_url", nullable = false, length = 2000) val url: String = "",
    @Column(name = "source_publisher", nullable = false) val publisher: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) val sourceType: SourceType = SourceType.NEWS_REPORT,
    @Column(name = "source_published_at") val publishedAt: Instant? = null,
)

@Entity
@Table(name = "economy_fact_measurement")
internal class EconomyMeasurementEntity(
    @Id @Column(name = "fact_id") var factId: UUID? = null,
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fact_id") var fact: EconomyFactEntity,
    @Column(name = "measurement_value", nullable = false, precision = 30, scale = 10) val value: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(name = "measurement_unit", nullable = false) val unit: EconomyMeasurementUnit,
    @Enumerated(EnumType.STRING) @Column(name = "release_status") val releaseStatus: EconomyReleaseStatus? = null,
    @Enumerated(EnumType.STRING) @Column(name = "seasonal_adjustment") val seasonalAdjustment: EconomySeasonalAdjustment? = null,
    @Enumerated(EnumType.STRING) @Column(name = "value_basis") val valueBasis: EconomyValueBasis? = null,
    @Column(name = "reference_period_from", nullable = false) val referencePeriodFrom: LocalDate,
    @Column(name = "reference_period_to", nullable = false) val referencePeriodTo: LocalDate,
    @Enumerated(EnumType.STRING) @Column(name = "reference_period_granularity", nullable = false)
    val referencePeriodGranularity: EconomyReferencePeriodGranularity,
) {
    fun toMeasurement() = EconomyMeasurement(value, unit, releaseStatus, seasonalAdjustment, valueBasis)
    fun toReferencePeriod() = EconomyReferencePeriod(referencePeriodFrom, referencePeriodTo, referencePeriodGranularity)
}
