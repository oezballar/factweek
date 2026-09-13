package dev.factweek.technology.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class OpenAiFactProposalResponseProcessorTest {
    private val processor = OpenAiFactProposalResponseProcessor()

    @Test
    fun `converts valid structured output with optional metadata`() {
        val response = processor.convert(
            OpenAiFactProposalTransportResponse.Content(
                validResponse,
                OpenAiFactProposalResponseMetadata(),
            ),
        )

        assertEquals(1, response.proposals?.size)
    }

    @Test
    fun `rejects a missing chat response`() {
        assertFailure(OpenAiFactProposalAdapterFailure.NO_CHAT_RESPONSE) {
            processor.convert(null)
        }
    }

    @Test
    fun `rejects a missing assistant message`() {
        assertFailure(OpenAiFactProposalAdapterFailure.MISSING_ASSISTANT_MESSAGE) {
            processor.convert(OpenAiFactProposalTransportResponse.MissingAssistantMessage())
        }
    }

    @Test
    fun `rejects null model content`() {
        assertFailure(OpenAiFactProposalAdapterFailure.EMPTY_MODEL_RESPONSE) {
            processor.convert(OpenAiFactProposalTransportResponse.Content(null))
        }
    }

    @Test
    fun `rejects empty model content`() {
        assertFailure(OpenAiFactProposalAdapterFailure.EMPTY_MODEL_RESPONSE) {
            processor.convert(OpenAiFactProposalTransportResponse.Content(""))
        }
    }

    @Test
    fun `rejects whitespace-only model content`() {
        assertFailure(OpenAiFactProposalAdapterFailure.EMPTY_MODEL_RESPONSE) {
            processor.convert(OpenAiFactProposalTransportResponse.Content(" \t\n "))
        }
    }

    @Test
    fun `wraps invalid JSON as a controlled adapter failure`() {
        val exception = assertFailure(OpenAiFactProposalAdapterFailure.INVALID_STRUCTURED_OUTPUT) {
            processor.convert(OpenAiFactProposalTransportResponse.Content("{not json"))
        }

        assertEquals("OpenAI returned invalid structured output", exception.message)
        assertNotNull(exception.cause)
    }

    private fun assertFailure(
        expected: OpenAiFactProposalAdapterFailure,
        action: () -> Unit,
    ): OpenAiFactProposalAdapterException {
        val exception = assertThrows<OpenAiFactProposalAdapterException>(action)
        assertEquals(expected, exception.failure)
        return exception
    }

    private companion object {
        val validResponse = """
            {
              "proposals": [
                {
                  "statement": "A battery reached a new efficiency threshold.",
                  "category": "ENERGY_AND_CLIMATE",
                  "entities": [{"name": "Example battery", "type": "TECHNOLOGY"}],
                  "occurredOn": null,
                  "evidenceText": "A battery reached a new efficiency threshold.",
                  "evidenceLevel": "DOCUMENTED"
                }
              ]
            }
        """.trimIndent()
    }
}
