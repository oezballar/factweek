package dev.factweek.processing.internal

import dev.factweek.processing.SectionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ArticleSectionClassificationSettingsBindingTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SettingsConfiguration::class.java))

    @Test
    fun `binds the production technology and economy catalogue`() {
        runWith(validProperties()) { context ->
            val settings = context.getBean(ArticleSectionClassificationSettings::class.java)

            assertEquals("article-section-classification-v1", settings.version)
            assertEquals(
                listOf(
                    ConfiguredSection(SectionId("technology"), "Technology"),
                    ConfiguredSection(SectionId("economy"), "Economy"),
                ),
                settings.configuredSections,
            )
        }
    }

    @Test
    fun `binds an additional section exclusively from configuration`() {
        runWith(
            validProperties(
                "factweek.article-classification.sections[2].id=policy",
                "factweek.article-classification.sections[2].description=Public policy decisions",
            ),
        ) { context ->
            val settings = context.getBean(ArticleSectionClassificationSettings::class.java)

            assertEquals(SectionId("policy"), settings.configuredSections[2].id)
            assertEquals("Public policy decisions", settings.configuredSections[2].description)
        }
    }

    @Test
    fun `accepts a classification version with exactly 128 characters`() {
        runWith(validProperties("factweek.article-classification.version=${"v".repeat(128)}")) { context ->
            assertEquals(128, context.getBean(ArticleSectionClassificationSettings::class.java).version.length)
        }
    }

    @Test
    fun `rejects invalid catalogue and adapter properties at startup`() {
        listOf(
            "empty catalogue" to emptyList<String>(),
            "empty id" to validProperties("factweek.article-classification.sections[0].id="),
            "duplicate id" to validProperties(
                "factweek.article-classification.sections[1].id=technology",
                "factweek.article-classification.sections[1].description=Duplicate",
            ),
            "blank description" to validProperties("factweek.article-classification.sections[0].description=   "),
            "blank version" to validProperties("factweek.article-classification.version=   "),
            "long version" to validProperties("factweek.article-classification.version=${"v".repeat(129)}"),
            "blank model" to validProperties("factweek.article-classification.openai.model=   "),
        ).forEach { (name, properties) ->
            contextRunner.withPropertyValues(*properties.toTypedArray()).run { context ->
                check(context.startupFailure != null) { "Expected startup failure for $name" }
                assertTrue(failureMessages(context.startupFailure!!).contains("Article classification"))
            }
        }
    }

    private fun runWith(properties: List<String>, assertion: (org.springframework.context.ApplicationContext) -> Unit) {
        contextRunner.withPropertyValues(*properties.toTypedArray()).run(assertion)
    }

    private fun validProperties(vararg additional: String): List<String> = (
        listOf(
        "factweek.article-classification.version=article-section-classification-v1",
        "factweek.article-classification.openai.model=gpt-5-mini",
        "factweek.article-classification.sections[0].id=technology",
        "factweek.article-classification.sections[0].description=Technology",
        "factweek.article-classification.sections[1].id=economy",
        "factweek.article-classification.sections[1].description=Economy",
        ) + additional
        ).associateByTo(linkedMapOf()) { it.substringBefore('=') }
        .values
        .toList()

    private fun failureMessages(exception: Throwable): String =
        generateSequence(exception) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ArticleSectionClassificationSettings::class)
    internal class SettingsConfiguration
}
