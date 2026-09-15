package dev.factweek.briefing

import dev.factweek.economy.*
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WeeklyEconomyBriefingControllerTest {
 @Test fun `renders a flat source dated indicator`() {
  val fact = BriefingEconomyFact(UUID.randomUUID(), "Inflation", EconomyCategory.PRICES_AND_INFLATION, EconomyEventType.INDICATOR_VALUE_REPORTED, EconomyEvidenceLevel.PRIMARY_CONFIRMED, null, EconomyReferencePeriod(LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),EconomyReferencePeriodGranularity.MONTH), null, EconomyMeasurement(java.math.BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT), emptyList(), listOf(SourceReference("https://x","Authority",SourceType.PRIMARY_DOCUMENT,Instant.parse("2026-09-10T08:00:00Z"))), LocalDate.of(2026,9,10), BriefingReferenceBasis.SOURCE_PUBLISHED_AT)
  val service = org.mockito.Mockito.mock(WeeklyEconomyBriefing::class.java)
  org.mockito.Mockito.`when`(service.current(emptySet(),10)).thenReturn(CurrentEconomyBriefing(LocalDate.of(2026,9,7),LocalDate.of(2026,9,13),Instant.parse("2026-09-13T12:00:00Z"),EconomyCategory.entries.toList(),10,1,listOf(fact)))
  MockMvcBuilders.standaloneSetup(WeeklyEconomyBriefingController(service)).build().perform(get("/api/v1/briefings/economy/current"))
   .andExpect(status().isOk).andExpect(jsonPath("$.factCount").value(1)).andExpect(jsonPath("$.facts[0].statement").value("Inflation")).andExpect(jsonPath("$.facts[0].fact").doesNotExist()).andExpect(jsonPath("$.facts[0].occurredOn").doesNotExist()).andExpect(jsonPath("$.facts[0].referencePeriod.granularity").value("MONTH")).andExpect(jsonPath("$.facts[0].referenceDateBasis").value("SOURCE_PUBLISHED_AT"))
 }
}
