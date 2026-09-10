package dev.factweek.ingestion.internal

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SourceContentControllerTest {
    private lateinit var retrieval: SourceContentRetrievalService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        retrieval = mock(SourceContentRetrievalService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(SourceContentController(retrieval)).build()
    }

    @Test
    fun `returns retrieval statistics without source text`() {
        `when`(retrieval.retrieve(10, false)).thenReturn(SourceContentRetrievalResult(10, 2, 1, 1, 0))

        mockMvc.perform(post("/api/v1/ingestion/source-content/fetches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedCount").value(2))
            .andExpect(jsonPath("$.fetchedCount").value(1))
            .andExpect(jsonPath("$.textContent").doesNotExist())
    }

    @Test
    fun `returns generic bad request for invalid maximum`() {
        `when`(retrieval.retrieve(26, false)).thenThrow(InvalidSourceContentFetchRequestException())

        mockMvc.perform(post("/api/v1/ingestion/source-content/fetches").param("maximum", "26"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Invalid source-content fetch request"))
    }
}
