package dev.factweek.ingestion.internal

import dev.factweek.ingestion.FetchedSourceDocument
import dev.factweek.ingestion.SourceDocuments
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
internal class SourceDocumentQueryService(
    private val repository: SourceDocumentRepository,
) : SourceDocuments {
    override fun findFetchedForFactProposals(maximum: Int): List<FetchedSourceDocument> =
        repository.findByStatusOrderByFetchedAtAscCandidateIdAsc(
            SourceDocumentStatus.FETCHED,
            PageRequest.of(0, maximum),
        ).map { document ->
            FetchedSourceDocument(
                id = requireNotNull(document.candidateId),
                sourceUrl = document.sourceUrl,
                textContent = requireNotNull(document.textContent),
                contentSha256 = requireNotNull(document.contentSha256),
                fetchedAt = requireNotNull(document.fetchedAt),
            )
        }
}
