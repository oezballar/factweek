package dev.factweek.technology.internal

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class OpenAiFactProposalEventLoggingTest {
    @Test
    fun `response event renders complete safe metadata`() {
        capture(SpringAiOpenAiFactProposalClient::class.java) { logger, messages ->
            val sourceDocumentId = UUID.fromString("00000000-0000-0000-0000-000000000123")
            OpenAiFactProposalEventLogging.response(
                logger,
                sourceDocumentId,
                "provider-model",
                "technology-fact-extraction-v2",
                "SUCCESS",
                42,
                OpenAiFactProposalResponseMetadata("provider-model", "stop", 100, 200, 300),
            )

            val message = messages.single().formattedMessage
            assertContains(message, "openai_fact_proposal_response_processed", "sourceDocumentId=$sourceDocumentId", "model=provider-model", "schemaVersion=technology-fact-extraction-v2", "outcome=SUCCESS", "durationMs=42", "finishReason=stop", "promptTokens=100", "completionTokens=200", "totalTokens=300")
        }
    }

    @Test
    fun `response event renders unavailable optional metadata deterministically`() {
        capture(SpringAiOpenAiFactProposalClient::class.java) { logger, messages ->
            OpenAiFactProposalEventLogging.response(
                logger,
                UUID.randomUUID(),
                "gpt-5-mini",
                "technology-fact-extraction-v2",
                "EMPTY_MODEL_RESPONSE",
                7,
                null,
            )

            val message = messages.single().formattedMessage
            assertContains(message, "finishReason=n/a", "promptTokens=n/a", "completionTokens=n/a", "totalTokens=n/a")
        }
    }

    @Test
    fun `extraction completed event renders safe metadata without content leaks`() {
        capture(OpenAiFactProposalExtractor::class.java) { _, messages ->
            val extractor = OpenAiFactProposalExtractor(
                object : OpenAiFactProposalClient {
                    override fun extract(prompt: OpenAiFactProposalPrompt) = OpenAiFactProposalResponse(
                        listOf(
                            OpenAiFactProposalDto(
                                statement = "SENTINEL_MODEL_STATEMENT",
                                category = "AI_AND_SOFTWARE",
                                entities = listOf(OpenAiEntityDto("SENTINEL_ENTITY", "TECHNOLOGY")),
                                evidenceText = "SENTINEL_EVIDENCE_TEXT",
                                evidenceLevel = "DOCUMENTED",
                            ),
                        ),
                    )
                },
                OpenAiFactProposalSettings("test-key", "gpt-5-mini", "technology-fact-extraction-v2", 5, 8000),
            )
            val sourceDocumentId = UUID.fromString("00000000-0000-0000-0000-000000000456")

            extractor.extract(
                FactProposalExtractionRequest(
                    sourceDocumentId,
                    "https://SENTINEL_SOURCE_URL.invalid",
                    "SENTINEL_SOURCE_CONTENT SENTINEL_PROMPT_TEXT",
                    "a".repeat(64),
                ),
            )

            val message = messages.single().formattedMessage
            assertContains(message, "openai_fact_proposal_extraction_completed", "sourceDocumentId=$sourceDocumentId", "model=gpt-5-mini", "schemaVersion=technology-fact-extraction-v2", "durationMs=", "resultCount=1")
            listOf(
                "SENTINEL_SOURCE_URL",
                "SENTINEL_SOURCE_CONTENT",
                "SENTINEL_PROMPT_TEXT",
                "SENTINEL_MODEL_STATEMENT",
                "SENTINEL_EVIDENCE_TEXT",
                "SENTINEL_ENTITY",
                "test-key",
            ).forEach {
                assertFalse(message.contains(it))
            }
        }
    }

    private fun capture(
        loggerType: Class<*>,
        test: (Logger, List<ILoggingEvent>) -> Unit,
    ) {
        val logger = LoggerFactory.getLogger(loggerType) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            test(logger, appender.list)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertContains(message: String, vararg expected: String) =
        expected.forEach { value -> assertTrue(message.contains(value), "Expected <$message> to contain <$value>") }
}
