package dev.factweek

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    private val modules = ApplicationModules.of(FactweekApplication::class.java)

    @Test
    fun `module boundaries are valid`() {
        modules.verify()
    }

    @Test
    fun `print module documentation`() {
        modules.forEach(::println)
    }
}
