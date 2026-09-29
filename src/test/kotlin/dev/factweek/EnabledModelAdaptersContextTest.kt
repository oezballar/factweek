package dev.factweek

import dev.factweek.economy.internal.OpenAiEconomyProposalExtractor
import dev.factweek.processing.internal.OpenAiArticleSectionClassifier
import dev.factweek.technology.internal.OpenAiFactProposalExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=context-test-key",
        "factweek.article-classification.openai.enabled=true",
        "factweek.fact-proposals.openai.enabled=true",
        "factweek.economy.extraction.openai.enabled=true",
    ],
)
@Testcontainers
class EnabledModelAdaptersContextTest {
    @Autowired private lateinit var applicationContext: ApplicationContext

    @Test
    fun `all production model adapters start with the auto configured Jackson mapper`() {
        assertEquals(1, applicationContext.getBeansOfType(JsonMapper::class.java).size)
        assertEquals(1, applicationContext.getBeansOfType(OpenAiArticleSectionClassifier::class.java).size)
        assertEquals(1, applicationContext.getBeansOfType(OpenAiFactProposalExtractor::class.java).size)
        assertEquals(1, applicationContext.getBeansOfType(OpenAiEconomyProposalExtractor::class.java).size)
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
