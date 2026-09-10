package dev.factweek.ingestion.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface NewsCandidateRepository : JpaRepository<NewsCandidateEntity, UUID> {
    fun findByCanonicalUrl(canonicalUrl: String): NewsCandidateEntity?
    fun countByCanonicalUrl(canonicalUrl: String): Long
}
