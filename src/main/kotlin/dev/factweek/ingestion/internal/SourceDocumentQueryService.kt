package dev.factweek.ingestion.internal

import dev.factweek.ingestion.FetchedSourceDocument
import dev.factweek.ingestion.SourceDocuments
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class SourceDocumentQueryService(
    private val repository: SourceDocumentRepository,
) : SourceDocuments {
    override fun findFetchedForFactProposals(
        maximum: Int,
        excludedSourceDocumentIds: Set<UUID>,
    ): List<FetchedSourceDocument> {
        val page = PageRequest.of(0, maximum)
        val documents = if (excludedSourceDocumentIds.isEmpty()) {
            repository.findByStatusOrderByFetchedAtAscCandidateIdAsc(SourceDocumentStatus.FETCHED, page)
        } else {
            repository.findByStatusAndCandidateIdNotInOrderByFetchedAtAscCandidateIdAsc(
                SourceDocumentStatus.FETCHED,
                excludedSourceDocumentIds,
                page,
            )
        }
        return documents.map(::toFetchedSourceDocument)
    }

    override fun findFetchedById(id: UUID): FetchedSourceDocument? =
        repository.findByCandidateIdAndStatus(id, SourceDocumentStatus.FETCHED)?.let(::toFetchedSourceDocument)

    private fun toFetchedSourceDocument(document: SourceDocumentEntity) = FetchedSourceDocument(
        id = requireNotNull(document.candidateId),
        sourceUrl = document.sourceUrl,
        textContent = requireNotNull(document.textContent),
        contentSha256 = requireNotNull(document.contentSha256),
        fetchedAt = requireNotNull(document.fetchedAt),
    )
}
