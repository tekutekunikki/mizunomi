package com.tekutekunikki.mizunomi.domain

import java.time.LocalTime
import org.junit.Test
import org.junit.Assert.assertEquals

class PaceCalculatorTest {
    @Test
    fun beforeWakeTimeIsOnTrackWithZeroExpected() {
        val status = buildPaceStatus(
            actualMl = 0,
            now = LocalTime.of(7, 59),
            dailyGoalMl = 2_000,
            wakeTimeMinutes = 8 * 60,
            bedTimeMinutes = 22 * 60,
        )

        assertEquals(0, status.expectedMl)
        assertEquals(PaceState.OnTrack, status.state)
    }

    @Test
    fun noonCanBeSlightlyBehind() {
        val status = buildPaceStatus(
            actualMl = 300,
            now = LocalTime.NOON,
            dailyGoalMl = 2_000,
            wakeTimeMinutes = 8 * 60,
            bedTimeMinutes = 22 * 60,
        )

        assertEquals(PaceState.SlightlyBehind, status.state)
    }

    @Test
    fun bedtimeUsesFullDailyGoal() {
        val status = buildPaceStatus(
            actualMl = 1_500,
            now = LocalTime.of(22, 0),
            dailyGoalMl = 2_000,
            wakeTimeMinutes = 8 * 60,
            bedTimeMinutes = 22 * 60,
        )

        assertEquals(2_000, status.expectedMl)
        assertEquals(PaceState.Behind, status.state)
    }
}
