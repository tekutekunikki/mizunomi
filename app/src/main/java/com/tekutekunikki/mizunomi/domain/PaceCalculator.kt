package com.tekutekunikki.mizunomi.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class PaceStatus(
    val targetTimeLabel: String,
    val expectedMl: Int,
    val actualMl: Int,
    val remainingMl: Int,
    val message: String,
    val detail: String,
    val state: PaceState,
)

internal enum class PaceState {
    OnTrack,
    SlightlyBehind,
    Behind,
}

internal fun buildPaceStatus(
    actualMl: Int,
    now: LocalTime,
    dailyGoalMl: Int,
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
): PaceStatus {
    val currentTimeMinutes = now.hour * 60 + now.minute
    val expectedMl = expectedIntakeForTime(
        currentTimeMinutes = currentTimeMinutes,
        wakeTimeMinutes = wakeTimeMinutes,
        bedTimeMinutes = bedTimeMinutes,
        dailyGoalMl = dailyGoalMl,
    )
    val remainingMl = (expectedMl - actualMl).coerceAtLeast(0)
    val state = when {
        actualMl >= expectedMl -> PaceState.OnTrack
        expectedMl < scaleForDailyGoal(300, dailyGoalMl) -> PaceState.OnTrack
        remainingMl < 300 -> PaceState.SlightlyBehind
        else -> PaceState.Behind
    }

    return PaceStatus(
        targetTimeLabel = now.format(DateTimeFormatter.ofPattern("H:mm")),
        expectedMl = expectedMl,
        actualMl = actualMl,
        remainingMl = remainingMl,
        message = when (state) {
            PaceState.OnTrack -> "\u3044\u3044\u30DA\u30FC\u30B9\u3067\u3059"
            PaceState.SlightlyBehind -> "\u5C11\u3057\u9045\u308C\u3066\u3044\u307E\u3059"
            PaceState.Behind -> "\u304B\u306A\u308A\u9045\u308C\u3066\u3044\u307E\u3059"
        },
        detail = when (state) {
            PaceState.OnTrack -> "\u3053\u306E\u8ABF\u5B50\u3067\u7121\u7406\u306A\u304F\u7D9A\u3051\u307E\u3057\u3087\u3046"
            PaceState.SlightlyBehind -> "\u4E00\u676F\u98F2\u3093\u3067\u304A\u304D\u307E\u3057\u3087\u3046"
            PaceState.Behind -> "\u4ECA\u65E5\u306E\u76EE\u6A19\u307E\u3067\u5C11\u3057\u9045\u308C\u3066\u3044\u307E\u3059"
        },
        state = state,
    )
}

internal fun expectedIntakeForTime(
    currentTimeMinutes: Int,
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
    dailyGoalMl: Int,
): Int {
    val activeMinutes = (bedTimeMinutes - wakeTimeMinutes).coerceAtLeast(1)
    val elapsedMinutes = (currentTimeMinutes - wakeTimeMinutes).coerceIn(0, activeMinutes)
    return (dailyGoalMl * (elapsedMinutes.toFloat() / activeMinutes)).toInt()
}

internal fun scaleForDailyGoal(
    baseAmountMl: Int,
    dailyGoalMl: Int,
): Int =
    (baseAmountMl * (dailyGoalMl / 2_000f)).toInt()
