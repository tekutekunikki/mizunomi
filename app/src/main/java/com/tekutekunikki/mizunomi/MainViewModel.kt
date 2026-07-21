package com.tekutekunikki.mizunomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tekutekunikki.mizunomi.data.CsvImportPreview
import com.tekutekunikki.mizunomi.data.IntakeRecord
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.buildIntakeRecordsCsv
import com.tekutekunikki.mizunomi.data.parseIntakeRecordsCsv
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val repository: IntakeRecordRepository,
    private val reminderSettingsRepository: ReminderSettingsRepository,
) : ViewModel() {
    val reminderEnabled: Flow<Boolean> = reminderSettingsRepository.reminderEnabled
    val dailyGoalMl: Flow<Int> = reminderSettingsRepository.dailyGoalMl
    val wakeTimeMinutes: Flow<Int> = reminderSettingsRepository.wakeTimeMinutes
    val bedTimeMinutes: Flow<Int> = reminderSettingsRepository.bedTimeMinutes

    fun observeTotalAmountForDay(date: LocalDate): Flow<Int> =
        repository.observeTotalAmountForDay(date)

    fun observeRecordsForDay(date: LocalDate): Flow<List<IntakeRecord>> =
        repository.observeRecordsForDay(date)

    fun observeRecentRecords(days: Long): Flow<List<IntakeRecord>> =
        repository.observeRecentRecords(days)

    fun observeRecordsForWeekContaining(date: LocalDate): Flow<List<IntakeRecord>> =
        repository.observeRecordsForWeekContaining(date)

    fun addRecord(
        drinkType: String,
        amountMl: Int,
        timestamp: Long,
        recordDate: LocalDate,
        onSaved: (recordId: Long, dayTotalMl: Int) -> Unit,
        onError: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val recordId = repository.addRecord(
                    drinkType = drinkType,
                    amountMl = amountMl,
                    timestamp = timestamp,
                )
                recordId to repository.getTotalAmountForDay(recordDate)
            }.onSuccess { (recordId, dayTotalMl) ->
                onSaved(recordId, dayTotalMl)
            }.onFailure {
                onError()
            }
        }
    }

    fun updateRecord(
        record: IntakeRecord,
        drinkType: String,
        amountMl: Int,
    ) {
        viewModelScope.launch {
            repository.updateRecord(
                record.copy(
                    drinkType = drinkType,
                    amountMl = amountMl,
                ),
            )
        }
    }

    fun deleteRecord(record: IntakeRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun deleteRecordById(
        recordId: Long,
        onSuccess: () -> Unit,
        onError: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.deleteRecordById(recordId)
            }.onSuccess {
                onSuccess()
            }.onFailure {
                onError()
            }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reminderSettingsRepository.setReminderEnabled(enabled)
        }
    }

    fun setDailyGoalMl(dailyGoalMl: Int) {
        viewModelScope.launch {
            reminderSettingsRepository.setDailyGoalMl(dailyGoalMl)
        }
    }

    fun setWakeTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            reminderSettingsRepository.setWakeTimeMinutes(minutes)
        }
    }

    fun setBedTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            reminderSettingsRepository.setBedTimeMinutes(minutes)
        }
    }

    fun prepareCsvExport(
        onReady: (csvContent: String) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                buildIntakeRecordsCsv(repository.getAllRecords())
            }.onSuccess(onReady)
                .onFailure {
                    onError("CSVデータを準備できませんでした。もう一度お試しください。")
                }
        }
    }

    fun analyzeCsvImport(
        csvText: String,
        onReady: (preview: CsvImportPreview) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val existingRecords = repository.getAllRecords()
                withContext(Dispatchers.Default) {
                    parseIntakeRecordsCsv(csvText, existingRecords)
                }
            }.onSuccess(onReady)
                .onFailure { error ->
                    onError(error.message ?: "CSVファイルを解析できませんでした")
                }
        }
    }

    fun importCsvRecords(
        records: List<IntakeRecord>,
        onSuccess: (importedCount: Int) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.importRecords(records)
            }.onSuccess(onSuccess)
                .onFailure {
                    onError("CSVデータを保存できませんでした")
                }
        }
    }

    class Factory(
        private val repository: IntakeRecordRepository,
        private val reminderSettingsRepository: ReminderSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    repository = repository,
                    reminderSettingsRepository = reminderSettingsRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
