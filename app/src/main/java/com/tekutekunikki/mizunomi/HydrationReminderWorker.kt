package com.tekutekunikki.mizunomi

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HydrationReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val checkHour = inputData.getInt(KeyCheckHour, -1)
        if (checkHour !in expectedMlByCheckHour.keys) {
            return Result.success()
        }

        val now = LocalDateTime.now()
        if (!isWithinCheckWindow(checkHour, now.toLocalTime())) {
            return Result.success()
        }

        val settingsRepository = ReminderSettingsRepository(applicationContext)
        if (!settingsRepository.isReminderEnabled()) {
            return Result.success()
        }
        val dailyGoalMl = settingsRepository.getDailyGoalMl()

        HydrationReminderNotifications.createChannel(applicationContext)

        val repository = IntakeRecordRepository(
            MizunomiDatabase.getInstance(applicationContext).intakeRecordDao(),
        )
        val today = now.toLocalDate()
        val todayTotalMl = repository.getTotalAmountForDay(today)
        val expectedMl = scaleForDailyGoal(
            baselineMl = expectedMlByCheckHour.getValue(checkHour),
            dailyGoalMl = dailyGoalMl,
        )
        val shortageMl = expectedMl - todayTotalMl

        if (
            todayTotalMl < dailyGoalMl &&
            shortageMl >= MinimumShortageMl &&
            !wasAlreadyNotified(today, checkHour)
        ) {
            HydrationReminderNotifications.showHydrationReminder(applicationContext, checkHour)
            markNotified(today, checkHour)
        }

        return Result.success()
    }

    private fun wasAlreadyNotified(date: LocalDate, checkHour: Int): Boolean =
        preferences().getBoolean(notificationKey(date, checkHour), false)

    private fun markNotified(date: LocalDate, checkHour: Int) {
        preferences()
            .edit()
            .putBoolean(notificationKey(date, checkHour), true)
            .apply()
    }

    private fun notificationKey(date: LocalDate, checkHour: Int): String =
        "${date.format(DateTimeFormatter.BASIC_ISO_DATE)}-$checkHour"

    private fun isWithinCheckWindow(checkHour: Int, currentTime: LocalTime): Boolean {
        val windowStart = LocalTime.of(checkHour, 0)
        val nextCheckHour = expectedMlByCheckHour.keys
            .filter { it > checkHour }
            .minOrNull()
        val windowEnd = nextCheckHour?.let { LocalTime.of(it, 0) }

        return !currentTime.isBefore(windowStart) &&
            (windowEnd == null || currentTime.isBefore(windowEnd))
    }

    private fun preferences() =
        applicationContext.getSharedPreferences("hydration_reminders", Context.MODE_PRIVATE)

    companion object {
        const val KeyCheckHour = "check_hour"
        private const val MinimumShortageMl = 300

        private val expectedMlByCheckHour = mapOf(
            12 to 700,
            15 to 1100,
            18 to 1500,
            21 to 1900,
        )
    }
}
