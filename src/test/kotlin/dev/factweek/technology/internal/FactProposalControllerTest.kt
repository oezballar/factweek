package dev.factweek.technology.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FactProposalControllerTest {
    private lateinit var service: FactProposalExtractionService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        service = mock(FactProposalExtractionService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(FactProposalController(service)).build()
    }

    @Test
    fun `returns extraction statistics`() {
        `when`(service.extract(10)).thenReturn(FactProposalExtractionResult(10, 1, 1, 0, 0))
        mockMvc.perform(post("/api/v1/technology/fact-proposals/extractions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proposedCount").value(1))
    }

    @Test
    fun `rejects invalid maximum`() {
        mockMvc.perform(post("/api/v1/technology/fact-proposals/extractions").param("maximum", "26"))
            .andExpect(status().isBadRequest)
    }
}
