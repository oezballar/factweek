package dev.factweek.technology.internal

import dev.factweek.technology.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import jakarta.persistence.*
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "technology_fact")
internal class TechnologyFactEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 1000) val statement: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val category: TechnologyCategory,
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) val eventType: TechnologyEventType,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val readiness: TechnologyReadiness,
    @Enumerated(EnumType.STRING) @Column(name = "evidence_level", nullable = false) val evidenceLevel: EvidenceLevel,
    @Column(name = "occurred_on") val occurredOn: LocalDate?,
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "technology_fact_entity", joinColumns = [JoinColumn(name = "fact_id")])
    val entities: MutableList<EntityValue> = mutableListOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "technology_fact_source", joinColumns = [JoinColumn(name = "fact_id")])
    val sources: MutableList<SourceValue> = mutableListOf(),
) {
    fun toDomain() = TechnologyFact(
        id, statement, category, eventType, readiness, evidenceLevel, occurredOn,
        entities.map { EntityReference(it.name, it.type) },
        sources.map { SourceReference(it.url, it.publisher, it.sourceType, it.publishedAt) },
    )
}

@Embeddable
internal data class EntityValue(
    @Column(name = "entity_name", nullable = false) val name: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "entity_type", nullable = false) val type: EntityType = EntityType.TECHNOLOGY,
)

@Embeddable
internal data class SourceValue(
    @Column(name = "source_url", nullable = false, length = 2000) val url: String = "",
    @Column(name = "source_publisher", nullable = false) val publisher: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) val sourceType: SourceType = SourceType.NEWS_REPORT,
    @Column(name = "source_published_at") val publishedAt: java.time.Instant? = null,
)
