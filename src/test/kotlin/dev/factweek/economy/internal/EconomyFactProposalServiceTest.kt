package dev.factweek.economy.internal

import dev.factweek.economy.*
import dev.factweek.ingestion.CandidateSourceType
import dev.factweek.ingestion.FetchedSourceDocument
import dev.factweek.ingestion.SourceDocuments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class EconomyFactProposalServiceTest {
    @Test fun `accepts a news document proposal without a final evidence decision`() {
        val repository = repository()
        val document = document("Evidence   passage")
        val service = EconomyFactProposalService(repository, documents(document), clock())
        val proposal = service.create(event(document.id, "Evidence passage"))
        assertEquals(EconomyFactProposalStatus.PROPOSED, proposal.status)
        assertEquals("A policy decision.", proposal.statement)
        assertEquals("Evidence passage", proposal.evidenceText)
    }

    @Test fun `validates indicator structure and source evidence passage`() {
        val document = document("Evidence passage")
        val service = EconomyFactProposalService(repository(), documents(document), clock())
        assertThrows<InvalidEconomyFactPublicationException> { service.create(event(document.id, "Evidence passage", eventType = EconomyEventType.INDICATOR_VALUE_REPORTED)) }
        assertThrows<InvalidEconomyFactProposalException> { service.create(event(document.id, "Missing")) }
        assertThrows<InvalidEconomyFactPublicationException> { service.create(event(document.id, " ")) }
    }

    @Test fun `stores a valid indicator structure`() {
        val document = document("Inflation evidence")
        val service = EconomyFactProposalService(repository(), documents(document), clock())
        val proposal = service.create(event(document.id, "Inflation evidence", eventType = EconomyEventType.INDICATOR_VALUE_REPORTED,
            measurement = EconomyMeasurement(BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT),
            period = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH)))
        assertEquals(BigDecimal("2.4"), proposal.measurement?.value)
        assertEquals(LocalDate.of(2026, 8, 1), proposal.referencePeriod?.from)
    }

    @Test fun `accepts statement and evidence at their boundaries`() {
        val evidence = "e".repeat(2_000)
        val document = document(evidence)
        val service = EconomyFactProposalService(repository(), documents(document), clock())
        assertDoesNotThrow { service.create(event(document.id, evidence, statement = "s".repeat(1_000))) }
    }

    @Test fun `rejects values beyond proposal boundaries`() {
        val document = document("Evidence passage")
        val service = EconomyFactProposalService(repository(), documents(document), clock())
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", statement = "s".repeat(1_001)))
        }
        val longEvidence = "e".repeat(2_001)
        val longDocument = document(longEvidence)
        val longService = EconomyFactProposalService(repository(), documents(longDocument), clock())
        assertThrows<InvalidEconomyFactPublicationException> { longService.create(event(longDocument.id, longEvidence)) }
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", entities = listOf(EconomyEntityReference("x".repeat(256), EconomyEntityType.COMPANY))))
        }
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "x".repeat(256), "DE")))
        }
        val regionCode = "r".repeat(32)
        val normalized = service.create(
            event(
                document.id,
                "Evidence passage",
                geography = EconomyGeography(EconomyGeographyKind.REGION, " Region ", " $regionCode "),
            ),
        )
        assertEquals(regionCode.uppercase(), normalized.geography?.code)
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", geography = EconomyGeography(EconomyGeographyKind.REGION, "Region", "r".repeat(33))))
        }
    }

    @Test fun `rejects invalid measurement precision count and event shape`() {
        val document = document("Evidence passage")
        val service = EconomyFactProposalService(repository(), documents(document), clock())
        val period = EconomyReferencePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), EconomyReferencePeriodGranularity.MONTH)
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", eventType = EconomyEventType.INDICATOR_VALUE_REPORTED, measurement = EconomyMeasurement(BigDecimal("0.12345678901"), EconomyMeasurementUnit.PERCENT), period = period))
        }
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", eventType = EconomyEventType.INDICATOR_VALUE_REPORTED, measurement = EconomyMeasurement(BigDecimal("1.5"), EconomyMeasurementUnit.COUNT), period = period))
        }
        assertThrows<InvalidEconomyFactPublicationException> {
            service.create(event(document.id, "Evidence passage", measurement = EconomyMeasurement(BigDecimal("1"), EconomyMeasurementUnit.PERCENT)))
        }
        assertThrows<InvalidEconomyFactPublicationException> { service.create(event(document.id, "Evidence passage", period = period)) }
    }

    private fun event(
        id: UUID,
        evidence: String,
        statement: String = " A policy decision. ",
        eventType: EconomyEventType = EconomyEventType.MONETARY_POLICY_DECIDED,
        measurement: EconomyMeasurement? = null,
        period: EconomyReferencePeriod? = null,
        geography: EconomyGeography? = null,
        entities: List<EconomyEntityReference> = emptyList(),
    ) = CreateEconomyFactProposal(id, statement, EconomyCategory.MONETARY_POLICY, eventType, geography = geography, entities = entities, measurement = measurement, referencePeriod = period, evidenceText = evidence)
    private fun clock() = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC)
    private fun document(text: String) = FetchedSourceDocument(UUID.randomUUID(), "https://example.org/source", text, "a".repeat(64), Instant.EPOCH, "News", CandidateSourceType.NEWS_REPORT)
    private fun documents(document: FetchedSourceDocument) = object : SourceDocuments {
        override fun existsById(id: UUID) = id == document.id
        override fun findFetchedForFactProposals(maximum: Int, excludedSourceDocumentIds: Set<UUID>) = emptyList<FetchedSourceDocument>()
        override fun findFetchedById(id: UUID) = document.takeIf { it.id == id }
    }
    private fun repository(): EconomyFactProposalRepository = mock(EconomyFactProposalRepository::class.java).also { repository ->
        `when`(repository.save(any(EconomyFactProposalEntity::class.java))).thenAnswer { it.arguments[0] }
    }
}
