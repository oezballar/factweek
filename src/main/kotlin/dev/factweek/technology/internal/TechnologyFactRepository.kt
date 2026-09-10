package dev.factweek.technology.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

internal interface TechnologyFactRepository : JpaRepository<TechnologyFactEntity, UUID> {
    fun findAllByOccurredOnBetweenOrderByOccurredOnDesc(from: LocalDate, to: LocalDate): List<TechnologyFactEntity>
}
