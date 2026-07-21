package com.tekutekunikki.mizunomi.domain

import com.tekutekunikki.mizunomi.data.IntakeRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal const val WeeklyTrendDays = 7L

internal data class DailyIntake(
    val date: LocalDate,
    val dayLabel: String,
    val dateLabel: String,
    val amountMl: Int,
    val isToday: Boolean,
)

private val MonthDayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

internal fun buildWeeklyTrend(
    records: List<IntakeRecord>,
    displayedWeekStart: LocalDate,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<DailyIntake> {
    val weekStart = displayedWeekStart.startOfWeek()
    val totalsByDate = records.groupBy {
        Instant.ofEpochMilli(it.timestamp)
            .atZone(zoneId)
            .toLocalDate()
    }.mapValues { (_, dayRecords) ->
        dayRecords.sumOf { it.amountMl }
    }

    return (0L until WeeklyTrendDays).map { dayOffset ->
        val date = weekStart.plusDays(dayOffset)
        DailyIntake(
            date = date,
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN),
            dateLabel = date.format(MonthDayFormatter),
            amountMl = totalsByDate[date] ?: 0,
            isToday = date == today,
        )
    }
}

internal fun LocalDate.startOfWeek(): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
