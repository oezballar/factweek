package dev.factweek.technology.internal

import org.springframework.ai.chat.client.ChatClient
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

    override fun extract(prompt: OpenAiFactProposalPrompt): OpenAiFactProposalResponse? =
        chatClient.prompt()
            .system(prompt.systemInstruction)
            .user(prompt.userContent)
            .options(OpenAiRequestOptions.forPrompt(prompt))
            .call()
            .entity(OpenAiFactProposalResponse::class.java) { specification ->
                specification.useProviderStructuredOutput()
            }
}

internal object OpenAiRequestOptions {
    fun forPrompt(prompt: OpenAiFactProposalPrompt): OpenAiChatOptions.Builder =
        OpenAiChatOptions.builder()
            .model(prompt.model)
            .maxCompletionTokens(prompt.maximumOutputTokens)
}
