package dev.factweek.technology

import java.time.LocalDate
import java.util.UUID

data class TechnologyFact(
    val id: UUID,
    val statement: String,
    val category: TechnologyCategory,
    val eventType: TechnologyEventType,
    val readiness: TechnologyReadiness,
    val evidenceLevel: EvidenceLevel,
    val occurredOn: LocalDate,
    val entities: List<EntityReference>,
    val sources: List<SourceReference>,
)

data class EntityReference(
    val name: String,
    val type: EntityType,
)

data class SourceReference(
    val url: String,
    val publisher: String,
    val sourceType: SourceType,
)

enum class TechnologyCategory {
    AI_AND_SOFTWARE,
    ENERGY_AND_CLIMATE,
    MEDICINE_AND_BIOTECH,
    ROBOTICS_AND_AUTOMATION,
    MATERIALS_AND_MANUFACTURING,
}

enum class TechnologyEventType {
    RESEARCH_RESULT_PUBLISHED,
    PROTOTYPE_DEMONSTRATED,
    CLINICAL_RESULT_PUBLISHED,
    TECHNOLOGY_DEPLOYED,
    REGULATORY_APPROVAL_GRANTED,
    PERFORMANCE_RECORD_VERIFIED,
    OPEN_SOURCE_RELEASED,
    PRODUCTION_STARTED,
}

enum class TechnologyReadiness {
    CONCEPT,
    LAB_RESULT,
    PROTOTYPE,
    CONTROLLED_PILOT,
    REAL_WORLD_PILOT,
    LIMITED_DEPLOYMENT,
    PRODUCTION_USE,
}

enum class EvidenceLevel {
    REPORTED,
    DOCUMENTED,
    PRIMARY_CONFIRMED,
    INDEPENDENTLY_CONFIRMED,
    PROVEN_IN_USE,
}

enum class EntityType { ORGANIZATION, TECHNOLOGY, PRODUCT, RESEARCH_GROUP, PLACE }

enum class SourceType { NEWS_REPORT, PRIMARY_DOCUMENT, PAPER, DATASET, REPOSITORY, REGULATOR }
