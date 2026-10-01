package dev.factweek.briefing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class BriefingWindow(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
) {
    companion object {
        fun requestedDates(from: String?, to: String?): Pair<LocalDate, LocalDate> {
            if (from == null || to == null) {
                throw InvalidBriefingWindowException("Both from and to are required as ISO calendar dates (yyyy-MM-dd)")
            }
            val dates = try {
                LocalDate.parse(from) to LocalDate.parse(to)
            } catch (_: DateTimeParseException) {
                throw InvalidBriefingWindowException("from and to must be valid ISO calendar dates (yyyy-MM-dd)")
            }
            if (ChronoUnit.DAYS.between(dates.first, dates.second) != 6L) {
                throw InvalidBriefingWindowException("from and to must define exactly seven consecutive calendar days, inclusive")
            }
            return dates
        }

        fun explicit(from: LocalDate, to: LocalDate, clock: Clock): BriefingWindow =
            BriefingWindow(from, to, clock.instant())

        fun current(clock: Clock): BriefingWindow {
            val generatedAt = clock.instant()
            val to = generatedAt.atZone(clock.zone).toLocalDate()

            return BriefingWindow(
                from = to.minusDays(6),
                to = to,
                generatedAt = generatedAt,
            )
        }
    }
}

class InvalidBriefingWindowException(message: String) : RuntimeException(message)
