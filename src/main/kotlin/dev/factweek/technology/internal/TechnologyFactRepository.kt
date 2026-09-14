package dev.factweek.technology.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

internal interface TechnologyFactRepository : JpaRepository<TechnologyFactEntity, UUID> {
    fun findAllByOccurredOnBetweenOrderByOccurredOnDescIdAsc(from: LocalDate, to: LocalDate): List<TechnologyFactEntity>

    @Query(
        """
        select fact from TechnologyFactEntity fact
        where fact.occurredOn between :from and :to
           or (
                fact.occurredOn is null
            and (select min(source.publishedAt)
                 from TechnologyFactEntity sourceFact join sourceFact.sources source
                 where sourceFact = fact and source.publishedAt is not null) >= :sourceFrom
            and (select min(source.publishedAt)
                 from TechnologyFactEntity sourceFact join sourceFact.sources source
                 where sourceFact = fact and source.publishedAt is not null) < :sourceToExclusive
           )
        """,
    )
    fun findAllRelevantForBriefing(
        from: LocalDate,
        to: LocalDate,
        sourceFrom: Instant,
        sourceToExclusive: Instant,
    ): List<TechnologyFactEntity>
}
