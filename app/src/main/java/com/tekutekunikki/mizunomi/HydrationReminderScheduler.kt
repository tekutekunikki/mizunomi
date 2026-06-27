package com.tekutekunikki.mizunomi

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object HydrationReminderScheduler {
    private val checkHours = listOf(12, 15, 18, 21)

    fun scheduleDailyChecks(context: Context) {
        checkHours.forEach { checkHour ->
            scheduleDailyCheck(context.applicationContext, checkHour)
        }
    }

    private fun scheduleDailyCheck(context: Context, checkHour: Int) {
        val delay = delayUntilNextCheck(checkHour)
        val request = PeriodicWorkRequestBuilder<HydrationReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(HydrationReminderWorker.KeyCheckHour to checkHour))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "hydration-reminder-$checkHour",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun delayUntilNextCheck(checkHour: Int): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(checkHour, 0))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next)
    }
}
