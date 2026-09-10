package dev.factweek.briefing

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
internal class BriefingConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
