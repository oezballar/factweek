package dev.factweek.ingestion.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SourceDocumentRepository : JpaRepository<SourceDocumentEntity, UUID>
