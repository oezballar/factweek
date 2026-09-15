package dev.factweek.economy.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal interface EconomyFactRepository : JpaRepository<EconomyFactEntity, UUID> {
    @Query("""
        select fact from EconomyFactEntity fact where fact.occurredOn between :from and :to
        or (fact.occurredOn is null and
            (select min(source.publishedAt) from EconomyFactEntity sourceFact join sourceFact.sources source
             where sourceFact = fact and source.publishedAt is not null) >= :sourceFrom and
            (select min(source.publishedAt) from EconomyFactEntity sourceFact join sourceFact.sources source
             where sourceFact = fact and source.publishedAt is not null) < :sourceToExclusive)
    """)
    fun findAllRelevantForBriefing(from: LocalDate, to: LocalDate, sourceFrom: Instant, sourceToExclusive: Instant): List<EconomyFactEntity>
}
