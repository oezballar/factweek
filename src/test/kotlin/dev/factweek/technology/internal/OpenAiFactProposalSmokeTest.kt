package dev.factweek.technology.internal

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID

/**
 * Opt-in only: this test calls the paid OpenAI API when FACTWEEK_OPENAI_SMOKE_TEST=true.
 * It is skipped by default and must never be enabled in CI.
 */
@EnabledIfEnvironmentVariable(named = "FACTWEEK_OPENAI_SMOKE_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(
    classes = [OpenAiFactProposalSmokeTest.SmokeApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "factweek.fact-proposals.openai.enabled=true",
        "spring.ai.model.chat=openai",
    ],
)
class OpenAiFactProposalSmokeTest {
    @Autowired private lateinit var extractor: OpenAiFactProposalExtractor

    @Test
    fun `extracts at most a small set of proposals from a fixed source`() {
        extractor.extract(
            FactProposalExtractionRequest(
                UUID.randomUUID(),
                "https://example.org/manual-smoke",
                "A laboratory reported that its battery prototype retained 90 percent capacity after 1,000 charge cycles.",
                "a".repeat(64),
            ),
        )
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class, FlywayAutoConfiguration::class],
    )
    @Import(OpenAiFactProposalSettings::class, OpenAiFactProposalExtractor::class, SpringAiOpenAiFactProposalClient::class)
    internal class SmokeApplication
}
