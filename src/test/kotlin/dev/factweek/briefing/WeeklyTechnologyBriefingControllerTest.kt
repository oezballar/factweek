package dev.factweek.briefing

import dev.factweek.technology.TechnologyCategory
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
import java.time.Instant
import java.time.LocalDate

class WeeklyTechnologyBriefingControllerTest {
    private lateinit var briefing: WeeklyTechnologyBriefing
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        briefing = mock(WeeklyTechnologyBriefing::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(WeeklyTechnologyBriefingController(briefing)).build()
    }

    @Test
    fun `uses default maximum and returns factual response`() {
        val response = response(requestedMaximum = 10)
        `when`(briefing.current(emptySet(), 10)).thenReturn(response)

        mockMvc.perform(get("/api/v1/briefings/technology/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from").value("2026-09-07"))
            .andExpect(jsonPath("$.to").value("2026-09-13"))
            .andExpect(jsonPath("$.generatedAt").value("2026-09-13T12:00:00Z"))
            .andExpect(jsonPath("$.requestedMaximum").value(10))
            .andExpect(jsonPath("$.factCount").value(0))
            .andExpect(jsonPath("$.facts").isArray)
        verify(briefing).current(emptySet(), 10)
    }

    @Test
    fun `supports multiple categories and a custom maximum`() {
        val categories = setOf(TechnologyCategory.AI_AND_SOFTWARE, TechnologyCategory.ENERGY_AND_CLIMATE)
        `when`(briefing.current(categories, 3)).thenReturn(response(categories.toList().sortedBy { it.name }, 3))

        mockMvc.perform(
            get("/api/v1/briefings/technology/current")
                .param("categories", "AI_AND_SOFTWARE", "ENERGY_AND_CLIMATE")
                .param("maximum", "3"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedMaximum").value(3))
            .andExpect(jsonPath("$.appliedCategories[0]").value("AI_AND_SOFTWARE"))
            .andExpect(jsonPath("$.appliedCategories[1]").value("ENERGY_AND_CLIMATE"))
        verify(briefing).current(categories, 3)
    }

    @Test
    fun `rejects invalid maximum and unknown categories as problem details`() {
        listOf("0", "51").forEach { maximum ->
            mockMvc.perform(get("/api/v1/briefings/technology/current").param("maximum", maximum))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid technology briefing request"))
        }

        mockMvc.perform(get("/api/v1/briefings/technology/current").param("categories", "UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid technology briefing request"))
    }

    private fun response(
        categories: List<TechnologyCategory> = TechnologyCategory.entries.sortedBy { it.name },
        requestedMaximum: Int,
    ) = CurrentTechnologyBriefing(
        from = LocalDate.of(2026, 9, 7),
        to = LocalDate.of(2026, 9, 13),
        generatedAt = Instant.parse("2026-09-13T12:00:00Z"),
        appliedCategories = categories,
        requestedMaximum = requestedMaximum,
        factCount = 0,
        facts = emptyList(),
    )
}
