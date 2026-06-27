package com.tekutekunikki.mizunomi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

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

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ReminderEnabledKey] = enabled
        }
    }

    suspend fun isReminderEnabled(): Boolean = reminderEnabled.first()

    private companion object {
        val ReminderEnabledKey = booleanPreferencesKey("reminderEnabled")
    }
}
