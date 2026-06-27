package com.tekutekunikki.mizunomi

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import java.time.LocalDate
import java.time.LocalDateTime
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
        if (now.hour != checkHour) {
            return Result.success()
        }

        val settingsRepository = ReminderSettingsRepository(applicationContext)
        if (!settingsRepository.isReminderEnabled()) {
            return Result.success()
        }

        HydrationReminderNotifications.createChannel(applicationContext)

        val repository = IntakeRecordRepository(
            MizunomiDatabase.getInstance(applicationContext).intakeRecordDao(),
        )
        val today = now.toLocalDate()
        val todayTotalMl = repository.getTotalAmountForDay(today)
        val expectedMl = expectedMlByCheckHour.getValue(checkHour)
        val shortageMl = expectedMl - todayTotalMl

        if (
            todayTotalMl < DailyGoalMl &&
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

    private fun preferences() =
        applicationContext.getSharedPreferences("hydration_reminders", Context.MODE_PRIVATE)

    companion object {
        const val KeyCheckHour = "check_hour"
        private const val DailyGoalMl = 2000
        private const val MinimumShortageMl = 300

        private val expectedMlByCheckHour = mapOf(
            12 to 700,
            15 to 1100,
            18 to 1500,
            21 to 1900,
        )
    }
}
