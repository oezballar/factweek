package dev.factweek.economy.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface EconomyFactRepository : JpaRepository<EconomyFactEntity, UUID>
