package dev.factweek.technology.internal

import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyReadiness
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class FactProposalReviewControllerTest {
    private lateinit var reviews: FactProposalReviewService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        reviews = mock(FactProposalReviewService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(FactProposalReviewController(reviews)).build()
    }

    @Test
    fun `accept returns review result`() {
        val proposalId = UUID.randomUUID()
        val factId = UUID.randomUUID()
        `when`(reviews.accept(proposalId, AcceptFactProposal(TechnologyEventType.TECHNOLOGY_DEPLOYED, TechnologyReadiness.PRODUCTION_USE, null)))
            .thenReturn(AcceptedFactProposalReview(proposalId, FactProposalStatus.ACCEPTED, factId, Instant.parse("2026-09-10T00:00:00Z")))

        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"eventType":"TECHNOLOGY_DEPLOYED","readiness":"PRODUCTION_USE"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.proposalId").value(proposalId.toString()))
            .andExpect(jsonPath("$.proposalStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$.technologyFactId").value(factId.toString()))
    }

    @Test
    fun `reject validates blank reasons and maps review errors`() {
        val proposalId = UUID.randomUUID()
        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":" "}"""),
        ).andExpect(status().isBadRequest)

        `when`(reviews.reject(proposalId, "Reason")).thenThrow(FactProposalNotFoundException())
        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"Reason"}"""),
        ).andExpect(status().isNotFound)

        `when`(reviews.reject(proposalId, "Conflict")).thenThrow(FactProposalReviewConflictException())
        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"Conflict"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `accept maps missing and malformed request bodies to generic problem details`() {
        val proposalId = UUID.randomUUID()

        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/accept")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A valid fact-proposal review request body is required"))

        mockMvc.perform(
            post("/api/v1/technology/fact-proposals/$proposalId/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A valid fact-proposal review request body is required"))
    }
}
