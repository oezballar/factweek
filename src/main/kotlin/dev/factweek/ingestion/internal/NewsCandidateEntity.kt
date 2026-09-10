package dev.factweek.ingestion.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "news_candidate",
    uniqueConstraints = [
        UniqueConstraint(name = "news_candidate_canonical_url_uk", columnNames = ["canonical_url"]),
    ],
)
internal class NewsCandidateEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "canonical_url", nullable = false, length = 2000)
    val canonicalUrl: String,
    @Column(nullable = false, length = 1000)
    val title: String,
    @Column(name = "source_domain", nullable = false, length = 255)
    val sourceDomain: String,
    @Column(name = "published_at")
    val publishedAt: Instant?,
    @Column(name = "fetched_at", nullable = false)
    val fetchedAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: CandidateStatus = CandidateStatus.DISCOVERED,
)
