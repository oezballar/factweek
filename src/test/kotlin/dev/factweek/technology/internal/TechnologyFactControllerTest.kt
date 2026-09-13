package dev.factweek.technology.internal

import dev.factweek.technology.EntityReference
import dev.factweek.technology.EntityType
import dev.factweek.technology.EvidenceLevel
import dev.factweek.technology.PublishTechnologyFact
import dev.factweek.technology.SourceReference
import dev.factweek.technology.SourceType
import dev.factweek.technology.TechnologyCategory
import dev.factweek.technology.TechnologyEventType
import dev.factweek.technology.TechnologyFact
import dev.factweek.technology.TechnologyFacts
import dev.factweek.technology.TechnologyReadiness
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class TechnologyFactControllerTest {
    @Test
    fun `publishes a fact without an occurred date`() {
        val technologyFacts = mock(TechnologyFacts::class.java)
        val command = PublishTechnologyFact(
            statement = "A source-backed technology fact.",
            category = TechnologyCategory.AI_AND_SOFTWARE,
            eventType = TechnologyEventType.TECHNOLOGY_DEPLOYED,
            readiness = TechnologyReadiness.PRODUCTION_USE,
            evidenceLevel = EvidenceLevel.PRIMARY_CONFIRMED,
            occurredOn = null,
            entities = listOf(EntityReference("Example", EntityType.TECHNOLOGY)),
            sources = listOf(SourceReference("https://example.org/source", "Example", SourceType.PRIMARY_DOCUMENT)),
        )
        `when`(technologyFacts.publish(command)).thenReturn(
            TechnologyFact(
                id = UUID.randomUUID(),
                statement = command.statement,
                category = command.category,
                eventType = command.eventType,
                readiness = command.readiness,
                evidenceLevel = command.evidenceLevel,
                occurredOn = null,
                entities = command.entities,
                sources = command.sources,
            ),
        )
        val mockMvc = MockMvcBuilders.standaloneSetup(TechnologyFactController(technologyFacts)).build()

        mockMvc.perform(
            post("/api/v1/technology/facts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "statement": "A source-backed technology fact.",
                      "category": "AI_AND_SOFTWARE",
                      "eventType": "TECHNOLOGY_DEPLOYED",
                      "readiness": "PRODUCTION_USE",
                      "evidenceLevel": "PRIMARY_CONFIRMED",
                      "entities": [{"name": "Example", "type": "TECHNOLOGY"}],
                      "sources": [{"url": "https://example.org/source", "publisher": "Example", "sourceType": "PRIMARY_DOCUMENT"}]
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        verify(technologyFacts).publish(command)
    }
}
