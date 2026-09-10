package dev.factweek.ingestion.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface NewsCandidateRepository : JpaRepository<NewsCandidateEntity, UUID> {
    fun findByCanonicalUrl(canonicalUrl: String): NewsCandidateEntity?
    fun countByCanonicalUrl(canonicalUrl: String): Long

    @Query(
        """
        select c from NewsCandidateEntity c
        where not exists (
            select d from SourceDocumentEntity d
            where d.candidate.id = c.id and d.status = :fetchedStatus
        )
        and (
            :retryFailed = true or not exists (
                select d from SourceDocumentEntity d where d.candidate.id = c.id
            )
        )
        order by
            case when exists (
                select d from SourceDocumentEntity d
                where d.candidate.id = c.id and d.status = :failedStatus
            ) then 1 else 0 end,
            c.publishedAt asc nulls last,
            c.id asc
        """,
    )
    fun findForSourceContentRetrieval(
        @Param("retryFailed") retryFailed: Boolean,
        @Param("fetchedStatus") fetchedStatus: SourceDocumentStatus,
        @Param("failedStatus") failedStatus: SourceDocumentStatus,
        pageable: Pageable,
    ): List<NewsCandidateEntity>
}
