package dev.factweek.ingestion.internal

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
internal class CandidateWriter(
    private val repository: NewsCandidateRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insert(candidate: NewsCandidateEntity): NewsCandidateEntity = repository.saveAndFlush(candidate)
}
