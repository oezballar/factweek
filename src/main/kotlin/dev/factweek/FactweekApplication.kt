package dev.factweek

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FactweekApplication

fun main(args: Array<String>) {
    runApplication<FactweekApplication>(*args)
}
