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
            PaceState.OnTrack -> "いいペースです"
            PaceState.SlightlyBehind -> "少し遅れています"
            PaceState.Behind -> "かなり遅れています"
        },
        detail = when (state) {
            PaceState.OnTrack -> "この調子で無理なく続けましょう"
            PaceState.SlightlyBehind -> "一杯飲んでおきましょう"
            PaceState.Behind -> "今日の目標まで少し遅れています"
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
