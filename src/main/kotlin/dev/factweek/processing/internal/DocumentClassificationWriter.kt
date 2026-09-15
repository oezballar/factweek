package dev.factweek.processing.internal

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class DocumentClassificationWriter(
    private val entityManager: EntityManager,
) {
    /** Creates a classification once; it deliberately never merges or updates an existing result. */
    @Transactional
    fun insert(entity: DocumentSectionClassificationEntity) {
        entityManager.persist(entity)
        entityManager.flush()
    }
}
