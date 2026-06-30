package com.tekutekunikki.mizunomi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

internal const val DefaultDailyGoalMl = 2000
internal val DailyGoalOptionsMl = listOf(1500, 2000, 2500, 3000)

internal fun scaleForDailyGoal(
    baselineMl: Int,
    dailyGoalMl: Int,
): Int = ((baselineMl.toLong() * dailyGoalMl) + DefaultDailyGoalMl / 2)
    .div(DefaultDailyGoalMl)
    .toInt()

class ReminderSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val reminderEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[ReminderEnabledKey] ?: true }

    val dailyGoalMl: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[DailyGoalMlKey]
                ?.takeIf { it in DailyGoalOptionsMl }
                ?: DefaultDailyGoalMl
        }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ReminderEnabledKey] = enabled
        }
    }

    suspend fun isReminderEnabled(): Boolean = reminderEnabled.first()

    suspend fun setDailyGoalMl(dailyGoalMl: Int) {
        require(dailyGoalMl in DailyGoalOptionsMl) {
            "Daily goal must be one of $DailyGoalOptionsMl ml."
        }
        dataStore.edit { preferences ->
            preferences[DailyGoalMlKey] = dailyGoalMl
        }
    }

    suspend fun getDailyGoalMl(): Int = dailyGoalMl.first()

    private companion object {
        val ReminderEnabledKey = booleanPreferencesKey("reminderEnabled")
        val DailyGoalMlKey = intPreferencesKey("dailyGoalMl")
    }
}
