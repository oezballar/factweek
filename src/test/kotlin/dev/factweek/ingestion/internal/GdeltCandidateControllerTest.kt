package dev.factweek.ingestion.internal

import dev.factweek.ingestion.GdeltCandidates
import dev.factweek.ingestion.GdeltImportResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GdeltCandidateControllerTest {
    private val now = Instant.parse("2026-09-08T12:00:00Z")
    private lateinit var candidates: GdeltCandidates
    private lateinit var importService: GdeltImportService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        candidates = mock(GdeltCandidates::class.java)
        importService = mock(GdeltImportService::class.java)
        mockMvc = MockMvcBuilders
            .standaloneSetup(GdeltCandidateController(candidates, importService, Clock.fixed(now, ZoneOffset.UTC)))
            .build()
    }

    @Test
    fun `uses the preceding seven days when preview window is omitted`() {
        val from = now.minusSeconds(7 * 24 * 60 * 60)
        `when`(candidates.findTechnologyCandidates("technology", from, now, 25)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/ingestion/gdelt/candidates"))
            .andExpect(status().isOk)

        verify(candidates).findTechnologyCandidates("technology", from, now, 25)
    }

    @Test
    fun `rejects import with from but no to`() {
        mockMvc.perform(post("/api/v1/ingestion/gdelt/imports").param("query", "technology").param("from", now.toString()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects import with to but no from`() {
        mockMvc.perform(post("/api/v1/ingestion/gdelt/imports").param("query", "technology").param("to", now.toString()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects invalid import maximum`() {
        mockMvc.perform(post("/api/v1/ingestion/gdelt/imports").param("query", "technology").param("maximum", "251"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns a generic bad gateway response when GDELT is unavailable`() {
        val from = now.minusSeconds(7 * 24 * 60 * 60)
        `when`(importService.import("technology", from, now, 100))
            .thenThrow(GdeltRequestException("internal connection details"))

        mockMvc.perform(post("/api/v1/ingestion/gdelt/imports").param("query", "technology"))
            .andExpect(status().isBadGateway)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal connection details"))))
    }

    @Test
    fun `returns import statistics`() {
        val from = now.minusSeconds(7 * 24 * 60 * 60)
        `when`(importService.import("technology", from, now, 100)).thenReturn(
            GdeltImportResult("technology", from, now, 100, 2, 1, 1),
        )

        mockMvc.perform(post("/api/v1/ingestion/gdelt/imports").param("query", "technology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.discoveredCount").value(2))
            .andExpect(jsonPath("$.storedCount").value(1))
            .andExpect(jsonPath("$.existingCount").value(1))
    }
}
