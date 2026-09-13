package dev.factweek.technology.internal

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.context.annotation.DependsOn
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** The only class that uses Spring AI's ChatClient API. */
@Component
@ConditionalOnProperty(prefix = "factweek.fact-proposals.openai", name = ["enabled"], havingValue = "true")
@DependsOn("openAiFactProposalSettings")
internal class SpringAiOpenAiFactProposalClient(
    builder: ChatClient.Builder,
    @Suppress("UNUSED_PARAMETER") settings: OpenAiFactProposalSettings,
) : OpenAiFactProposalClient {
    private val chatClient = builder.build()
    private val responseProcessor = OpenAiFactProposalResponseProcessor()

    override fun extract(prompt: OpenAiFactProposalPrompt): OpenAiFactProposalResponse? {
        val startedAt = System.nanoTime()
        val chatResponse = try {
            chatClient.prompt()
                .system(prompt.systemInstruction)
                .user(prompt.userContent)
                .options(OpenAiRequestOptions.forPrompt(prompt))
                .call()
                .chatResponse()
        } catch (exception: RuntimeException) {
            logResponse(prompt, null, "PROVIDER_REQUEST_FAILED", startedAt)
            throw exception
        }
        val transportResponse = chatResponse.toTransportResponse()
        return try {
            responseProcessor.convert(transportResponse).also {
                logResponse(prompt, transportResponse?.metadata, "SUCCESS", startedAt)
            }
        } catch (exception: OpenAiFactProposalAdapterException) {
            logResponse(prompt, transportResponse?.metadata, exception.failure.name, startedAt)
            throw exception
        }
    }

    private fun ChatResponse?.toTransportResponse(): OpenAiFactProposalTransportResponse? {
        if (this == null) return null
        val responseMetadata = metadata()
        val output = result?.output ?: return OpenAiFactProposalTransportResponse.MissingAssistantMessage(responseMetadata)
        return OpenAiFactProposalTransportResponse.Content(output.text, responseMetadata)
    }

    private fun ChatResponse.metadata(): OpenAiFactProposalResponseMetadata {
        val usage = metadata.usage
        val usageAvailable = usage.nativeUsage.let { it != null && (it !is Map<*, *> || it.isNotEmpty()) }
        return OpenAiFactProposalResponseMetadata(
            model = metadata.model.takeIf { it.isNotBlank() },
            finishReason = result?.metadata?.finishReason,
            promptTokens = usage.promptTokens.takeIf { usageAvailable },
            completionTokens = usage.completionTokens.takeIf { usageAvailable },
            totalTokens = usage.totalTokens.takeIf { usageAvailable },
        )
    }

    private fun logResponse(
        prompt: OpenAiFactProposalPrompt,
        metadata: OpenAiFactProposalResponseMetadata?,
        outcome: String,
        startedAt: Long,
    ) {
        val event = logger.atInfo()
            .addKeyValue("sourceDocumentId", prompt.sourceDocumentId)
            .addKeyValue("model", metadata?.model ?: prompt.model)
            .addKeyValue("schemaVersion", prompt.schemaVersion)
            .addKeyValue("outcome", outcome)
            .addKeyValue("durationMs", (System.nanoTime() - startedAt) / 1_000_000)
        metadata?.finishReason?.let { event.addKeyValue("finishReason", it) }
        metadata?.promptTokens?.let { event.addKeyValue("promptTokens", it) }
        metadata?.completionTokens?.let { event.addKeyValue("completionTokens", it) }
        metadata?.totalTokens?.let { event.addKeyValue("totalTokens", it) }
        event.log("openai_fact_proposal_response_processed")
    }

    private companion object {
        val logger = org.slf4j.LoggerFactory.getLogger(SpringAiOpenAiFactProposalClient::class.java)
    }
}

internal object OpenAiRequestOptions {
    fun forPrompt(prompt: OpenAiFactProposalPrompt): OpenAiChatOptions.Builder =
        OpenAiChatOptions.builder()
            .model(prompt.model)
            .maxCompletionTokens(prompt.maximumOutputTokens)
            .responseFormat(
                OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(OpenAiFactProposalStructuredOutput.strictSchema)
                    .strict(true)
                    .build(),
            )
}
