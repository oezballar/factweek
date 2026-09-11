package dev.factweek.ingestion.internal

import dev.factweek.ingestion.CandidateCaptureResult
import dev.factweek.ingestion.CandidateCaptures
import dev.factweek.ingestion.CandidateDiscoveryProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.time.Instant
import java.util.UUID

class ManualCandidateControllerTest {
    private lateinit var captures: CandidateCaptures
    private lateinit var mockMvc: MockMvc

    @BeforeEach fun setUp() {
        captures = object : CandidateCaptures {
            override fun capture(command: dev.factweek.ingestion.CandidateCaptureCommand) =
                CandidateCaptureResult(UUID.fromString("00000000-0000-0000-0000-000000000001"), command.sourceUrl, command.title, command.publisher, command.publishedAt, command.language, command.discoveryProvider, true)
        }
        mockMvc = MockMvcBuilders.standaloneSetup(ManualCandidateController(captures)).build()
    }

    @Test fun `creates a manual candidate`() {
        mockMvc.perform(post("/api/v1/ingestion/candidates").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.discoveryProvider").value("MANUAL"))
    }

    @Test fun `rejects missing malformed and invalid bodies as problem details`() {
        listOf("", "{bad").forEach { payload ->
            mockMvc.perform(post("/api/v1/ingestion/candidates").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    private fun body() = """{"sourceUrl":"https://example.org/result","title":"New battery result","publisher":"Example","publishedAt":"2026-09-11T08:00:00Z","language":"en"}"""
}
