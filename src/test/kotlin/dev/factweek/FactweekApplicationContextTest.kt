package dev.factweek

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
import java.time.Clock

@SpringBootTest(
    classes = [FactweekApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.ai.model.chat=none",
        "factweek.fact-proposals.openai.enabled=false",
    ],
)
@Testcontainers
class FactweekApplicationContextTest {
    @Autowired private lateinit var applicationContext: ApplicationContext

    @Test
    fun `full application context provides exactly one central clock`() {
        assertEquals(setOf("applicationClock"), applicationContext.getBeansOfType(Clock::class.java).keys)
    }

    companion object {
        @Container @JvmStatic @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"),
        )
    }
}
