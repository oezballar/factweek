package dev.factweek.economy.internal

import dev.factweek.economy.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class EconomyFactControllerTest {
    @Test fun `publishes a discrete event`() {
        val mvc = MockMvcBuilders.standaloneSetup(EconomyFactController(RecordingEconomyFacts())).build()
        mvc.perform(post("/api/v1/economy/facts").contentType(MediaType.APPLICATION_JSON).content(eventJson()))
            .andExpect(status().isCreated).andExpect(jsonPath("$.eventType").value("MONETARY_POLICY_DECIDED"))
            .andExpect(jsonPath("$.measurement").doesNotExist())
            .andExpect(jsonPath("$.sources[0].publishedAt").doesNotExist())
    }

    @Test fun `publishes a periodic measurement with numeric value`() {
        val mvc = MockMvcBuilders.standaloneSetup(EconomyFactController(RecordingEconomyFacts())).build()
        mvc.perform(post("/api/v1/economy/facts").contentType(MediaType.APPLICATION_JSON).content(indicatorJson()))
            .andExpect(status().isCreated).andExpect(jsonPath("$.measurement.value").value(2.4))
            .andExpect(jsonPath("$.sources[0].publishedAt").value("2026-09-10T08:00:00Z"))
            .andExpect(jsonPath("$.measurement.releaseStatus").doesNotExist())
            .andExpect(jsonPath("$.measurement.seasonalAdjustment").doesNotExist())
            .andExpect(jsonPath("$.measurement.valueBasis").doesNotExist())
    }

    @Test fun `returns problem detail for invalid combinations`() {
        val mvc = MockMvcBuilders.standaloneSetup(EconomyFactController(RecordingEconomyFacts())).build()
        mvc.perform(post("/api/v1/economy/facts").contentType(MediaType.APPLICATION_JSON).content("""{"statement":"A policy decision.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","evidenceLevel":"PRIMARY_CONFIRMED","sources":[{"url":"https://example.org/report","publisher":"News","sourceType":"NEWS_REPORT"}]}"""))
            .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    private class RecordingEconomyFacts : EconomyFacts {
        override fun publish(command: PublishEconomyFact): EconomyFact {
            val normalized = EconomyFactPolicy.normalizeAndValidate(command)
            return EconomyFact(UUID.randomUUID(), normalized.statement, normalized.category, normalized.eventType,
                normalized.evidenceLevel, normalized.occurredOn, normalized.referencePeriod, normalized.geography, normalized.measurement, normalized.entities, normalized.sources)
        }
        override fun relevantForBriefingBetween(from: java.time.LocalDate, to: java.time.LocalDate): List<EconomyFact> = emptyList()
    }
    private fun eventJson() = """{"statement":"A central bank changed its policy rate.","category":"MONETARY_POLICY","eventType":"MONETARY_POLICY_DECIDED","evidenceLevel":"PRIMARY_CONFIRMED","occurredOn":"2026-09-10","sources":[{"url":"https://example.org/source","publisher":"Example","sourceType":"PRIMARY_DOCUMENT"}]}"""
    private fun indicatorJson() = """{"statement":"Inflation was reported for August 2026.","category":"PRICES_AND_INFLATION","eventType":"INDICATOR_VALUE_REPORTED","evidenceLevel":"PRIMARY_CONFIRMED","referencePeriod":{"from":"2026-08-01","to":"2026-08-31","granularity":"MONTH"},"measurement":{"value":2.4,"unit":"PERCENT"},"sources":[{"url":"https://example.org/source","publisher":"Example","sourceType":"PRIMARY_DOCUMENT","publishedAt":"2026-09-10T08:00:00Z"}]}"""
}
