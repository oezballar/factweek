package dev.factweek.briefing

import dev.factweek.economy.EconomyCategory
import dev.factweek.economy.EconomyEntityReference
import dev.factweek.economy.EconomyEntityType
import dev.factweek.economy.EconomyEvidenceLevel
import dev.factweek.economy.EconomyEventType
import dev.factweek.economy.EconomyGeography
import dev.factweek.economy.EconomyGeographyKind
import dev.factweek.economy.EconomyMeasurement
import dev.factweek.economy.EconomyMeasurementUnit
import dev.factweek.economy.EconomyReferencePeriod
import dev.factweek.economy.EconomyReferencePeriodGranularity
import dev.factweek.provenance.SourceReference
import dev.factweek.provenance.SourceType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WeeklyEconomyBriefingControllerTest {
    private lateinit var briefing: WeeklyEconomyBriefing
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        briefing = mock(WeeklyEconomyBriefing::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(WeeklyEconomyBriefingController(briefing)).build()
    }

    @Test
    fun `uses default maximum and exposes a flat source dated indicator`() {
        `when`(briefing.current(emptySet(), 10)).thenReturn(response(facts = listOf(sourceDatedFact())))

        mockMvc.perform(get("/api/v1/briefings/economy/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedMaximum").value(10))
            .andExpect(jsonPath("$.factCount").value(1))
            .andExpect(jsonPath("$.facts[0].statement").value("Inflation was reported."))
            .andExpect(jsonPath("$.facts[0].category").value("PRICES_AND_INFLATION"))
            .andExpect(jsonPath("$.facts[0].eventType").value("INDICATOR_VALUE_REPORTED"))
            .andExpect(jsonPath("$.facts[0].evidenceLevel").value("PRIMARY_CONFIRMED"))
            .andExpect(jsonPath("$.facts[0].occurredOn").doesNotExist())
            .andExpect(jsonPath("$.facts[0].referencePeriod.granularity").value("MONTH"))
            .andExpect(jsonPath("$.facts[0].measurement.value").value(2.4))
            .andExpect(jsonPath("$.facts[0].geography.code").value("DE"))
            .andExpect(jsonPath("$.facts[0].entities[0].name").value("Inflation"))
            .andExpect(jsonPath("$.facts[0].sources[0].publishedAt").value("2026-09-10T08:00:00Z"))
            .andExpect(jsonPath("$.facts[0].referenceDate").value("2026-09-10"))
            .andExpect(jsonPath("$.facts[0].referenceDateBasis").value("SOURCE_PUBLISHED_AT"))
            .andExpect(jsonPath("$.facts[0].fact").doesNotExist())
            .andExpect(jsonPath("$.facts[0].headline").doesNotExist())
            .andExpect(jsonPath("$.facts[0].summary").doesNotExist())
            .andExpect(jsonPath("$.facts[0].narrative").doesNotExist())
            .andExpect(jsonPath("$.facts[0].interpretation").doesNotExist())
            .andExpect(jsonPath("$.facts[0].forecast").doesNotExist())
            .andExpect(jsonPath("$.facts[0].recommendation").doesNotExist())

        verify(briefing).current(emptySet(), 10)
    }

    @Test
    fun `supports repeated categories custom maximum and occurred references`() {
        val categories = setOf(EconomyCategory.MONETARY_POLICY, EconomyCategory.PRICES_AND_INFLATION)
        `when`(briefing.current(categories, 3)).thenReturn(
            response(
                categories = categories.sortedBy { it.name },
                requestedMaximum = 3,
                facts = listOf(occurredFact()),
            ),
        )

        mockMvc.perform(
            get("/api/v1/briefings/economy/current")
                .param("categories", "MONETARY_POLICY", "PRICES_AND_INFLATION")
                .param("maximum", "3"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedMaximum").value(3))
            .andExpect(jsonPath("$.appliedCategories[0]").value("MONETARY_POLICY"))
            .andExpect(jsonPath("$.appliedCategories[1]").value("PRICES_AND_INFLATION"))
            .andExpect(jsonPath("$.facts[0].occurredOn").value("2026-09-11"))
            .andExpect(jsonPath("$.facts[0].referenceDateBasis").value("OCCURRED_ON"))

        verify(briefing).current(categories, 3)
    }

    @Test
    fun `returns problem details for invalid maximum and category`() {
        listOf("0", "51").forEach { maximum ->
            mockMvc.perform(get("/api/v1/briefings/economy/current").param("maximum", maximum))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid economy briefing request"))
        }

        mockMvc.perform(get("/api/v1/briefings/economy/current").param("categories", "UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid economy briefing request"))
    }

    private fun response(
        categories: List<EconomyCategory> = EconomyCategory.entries.sortedBy { it.name },
        requestedMaximum: Int = 10,
        facts: List<BriefingEconomyFact> = emptyList(),
    ): CurrentEconomyBriefing = CurrentEconomyBriefing(
        from = LocalDate.of(2026, 9, 7),
        to = LocalDate.of(2026, 9, 13),
        generatedAt = Instant.parse("2026-09-13T12:00:00Z"),
        appliedCategories = categories,
        requestedMaximum = requestedMaximum,
        factCount = facts.size,
        facts = facts,
    )

    private fun sourceDatedFact(): BriefingEconomyFact = briefingFact(
        occurredOn = null,
        referenceDate = LocalDate.of(2026, 9, 10),
        referenceDateBasis = BriefingReferenceBasis.SOURCE_PUBLISHED_AT,
    )

    private fun occurredFact(): BriefingEconomyFact = briefingFact(
        occurredOn = LocalDate.of(2026, 9, 11),
        referenceDate = LocalDate.of(2026, 9, 11),
        referenceDateBasis = BriefingReferenceBasis.OCCURRED_ON,
    )

    private fun briefingFact(
        occurredOn: LocalDate?,
        referenceDate: LocalDate,
        referenceDateBasis: BriefingReferenceBasis,
    ): BriefingEconomyFact = BriefingEconomyFact(
        id = UUID.randomUUID(),
        statement = "Inflation was reported.",
        category = EconomyCategory.PRICES_AND_INFLATION,
        eventType = EconomyEventType.INDICATOR_VALUE_REPORTED,
        evidenceLevel = EconomyEvidenceLevel.PRIMARY_CONFIRMED,
        occurredOn = occurredOn,
        referencePeriod = EconomyReferencePeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            EconomyReferencePeriodGranularity.MONTH,
        ),
        geography = EconomyGeography(EconomyGeographyKind.COUNTRY, "Germany", "DE"),
        measurement = EconomyMeasurement(BigDecimal("2.4"), EconomyMeasurementUnit.PERCENT),
        entities = listOf(EconomyEntityReference("Inflation", EconomyEntityType.INDICATOR)),
        sources = listOf(
            SourceReference(
                "https://example.org/inflation",
                "Example Statistics Office",
                SourceType.PRIMARY_DOCUMENT,
                Instant.parse("2026-09-10T08:00:00Z"),
            ),
        ),
        referenceDate = referenceDate,
        referenceDateBasis = referenceDateBasis,
    )
}
