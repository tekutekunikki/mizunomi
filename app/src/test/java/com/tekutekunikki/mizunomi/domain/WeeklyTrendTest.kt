package com.tekutekunikki.mizunomi.domain

import com.tekutekunikki.mizunomi.data.IntakeRecord
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class WeeklyTrendTest {
    @Test
    fun buildsSevenDaysAndFillsMissingDaysWithZero() {
        val zoneId = ZoneId.of("UTC")
        val weekStart = LocalDate.of(2026, 7, 6)
        val today = LocalDate.of(2026, 7, 8)
        val tuesdayNoon = weekStart.plusDays(1)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val days = buildWeeklyTrend(
            records = listOf(
                IntakeRecord(id = 0, drinkType = DrinkTypeWater, amountMl = 700, timestamp = tuesdayNoon, memo = null),
            ),
            displayedWeekStart = weekStart,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(7, days.size)
        assertEquals(0, days[0].amountMl)
        assertEquals(700, days[1].amountMl)
        assertFalse(days[1].isToday)
        assertTrue(days[2].isToday)
    }
}
