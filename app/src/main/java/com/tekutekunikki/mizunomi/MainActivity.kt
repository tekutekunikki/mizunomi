package com.tekutekunikki.mizunomi

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tekutekunikki.mizunomi.data.IntakeRecord
import com.tekutekunikki.mizunomi.data.CsvImportException
import com.tekutekunikki.mizunomi.data.CsvImportPreview
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MaxCsvImportBytes
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import com.tekutekunikki.mizunomi.data.buildIntakeRecordsCsv
import com.tekutekunikki.mizunomi.data.decodeUtf8Csv
import com.tekutekunikki.mizunomi.data.parseIntakeRecordsCsv
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WeeklyTrendDays = 7L
private const val PastRecordLimitDays = 30L
private const val SweetDrinkWarningThresholdMl = 500
private const val BalancedDrinkTotalThresholdMl = 1000
private const val WaterTeaMinimumThresholdMl = 500

private data class RecordFeedback(
    val drinkType: String,
    val amountMl: Int,
    val recordedAt: LocalDateTime,
    val dayTotalMl: Int,
)

private sealed interface CsvExportStatus {
    data object InProgress : CsvExportStatus
    data object Success : CsvExportStatus
    data class Error(val message: String) : CsvExportStatus
}

private sealed interface CsvImportStatus {
    data object InProgress : CsvImportStatus
    data class Success(
        val importedCount: Int,
        val skippedCount: Int,
        val duplicateCount: Int,
        val unknownDrinkTypeCount: Int,
    ) : CsvImportStatus

    data class Error(val message: String) : CsvImportStatus
}

private val DrinkTypes = listOf(
    "\u6C34",
    "\u304A\u8336",
    "\u30B3\u30FC\u30D2\u30FC",
    "\u30B8\u30E5\u30FC\u30B9",
    "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF",
    "\u4E73\u98F2\u6599",
    "\u70AD\u9178\u98F2\u6599",
    "\u30A2\u30EB\u30B3\u30FC\u30EB",
    "\u305D\u306E\u4ED6",
)

private val SweetDrinkTypes = setOf(
    "\u30B8\u30E5\u30FC\u30B9",
    "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF",
    "\u4E73\u98F2\u6599",
    "\u70AD\u9178\u98F2\u6599",
)

private val WaterTeaDrinkTypes = setOf(
    "\u6C34",
    "\u304A\u8336",
)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        HydrationReminderScheduler.scheduleDailyChecks(this)
    }

    private val repository by lazy {
        IntakeRecordRepository(
            MizunomiDatabase.getInstance(this).intakeRecordDao(),
        )
    }
    private val reminderSettingsRepository by lazy {
        ReminderSettingsRepository(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HydrationReminderNotifications.createChannel(this)
        HydrationReminderScheduler.scheduleDailyChecks(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            MizunomiApp(
                repository = repository,
                reminderSettingsRepository = reminderSettingsRepository,
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun MizunomiApp(
    repository: IntakeRecordRepository,
    reminderSettingsRepository: ReminderSettingsRepository,
) {
    val todayTotalMl by repository.observeTotalAmountForDay(LocalDate.now())
        .collectAsState(initial = 0)
    val todayRecords by repository.observeTodayRecords()
        .collectAsState(initial = emptyList())
    val recentRecords by repository.observeRecentRecords(PastRecordLimitDays + 1)
        .collectAsState(initial = emptyList())
    val reminderEnabled by reminderSettingsRepository.reminderEnabled
        .collectAsState(initial = true)
    val dailyGoalMl by reminderSettingsRepository.dailyGoalMl
        .collectAsState(initial = DefaultDailyGoalMl)
    val wakeTimeMinutes by reminderSettingsRepository.wakeTimeMinutes
        .collectAsState(initial = DefaultWakeTimeMinutes)
    val bedTimeMinutes by reminderSettingsRepository.bedTimeMinutes
        .collectAsState(initial = DefaultBedTimeMinutes)
    val scope = rememberCoroutineScope()

    MizunomiAppContent(
        todayTotalMl = todayTotalMl,
        todayRecords = todayRecords,
        recentRecords = recentRecords,
        reminderEnabled = reminderEnabled,
        dailyGoalMl = dailyGoalMl,
        wakeTimeMinutes = wakeTimeMinutes,
        bedTimeMinutes = bedTimeMinutes,
        onAddRecord = { drinkType, amountMl, timestamp, recordDate, onSaved ->
            scope.launch {
                repository.addRecord(
                    drinkType = drinkType,
                    amountMl = amountMl,
                    timestamp = timestamp,
                )
                onSaved(repository.getTotalAmountForDay(recordDate))
            }
        },
        onUpdateRecord = { record, drinkType, amountMl ->
            scope.launch {
                repository.updateRecord(
                    record.copy(
                        drinkType = drinkType,
                        amountMl = amountMl,
                    ),
                )
            }
        },
        onDeleteRecord = { record ->
            scope.launch {
                repository.deleteRecord(record)
            }
        },
        onReminderEnabledChange = { enabled ->
            scope.launch {
                reminderSettingsRepository.setReminderEnabled(enabled)
            }
        },
        onDailyGoalChange = { dailyGoalMl ->
            scope.launch {
                reminderSettingsRepository.setDailyGoalMl(dailyGoalMl)
            }
        },
        onWakeTimeChange = { minutes ->
            scope.launch { reminderSettingsRepository.setWakeTimeMinutes(minutes) }
        },
        onBedTimeChange = { minutes ->
            scope.launch { reminderSettingsRepository.setBedTimeMinutes(minutes) }
        },
        onPrepareCsvExport = { onReady, onError ->
            scope.launch {
                runCatching {
                    buildIntakeRecordsCsv(repository.getAllRecords())
                }.onSuccess(onReady)
                    .onFailure {
                        onError("CSVデータを準備できませんでした。もう一度お試しください。")
                    }
            }
        },
        onAnalyzeCsvImport = { csvText, onReady, onError ->
            scope.launch {
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
        },
        onImportCsvRecords = { records, onSuccess, onError ->
            scope.launch {
                runCatching { repository.importRecords(records) }
                    .onSuccess(onSuccess)
                    .onFailure { onError("CSVデータを保存できませんでした") }
            }
        },
    )
}

@Composable
fun MizunomiAppContent(
    todayTotalMl: Int,
    todayRecords: List<IntakeRecord>,
    recentRecords: List<IntakeRecord>,
    reminderEnabled: Boolean,
    dailyGoalMl: Int,
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
    onAddRecord: (
        drinkType: String,
        amountMl: Int,
        timestamp: Long,
        recordDate: LocalDate,
        onSaved: (dayTotalMl: Int) -> Unit,
    ) -> Unit,
    onUpdateRecord: (record: IntakeRecord, drinkType: String, amountMl: Int) -> Unit,
    onDeleteRecord: (record: IntakeRecord) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onWakeTimeChange: (Int) -> Unit,
    onBedTimeChange: (Int) -> Unit,
    onPrepareCsvExport: (
        onReady: (csvContent: String) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
    onAnalyzeCsvImport: (
        csvText: String,
        onReady: (preview: CsvImportPreview) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
    onImportCsvRecords: (
        records: List<IntakeRecord>,
        onSuccess: (importedCount: Int) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
) {
    val drinkTypes = DrinkTypes
    val amounts = listOf(100, 200, 300, 500)
    var selectedDrinkType by remember { mutableStateOf(drinkTypes.first()) }
    var editingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    var voiceInputState by remember { mutableStateOf<VoiceInputState?>(null) }
    var recordFeedback by remember { mutableStateOf<RecordFeedback?>(null) }
    var selectedRecordDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedRecordTime by remember { mutableStateOf<LocalTime?>(null) }
    var recordDateTimeError by remember { mutableStateOf<String?>(null) }
    var csvExportStatus by remember { mutableStateOf<CsvExportStatus?>(null) }
    var csvImportStatus by remember { mutableStateOf<CsvImportStatus?>(null) }
    var pendingCsvImport by remember { mutableStateOf<CsvImportPreview?>(null) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val createCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csvContent = pendingCsvContent
        pendingCsvContent = null
        if (uri == null) {
            csvExportStatus = null
        } else if (csvContent == null) {
            csvExportStatus = CsvExportStatus.Error("CSVデータを保存できませんでした。")
        } else {
            uiScope.launch {
                csvExportStatus = CsvExportStatus.InProgress
                runCatching {
                    withContext(Dispatchers.IO) {
                        val outputStream = requireNotNull(
                            context.contentResolver.openOutputStream(uri, "wt"),
                        )
                        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(csvContent)
                        }
                    }
                }.onSuccess {
                    csvExportStatus = CsvExportStatus.Success
                }.onFailure {
                    csvExportStatus = CsvExportStatus.Error(
                        "CSVファイルを書き出せませんでした。保存先を確認してください。",
                    )
                }
            }
        }
    }
    val openCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            csvImportStatus = null
        } else {
            uiScope.launch {
                csvImportStatus = CsvImportStatus.InProgress
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                            if (descriptor.length > MaxCsvImportBytes) {
                                throw CsvImportException("ファイルサイズが5MBを超えています")
                            }
                        }
                        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "ファイルを開けませんでした"
                        }.use { input -> input.readBytesWithLimit(MaxCsvImportBytes) }
                        decodeUtf8Csv(bytes)
                    }
                }.onSuccess { csvText ->
                    onAnalyzeCsvImport(
                        csvText,
                        { preview ->
                            pendingCsvImport = preview
                            csvImportStatus = null
                        },
                        { message ->
                            csvImportStatus = CsvImportStatus.Error(
                                "CSVファイルを読み込めませんでした\n理由: $message",
                            )
                        },
                    )
                }.onFailure { error ->
                    csvImportStatus = CsvImportStatus.Error(
                        "CSVファイルを読み込めませんでした\n理由: " +
                            (error.message ?: "ファイルを開けませんでした"),
                    )
                }
            }
        }
    }
    val addRecordWithFeedback = { drinkType: String, amountMl: Int ->
        val now = LocalDateTime.now()
        val recordDateTime = LocalDateTime.of(
            selectedRecordDate,
            selectedRecordTime ?: now.toLocalTime(),
        ).withSecond(0).withNano(0)
        val validationError = validateRecordDateTime(recordDateTime, now)
        recordDateTimeError = validationError
        if (validationError == null) {
            onAddRecord(
                drinkType,
                amountMl,
                recordDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                recordDateTime.toLocalDate(),
            ) { updatedDayTotalMl ->
                recordFeedback = RecordFeedback(
                    drinkType = drinkType,
                    amountMl = amountMl,
                    recordedAt = recordDateTime,
                    dayTotalMl = updatedDayTotalMl,
                )
            }
        }
    }
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            voiceInputState = buildVoiceInputState(recognizedText)
        }
    }
    val remainingMl = (dailyGoalMl - todayTotalMl).coerceAtLeast(0)
    val progress = (todayTotalMl.toFloat() / dailyGoalMl).coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt()
    val isGoalAchieved = todayTotalMl >= dailyGoalMl
    val paceStatus = buildPaceStatus(
        actualMl = todayTotalMl,
        now = LocalTime.now(),
        dailyGoalMl = dailyGoalMl,
        wakeTimeMinutes = wakeTimeMinutes,
        bedTimeMinutes = bedTimeMinutes,
    )
    val weeklyTrend = remember(recentRecords) {
        buildWeeklyTrend(recentRecords)
    }
    val drinkNotices = remember(todayRecords, todayTotalMl) {
        buildDrinkNotices(todayRecords, todayTotalMl)
    }
    val drinkSummaries = drinkTypes.map { drinkType ->
        DrinkSummary(
            drinkType = drinkType,
            amountMl = todayRecords
                .filter { it.drinkType == drinkType }
                .sumOf { it.amountMl },
        )
    }.filter { it.amountMl > 0 }

    MaterialTheme {
        pendingCsvImport?.let { preview ->
            CsvImportPreviewDialog(
                preview = preview,
                onDismiss = { pendingCsvImport = null },
                onImport = {
                    pendingCsvImport = null
                    csvImportStatus = CsvImportStatus.InProgress
                    onImportCsvRecords(
                        preview.records,
                        { importedCount ->
                            csvImportStatus = CsvImportStatus.Success(
                                importedCount = importedCount,
                                skippedCount = preview.skippedRowCount,
                                duplicateCount = preview.duplicateCount,
                                unknownDrinkTypeCount = preview.unknownDrinkTypeCount,
                            )
                        },
                        { message -> csvImportStatus = CsvImportStatus.Error(message) },
                    )
                },
            )
        }
        editingRecord?.let { record ->
            EditRecordDialog(
                record = record,
                drinkTypes = drinkTypes,
                amounts = amounts,
                onDismiss = { editingRecord = null },
                onSave = { drinkType, amountMl ->
                    onUpdateRecord(record, drinkType, amountMl)
                    editingRecord = null
                },
            )
        }

        deletingRecord?.let { record ->
            DeleteRecordDialog(
                record = record,
                onDismiss = { deletingRecord = null },
                onConfirmDelete = {
                    onDeleteRecord(record)
                    deletingRecord = null
                },
            )
        }

        voiceInputState?.let { state ->
            VoiceIntakeDialog(
                state = state,
                drinkTypes = drinkTypes,
                amounts = amounts,
                onDismiss = { voiceInputState = null },
                onSave = { drinkType, amountMl ->
                    addRecordWithFeedback(drinkType, amountMl)
                    selectedDrinkType = drinkType
                    voiceInputState = null
                },
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFF4F8FB),
            bottomBar = {
                MizunomiBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                )
            },
        ) { innerPadding ->
            when (selectedTab) {
                AppTab.Home -> HomeTabContent(
                    contentPadding = innerPadding,
                    todayTotalMl = todayTotalMl,
                    remainingMl = remainingMl,
                    progress = progress,
                    progressPercent = progressPercent,
                    isGoalAchieved = isGoalAchieved,
                    dailyGoalMl = dailyGoalMl,
                    paceStatus = paceStatus,
                    drinkNotices = drinkNotices,
                )

                AppTab.Record -> RecordTabContent(
                    contentPadding = innerPadding,
                    drinkTypes = drinkTypes,
                    amounts = amounts,
                    selectedDrinkType = selectedDrinkType,
                    onDrinkTypeSelected = { selectedDrinkType = it },
                    feedback = recordFeedback,
                    dailyGoalMl = dailyGoalMl,
                    selectedRecordDate = selectedRecordDate,
                    selectedRecordTime = selectedRecordTime,
                    recordDateTimeError = recordDateTimeError,
                    onRecordDateSelected = { date ->
                        selectedRecordDate = date
                        recordDateTimeError = null
                    },
                    onUseCurrentTime = {
                        selectedRecordTime = null
                        recordDateTimeError = null
                    },
                    onRecordTimeSelected = { time ->
                        selectedRecordTime = time
                        recordDateTimeError = null
                    },
                    onQuickAdd = { amountMl ->
                        addRecordWithFeedback(selectedDrinkType, amountMl)
                    },
                    onVoiceInput = {
                        try {
                            voiceInputLauncher.launch(buildVoiceRecognitionIntent())
                        } catch (exception: ActivityNotFoundException) {
                            voiceInputState = VoiceInputState(
                                rawText = null,
                                candidate = null,
                                errorMessage = "\u7AEF\u672B\u306E\u97F3\u58F0\u5165\u529B\u3092\u8D77\u52D5\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F\u3002",
                            )
                        }
                    },
                )

                AppTab.History -> HistoryTabContent(
                    contentPadding = innerPadding,
                    weeklyTrend = weeklyTrend,
                    dailyGoalMl = dailyGoalMl,
                    drinkSummaries = drinkSummaries,
                    recentRecords = recentRecords.sortedByDescending { it.timestamp },
                    onEdit = { editingRecord = it },
                    onDelete = { deletingRecord = it },
                )

                AppTab.Settings -> SettingsTabContent(
                    contentPadding = innerPadding,
                    reminderEnabled = reminderEnabled,
                    dailyGoalMl = dailyGoalMl,
                    wakeTimeMinutes = wakeTimeMinutes,
                    bedTimeMinutes = bedTimeMinutes,
                    onReminderEnabledChange = onReminderEnabledChange,
                    onDailyGoalChange = onDailyGoalChange,
                    onWakeTimeChange = onWakeTimeChange,
                    onBedTimeChange = onBedTimeChange,
                    csvExportStatus = csvExportStatus,
                    csvImportStatus = csvImportStatus,
                    onExportCsv = {
                        csvExportStatus = CsvExportStatus.InProgress
                        onPrepareCsvExport(
                            { csvContent ->
                                pendingCsvContent = csvContent
                                createCsvDocumentLauncher.launch(
                                    "mizunomi-records-${LocalDate.now()}.csv",
                                )
                            },
                            { message ->
                                csvExportStatus = CsvExportStatus.Error(message)
                            },
                        )
                    },
                    onImportCsv = {
                        csvImportStatus = CsvImportStatus.InProgress
                        openCsvDocumentLauncher.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "text/plain"),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MizunomiBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(
                        text = tab.symbol,
                        fontSize = 20.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF116DAE),
                    selectedTextColor = Color(0xFF116DAE),
                    indicatorColor = Color(0xFFE3F3FF),
                    unselectedIconColor = Color(0xFF6C7A86),
                    unselectedTextColor = Color(0xFF6C7A86),
                ),
            )
        }
    }
}

@Composable
private fun HomeTabContent(
    contentPadding: PaddingValues,
    todayTotalMl: Int,
    remainingMl: Int,
    progress: Float,
    progressPercent: Int,
    isGoalAchieved: Boolean,
    dailyGoalMl: Int,
    paceStatus: PaceStatus,
    drinkNotices: List<DrinkNotice>,
) {
    MizunomiTabList(contentPadding = contentPadding) {
        item { TabHeader(title = "mizunomi", subtitle = "今日の水分バランス") }
        item {
            SummaryCard(
                todayTotalMl = todayTotalMl,
                remainingMl = remainingMl,
                progress = progress,
                progressPercent = progressPercent,
                isGoalAchieved = isGoalAchieved,
                dailyGoalMl = dailyGoalMl,
            )
        }
        item { PaceStatusCard(status = paceStatus) }
        if (drinkNotices.isNotEmpty()) {
            item { DrinkNoticeCard(notices = drinkNotices) }
        }
    }
}

@Composable
private fun RecordTabContent(
    contentPadding: PaddingValues,
    drinkTypes: List<String>,
    amounts: List<Int>,
    selectedDrinkType: String,
    onDrinkTypeSelected: (String) -> Unit,
    feedback: RecordFeedback?,
    dailyGoalMl: Int,
    selectedRecordDate: LocalDate,
    selectedRecordTime: LocalTime?,
    recordDateTimeError: String?,
    onRecordDateSelected: (LocalDate) -> Unit,
    onUseCurrentTime: () -> Unit,
    onRecordTimeSelected: (LocalTime) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onVoiceInput: () -> Unit,
) {
    MizunomiTabList(contentPadding = contentPadding) {
        item { TabHeader(title = "記録", subtitle = "飲んだ分をすぐ追加") }
        item {
            AddIntakeCard(
                drinkTypes = drinkTypes,
                amounts = amounts,
                selectedDrinkType = selectedDrinkType,
                onDrinkTypeSelected = onDrinkTypeSelected,
                selectedRecordDate = selectedRecordDate,
                selectedRecordTime = selectedRecordTime,
                recordDateTimeError = recordDateTimeError,
                onRecordDateSelected = onRecordDateSelected,
                onUseCurrentTime = onUseCurrentTime,
                onRecordTimeSelected = onRecordTimeSelected,
                onQuickAdd = onQuickAdd,
                onVoiceInput = onVoiceInput,
            )
        }
        feedback?.let { savedRecord ->
            item { RecordFeedbackCard(feedback = savedRecord, dailyGoalMl = dailyGoalMl) }
        }
    }
}

@Composable
private fun RecordFeedbackCard(
    feedback: RecordFeedback,
    dailyGoalMl: Int,
) {
    val isToday = feedback.recordedAt.toLocalDate() == LocalDate.now()
    val goalAchieved = isToday && feedback.dayTotalMl >= dailyGoalMl
    val remainingMl = (dailyGoalMl - feedback.dayTotalMl).coerceAtLeast(0)
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goalAchieved) Color(0xFFDCF6E6) else Color(0xFFE7F6ED),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = if (goalAchieved) {
                    "✓ 今日の目標を達成しました！"
                } else {
                    "✓ 記録しました"
                },
                color = Color(0xFF168344),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildString {
                    if (!isToday) {
                        append(feedback.recordedAt.format(dateTimeFormatter))
                        append(" に ")
                    }
                    append(feedback.drinkType)
                    append(' ')
                    append(numberFormat.format(feedback.amountMl))
                    append("ml")
                    if (goalAchieved) append(" を記録しました")
                },
                color = Color(0xFF173B2A),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isToday) {
                    "今日の合計 ${numberFormat.format(feedback.dayTotalMl)}ml / " +
                        "${numberFormat.format(dailyGoalMl)}ml"
                } else {
                    "この日の合計 ${numberFormat.format(feedback.dayTotalMl)}ml"
                },
                color = Color(0xFF315C47),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isToday && !goalAchieved) {
                Text(
                    text = "あと ${numberFormat.format(remainingMl)}ml",
                    color = Color(0xFF315C47),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HistoryTabContent(
    contentPadding: PaddingValues,
    weeklyTrend: List<DailyIntake>,
    dailyGoalMl: Int,
    drinkSummaries: List<DrinkSummary>,
    recentRecords: List<IntakeRecord>,
    onEdit: (IntakeRecord) -> Unit,
    onDelete: (IntakeRecord) -> Unit,
) {
    MizunomiTabList(contentPadding = contentPadding) {
        item { TabHeader(title = "履歴", subtitle = "最近の記録と7日間の変化") }
        item { WeeklyTrendCard(days = weeklyTrend, dailyGoalMl = dailyGoalMl) }
        if (drinkSummaries.isNotEmpty()) {
            item { TypeSummaryCard(summaries = drinkSummaries) }
        }
        item {
            Text(
                text = "Recent records",
                color = Color(0xFF25384A),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (recentRecords.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            items(recentRecords.take(10), key = { it.id }) { record ->
                IntakeRecordRow(
                    record = record,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    contentPadding: PaddingValues,
    reminderEnabled: Boolean,
    dailyGoalMl: Int,
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onWakeTimeChange: (Int) -> Unit,
    onBedTimeChange: (Int) -> Unit,
    csvExportStatus: CsvExportStatus?,
    csvImportStatus: CsvImportStatus?,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
) {
    var showDailyGoalDialog by remember { mutableStateOf(false) }
    var lifestyleError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (showDailyGoalDialog) {
        DailyGoalSelectionDialog(
            selectedGoalMl = dailyGoalMl,
            onDismiss = { showDailyGoalDialog = false },
            onSelect = { selectedGoalMl ->
                onDailyGoalChange(selectedGoalMl)
                showDailyGoalDialog = false
            },
        )
    }

    MizunomiTabList(contentPadding = contentPadding) {
        item { TabHeader(title = "設定", subtitle = "自分に合う水分習慣へ") }
        item {
            SettingsFoundationCard(
                reminderEnabled = reminderEnabled,
                dailyGoalMl = dailyGoalMl,
                onReminderEnabledChange = onReminderEnabledChange,
                onDailyGoalClick = { showDailyGoalDialog = true },
            )
        }
        item {
            LifestyleSettingsCard(
                wakeTimeMinutes = wakeTimeMinutes,
                bedTimeMinutes = bedTimeMinutes,
                errorMessage = lifestyleError,
                onWakeTimeClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val selectedMinutes = hour * 60 + minute
                            if (selectedMinutes >= bedTimeMinutes) {
                                lifestyleError = "起床時間は就寝時間より前にしてください"
                            } else {
                                lifestyleError = null
                                onWakeTimeChange(selectedMinutes)
                            }
                        },
                        wakeTimeMinutes / 60,
                        wakeTimeMinutes % 60,
                        true,
                    ).show()
                },
                onBedTimeClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val selectedMinutes = hour * 60 + minute
                            if (selectedMinutes <= wakeTimeMinutes) {
                                lifestyleError = "就寝時間は起床時間より後にしてください"
                            } else {
                                lifestyleError = null
                                onBedTimeChange(selectedMinutes)
                            }
                        },
                        bedTimeMinutes / 60,
                        bedTimeMinutes % 60,
                        true,
                    ).show()
                },
            )
        }
        item {
            DataManagementCard(
                exportStatus = csvExportStatus,
                importStatus = csvImportStatus,
                onExportCsv = onExportCsv,
                onImportCsv = onImportCsv,
            )
        }
    }
}

@Composable
private fun MizunomiTabList(
    contentPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F8FB))
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun TabHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = Color(0xFF13314B),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = Color(0xFF6C7A86),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = "まだ記録がありません。記録タブから最初の一杯を追加しましょう。",
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF6C7A86),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsFoundationCard(
    reminderEnabled: Boolean,
    dailyGoalMl: Int,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDailyGoalClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "基本設定",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            DailyGoalSettingRow(
                dailyGoalMl = dailyGoalMl,
                onClick = onDailyGoalClick,
            )
            ReminderToggleRow(
                enabled = reminderEnabled,
                onEnabledChange = onReminderEnabledChange,
            )
            Text(
                text = "音声入力について",
                color = Color(0xFF25384A),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "音声入力は端末の音声認識機能を利用します。\n認識精度や通信の有無は端末環境に依存します。",
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LifestyleSettingsCard(
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
    errorMessage: String?,
    onWakeTimeClick: () -> Unit,
    onBedTimeClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "生活リズム",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TimeSettingRow(
                label = "起床時間",
                value = formatTimeMinutes(wakeTimeMinutes),
                supportingText = "水分補給を始める時刻",
                onClick = onWakeTimeClick,
            )
            TimeSettingRow(
                label = "就寝時間",
                value = formatTimeMinutes(bedTimeMinutes),
                supportingText = "1日の水分補給を終える時刻",
                onClick = onBedTimeClick,
            )
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TimeSettingRow(
    label: String,
    value: String,
    supportingText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = label, color = Color(0xFF31485B), fontWeight = FontWeight.Medium)
            Text(
                text = supportingText,
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "$value  ›",
            color = Color(0xFF116DAE),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DataManagementCard(
    exportStatus: CsvExportStatus?,
    importStatus: CsvImportStatus?,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
) {
    val isBusy = exportStatus is CsvExportStatus.InProgress ||
        importStatus is CsvImportStatus.InProgress
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "データ管理",
                        color = Color(0xFF25384A),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "記録を端末に保存",
                        color = Color(0xFF6C7A86),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F3FB)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = "CSV",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFF116DAE),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = "すべての水分記録を、日時・飲み物・量・メモを含むCSVファイルに書き出します。",
                color = Color(0xFF526777),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1683D8)),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (exportStatus is CsvExportStatus.InProgress) {
                        "CSVを準備しています…"
                    } else {
                        "データを書き出す（CSV）"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = onImportCsv,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (importStatus is CsvImportStatus.InProgress) {
                        "CSVを確認しています…"
                    } else {
                        "データを読み込む（CSV）"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            when (exportStatus) {
                CsvExportStatus.Success -> Text(
                    text = "✓ CSVファイルを書き出しました",
                    color = Color(0xFF168344),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                is CsvExportStatus.Error -> Text(
                    text = exportStatus.message,
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                CsvExportStatus.InProgress, null -> Unit
            }
            when (importStatus) {
                is CsvImportStatus.Success -> Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "✓ ${importStatus.importedCount}件を読み込みました",
                        color = Color(0xFF168344),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${importStatus.skippedCount}件をスキップ（重複 ${importStatus.duplicateCount}件）",
                        color = Color(0xFF526777),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (importStatus.unknownDrinkTypeCount > 0) {
                        Text(
                            text = "未知の飲み物 ${importStatus.unknownDrinkTypeCount}件を「その他」で読み込みました",
                            color = Color(0xFF526777),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is CsvImportStatus.Error -> Text(
                    text = importStatus.message,
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )

                CsvImportStatus.InProgress, null -> Unit
            }
        }
    }
}

@Composable
private fun CsvImportPreviewDialog(
    preview: CsvImportPreview,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "CSVを読み込みますか？") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ImportPreviewCount("読み込み予定", preview.records.size)
                ImportPreviewCount("スキップ予定", preview.skippedRowCount)
                ImportPreviewCount("重複候補", preview.duplicateCount)
                ImportPreviewCount("その他へ変換", preview.unknownDrinkTypeCount)
                if (preview.errors.isNotEmpty()) {
                    Text(
                        text = "確認が必要な行",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF25384A),
                        fontWeight = FontWeight.SemiBold,
                    )
                    preview.errors.forEach { error ->
                        Text(
                            text = "${error.rowNumber}行目: ${error.reason}",
                            color = Color(0xFF8A5A00),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.hiddenErrorCount > 0) {
                        Text(
                            text = "ほか${preview.hiddenErrorCount}件のエラーがあります",
                            color = Color(0xFF8A5A00),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (preview.records.isEmpty()) {
                    Text(
                        text = "読み込み可能な記録がありません。",
                        color = Color(0xFFB3261E),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onImport,
                enabled = preview.records.isNotEmpty(),
            ) {
                Text(text = "読み込む")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "キャンセル") }
        },
    )
}

@Composable
private fun ImportPreviewCount(
    label: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = Color(0xFF526777))
        Text(text = "$count 件", color = Color(0xFF173B2A), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailyGoalSettingRow(
    dailyGoalMl: Int,
    onClick: () -> Unit,
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "1日の目標水分量",
                color = Color(0xFF31485B),
            )
            Text(
                text = "タップして変更",
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "${numberFormat.format(dailyGoalMl)} ml  ›",
            color = Color(0xFF116DAE),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DailyGoalSelectionDialog(
    selectedGoalMl: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "1日の目標水分量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "毎日の目標を選んでください。",
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = Color(0xFF6C7A86),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DailyGoalOptionsMl.forEach { goalMl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(goalMl) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = goalMl == selectedGoalMl,
                            onClick = { onSelect(goalMl) },
                        )
                        Text(
                            text = "${numberFormat.format(goalMl)} ml",
                            modifier = Modifier.padding(start = 8.dp),
                            color = Color(0xFF25384A),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "キャンセル")
            }
        },
    )
}

@Composable
private fun ReminderToggleRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "水分補給リマインド",
                color = Color(0xFF31485B),
            )
            Text(
                text = if (enabled) "ON" else "OFF",
                color = if (enabled) Color(0xFF116DAE) else Color(0xFF6C7A86),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1683D8),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFB8C7D1),
                uncheckedBorderColor = Color(0xFFB8C7D1),
            ),
        )
    }
}

@Composable
private fun VoiceIntakeDialog(
    state: VoiceInputState,
    drinkTypes: List<String>,
    amounts: List<Int>,
    onDismiss: () -> Unit,
    onSave: (drinkType: String, amountMl: Int) -> Unit,
) {
    var isEditing by remember(state.rawText, state.errorMessage) {
        mutableStateOf(state.candidate == null)
    }
    var selectedDrinkType by remember(state.rawText, state.errorMessage) {
        mutableStateOf(state.candidate?.drinkType ?: drinkTypes.first())
    }
    val correctionAmounts = remember(state.candidate?.amountMl, amounts) {
        (listOfNotNull(state.candidate?.amountMl) + amounts)
            .distinct()
            .sorted()
    }
    var selectedAmountMl by remember(state.rawText, state.errorMessage) {
        mutableStateOf(state.candidate?.amountMl ?: amounts.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "\u97F3\u58F0\u5165\u529B\u306E\u7D50\u679C")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.rawText?.let { rawText ->
                    Text(
                        text = "\u8A8D\u8B58\u7D50\u679C\uFF1A$rawText",
                        color = Color(0xFF6C7A86),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (state.candidate != null && !isEditing) {
                    Text(
                        text = "${state.candidate.drinkType} ${state.candidate.amountMl}ml \u3068\u3057\u3066\u8A18\u9332\u3057\u307E\u3059\u304B\uFF1F",
                        color = Color(0xFF31485B),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = state.errorMessage
                            ?: "\u98F2\u307F\u7269\u3068\u91CF\u3092\u9078\u3093\u3067\u304F\u3060\u3055\u3044\u3002",
                        color = Color(0xFF31485B),
                        fontWeight = FontWeight.SemiBold,
                    )
                    ChoiceRow(
                        values = drinkTypes,
                        selectedValue = selectedDrinkType,
                        onSelected = { selectedDrinkType = it },
                    )
                    AmountSelectionGrid(
                        amounts = correctionAmounts,
                        selectedAmountMl = selectedAmountMl,
                        onSelected = { selectedAmountMl = it },
                    )
                }
            }
        },
        confirmButton = {
            if (state.candidate != null && !isEditing) {
                TextButton(
                    onClick = {
                        onSave(state.candidate.drinkType, state.candidate.amountMl)
                    },
                ) {
                    Text(text = "\u4FDD\u5B58")
                }
            } else {
                TextButton(
                    onClick = { onSave(selectedDrinkType, selectedAmountMl) },
                ) {
                    Text(text = "\u4FDD\u5B58")
                }
            }
        },
        dismissButton = {
            if (state.candidate != null && !isEditing) {
                TextButton(onClick = { isEditing = true }) {
                    Text(text = "\u4FEE\u6B63")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            }
        },
    )
}

@Composable
private fun DrinkNoticeCard(notices: List<DrinkNotice>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\u98F2\u307F\u7269\u30D0\u30E9\u30F3\u30B9",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            notices.forEach { notice ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = notice.title,
                        color = Color(0xFF8A5A00),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = notice.message,
                        color = Color(0xFF6C7A86),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaceStatusCard(status: PaceStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\u4ECA\u65E5\u306E\u30DA\u30FC\u30B9",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "\u76EE\u5B89\uFF1A${status.targetTimeLabel}\u307E\u3067\u306B",
                    color = Color(0xFF6C7A86),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${status.expectedMl} ml",
                    color = Color(0xFF0F2F47),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "\u73FE\u5728\uFF1A${status.actualMl} ml",
                    color = Color(0xFF31485B),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (status.remainingMl > 0) {
                        "\u3042\u3068 ${status.remainingMl} ml"
                    } else {
                        "\u9806\u8ABF\u3067\u3059"
                    },
                    color = status.color,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = status.message,
                color = status.color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status.detail,
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EditRecordDialog(
    record: IntakeRecord,
    drinkTypes: List<String>,
    amounts: List<Int>,
    onDismiss: () -> Unit,
    onSave: (drinkType: String, amountMl: Int) -> Unit,
) {
    var selectedDrinkType by remember(record.id) { mutableStateOf(record.drinkType) }
    var selectedAmountMl by remember(record.id) { mutableStateOf(record.amountMl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit record")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = record.timestamp.toRecordDateTimeText(),
                    color = Color(0xFF6C7A86),
                    style = MaterialTheme.typography.bodySmall,
                )
                ChoiceRow(
                    values = drinkTypes,
                    selectedValue = selectedDrinkType,
                    onSelected = { selectedDrinkType = it },
                )
                AmountSelectionGrid(
                    amounts = amounts,
                    selectedAmountMl = selectedAmountMl,
                    onSelected = { selectedAmountMl = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedDrinkType, selectedAmountMl) },
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun DeleteRecordDialog(
    record: IntakeRecord,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "\u3053\u306E\u8A18\u9332\u3092\u524A\u9664\u3057\u307E\u3059\u304B\uFF1F")
        },
        text = {
            Text(
                text = "${record.drinkType} ${record.amountMl} ml",
                color = Color(0xFF31485B),
                fontWeight = FontWeight.SemiBold,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(text = "\u524A\u9664", color = Color(0xFFB3261E))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "\u30AD\u30E3\u30F3\u30BB\u30EB")
            }
        },
    )
}

@Composable
private fun SummaryCard(
    todayTotalMl: Int,
    remainingMl: Int,
    progress: Float,
    progressPercent: Int,
    isGoalAchieved: Boolean,
    dailyGoalMl: Int,
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoalAchieved) Color(0xFFF2FBF6) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Today's intake",
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$todayTotalMl ml",
                color = Color(0xFF0F2F47),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 52.sp,
            )
            if (isGoalAchieved) {
                AchievementBadge()
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (isGoalAchieved) Color(0xFF2EAD5B) else Color(0xFF2A9DF4),
                trackColor = Color(0xFFE5EEF5),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Goal ${numberFormat.format(dailyGoalMl)} ml",
                    color = Color(0xFF6C7A86),
                )
                Text(text = "$progressPercent%", color = Color(0xFF0F6FAE), fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = if (isGoalAchieved) {
                    "\u304A\u3081\u3067\u3068\u3046\u3054\u3056\u3044\u307E\u3059\uFF01"
                } else {
                    "\u3042\u3068 $remainingMl ml"
                },
                color = if (isGoalAchieved) Color(0xFF1F8F4D) else Color(0xFF2D6A9F),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AchievementBadge() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F7EA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u2713",
                color = Color(0xFF168344),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Column {
                Text(
                    text = "\u4ECA\u65E5\u306E\u76EE\u6A19\u3092\u9054\u6210\u3057\u307E\u3057\u305F\uFF01",
                    color = Color(0xFF146C3A),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "\u9054\u6210\u30D0\u30C3\u30B8",
                    color = Color(0xFF3E7A56),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(
    days: List<DailyIntake>,
    dailyGoalMl: Int,
) {
    val maxBarAmount = maxOf(dailyGoalMl, days.maxOfOrNull { it.amountMl } ?: 0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "7-day trend",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            days.forEach { day ->
                TrendBarRow(
                    day = day,
                    maxBarAmount = maxBarAmount,
                    dailyGoalMl = dailyGoalMl,
                )
            }
        }
    }
}

@Composable
private fun TrendBarRow(
    day: DailyIntake,
    maxBarAmount: Int,
    dailyGoalMl: Int,
) {
    val progress = (day.amountMl.toFloat() / maxBarAmount).coerceIn(0f, 1f)
    val barColor = when {
        day.amountMl >= dailyGoalMl -> Color(0xFF2EAD5B)
        day.isToday -> Color(0xFF1683D8)
        else -> Color(0xFF76B9E8)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.dayLabel,
                color = if (day.isToday) Color(0xFF0F6FAE) else Color(0xFF6C7A86),
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
            )
            if (day.amountMl >= dailyGoalMl) {
                Text(
                    text = "\u2713",
                    color = Color(0xFF168344),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 12.dp),
            color = barColor,
            trackColor = Color(0xFFE5EEF5),
        )
        Text(
            text = "${day.amountMl} ml",
            modifier = Modifier.width(72.dp),
            color = if (day.isToday) Color(0xFF0F2F47) else Color(0xFF31485B),
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TypeSummaryCard(summaries: List<DrinkSummary>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "By drink type",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summaries.forEach { summary ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = summary.drinkType,
                        color = Color(0xFF31485B),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${summary.amountMl} ml",
                        color = Color(0xFF0F2F47),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddIntakeCard(
    drinkTypes: List<String>,
    amounts: List<Int>,
    selectedDrinkType: String,
    onDrinkTypeSelected: (String) -> Unit,
    selectedRecordDate: LocalDate,
    selectedRecordTime: LocalTime?,
    recordDateTimeError: String?,
    onRecordDateSelected: (LocalDate) -> Unit,
    onUseCurrentTime: () -> Unit,
    onRecordTimeSelected: (LocalTime) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onVoiceInput: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Quick add",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            RecordDateTimeSelector(
                selectedDate = selectedRecordDate,
                selectedTime = selectedRecordTime,
                errorMessage = recordDateTimeError,
                onDateSelected = onRecordDateSelected,
                onUseCurrentTime = onUseCurrentTime,
                onTimeSelected = onRecordTimeSelected,
            )
            OutlinedButton(
                onClick = onVoiceInput,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "\uD83C\uDFA4 \u97F3\u58F0\u3067\u8A18\u9332",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ChoiceRow(
                values = drinkTypes,
                selectedValue = selectedDrinkType,
                onSelected = onDrinkTypeSelected,
            )
            QuickAmountGrid(
                amounts = amounts,
                onQuickAdd = onQuickAdd,
            )
        }
    }
}

@Composable
private fun RecordDateTimeSelector(
    selectedDate: LocalDate,
    selectedTime: LocalTime?,
    errorMessage: String?,
    onDateSelected: (LocalDate) -> Unit,
    onUseCurrentTime: () -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val displayTime = selectedTime ?: LocalTime.now()
    val displayDate = when (selectedDate) {
        today -> "今日"
        today.minusDays(1) -> "昨日"
        else -> selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "記録日時",
                    color = Color(0xFF527189),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$displayDate ${displayTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = Color(0xFF0F5F94),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "日付",
            color = Color(0xFF526777),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectButton(
                text = "今日",
                selected = selectedDate == today,
                modifier = Modifier.weight(1f),
                onClick = { onDateSelected(today) },
            )
            SelectButton(
                text = "昨日",
                selected = selectedDate == today.minusDays(1),
                modifier = Modifier.weight(1f),
                onClick = { onDateSelected(today.minusDays(1)) },
            )
            SelectButton(
                text = "日付を選択",
                selected = selectedDate != today && selectedDate != today.minusDays(1),
                modifier = Modifier.weight(1.35f),
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                    ).apply {
                        datePicker.minDate = today.minusDays(PastRecordLimitDays)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        datePicker.maxDate = today.plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli() - 1
                    }.show()
                },
            )
        }
        Text(
            text = "時刻",
            color = Color(0xFF526777),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectButton(
                text = "現在時刻",
                selected = selectedTime == null,
                modifier = Modifier.weight(1f),
                onClick = onUseCurrentTime,
            )
            SelectButton(
                text = selectedTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "時刻を選択",
                selected = selectedTime != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    val initialTime = selectedTime ?: LocalTime.now()
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            onTimeSelected(LocalTime.of(hourOfDay, minute))
                        },
                        initialTime.hour,
                        initialTime.minute,
                        true,
                    ).show()
                },
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFB3261E),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowValues.forEach { value ->
                    SelectButton(
                        text = value,
                        selected = selectedValue == value,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(value) },
                    )
                }
                if (rowValues.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickAmountGrid(
    amounts: List<Int>,
    onQuickAdd: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        amounts.chunked(2).forEach { rowAmounts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowAmounts.forEach { amountMl ->
                    Button(
                        onClick = { onQuickAdd(amountMl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1683D8)),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "+${amountMl}ml",
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (rowAmounts.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AmountSelectionGrid(
    amounts: List<Int>,
    selectedAmountMl: Int,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        amounts.chunked(2).forEach { rowAmounts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowAmounts.forEach { amountMl ->
                    SelectButton(
                        text = "$amountMl ml",
                        selected = selectedAmountMl == amountMl,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(amountMl) },
                    )
                }
                if (rowAmounts.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SelectButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFFE3F3FF) else Color.White,
            contentColor = if (selected) Color(0xFF116DAE) else Color(0xFF31485B),
        ),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun IntakeRecordRow(
    record: IntakeRecord,
    onEdit: (IntakeRecord) -> Unit,
    onDelete: (IntakeRecord) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.drinkType,
                    color = Color(0xFF263B4D),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = record.timestamp.toRecordDateTimeText(),
                    color = Color(0xFF7C8A96),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.amountMl} ml",
                    color = Color(0xFF0F2F47),
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { onEdit(record) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(text = "Edit", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onDelete(record) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFB3261E),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun Long.toRecordDateTimeText(): String {
    val dateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    val dateLabel = when (dateTime.toLocalDate()) {
        today -> "今日"
        today.minusDays(1) -> "昨日"
        else -> dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    return "$dateLabel ${dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun validateRecordDateTime(
    recordDateTime: LocalDateTime,
    now: LocalDateTime,
): String? = when {
    recordDateTime.toLocalDate().isBefore(now.toLocalDate().minusDays(PastRecordLimitDays)) ->
        "記録できるのは過去30日までです。"

    recordDateTime.isAfter(now) ->
        "未来の日時は記録できません。日時を変更してください。"

    else -> null
}

private fun buildWeeklyTrend(records: List<IntakeRecord>): List<DailyIntake> {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()
    val totalsByDate = records.groupBy {
        Instant.ofEpochMilli(it.timestamp)
            .atZone(zoneId)
            .toLocalDate()
    }.mapValues { (_, dayRecords) ->
        dayRecords.sumOf { it.amountMl }
    }

    return (WeeklyTrendDays - 1 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo)
        DailyIntake(
            date = date,
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN),
            amountMl = totalsByDate[date] ?: 0,
            isToday = date == today,
        )
    }
}

private fun buildPaceStatus(
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
        color = when (state) {
            PaceState.OnTrack -> Color(0xFF168344)
            PaceState.SlightlyBehind -> Color(0xFFB06C00)
            PaceState.Behind -> Color(0xFFB3261E)
        },
    )
}

private fun formatTimeMinutes(minutes: Int): String =
    "%02d:%02d".format(Locale.JAPAN, minutes / 60, minutes % 60)

private fun buildVoiceRecognitionIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "\u98F2\u307F\u7269\u3068\u91CF\u3092\u8A71\u3057\u3066\u304F\u3060\u3055\u3044")
    }

private fun buildVoiceInputState(text: String?): VoiceInputState =
    if (text.isNullOrBlank()) {
        VoiceInputState(
            rawText = text,
            candidate = null,
            errorMessage = "\u3046\u307E\u304F\u8AAD\u307F\u53D6\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u98F2\u307F\u7269\u3068\u91CF\u3092\u9078\u3093\u3067\u304F\u3060\u3055\u3044\u3002",
        )
    } else {
        val candidate = parseVoiceIntake(text)
        VoiceInputState(
            rawText = text,
            candidate = candidate,
            errorMessage = if (candidate == null) {
                "\u3046\u307E\u304F\u8AAD\u307F\u53D6\u308C\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u98F2\u307F\u7269\u3068\u91CF\u3092\u9078\u3093\u3067\u304F\u3060\u3055\u3044\u3002"
            } else {
                null
            },
        )
    }

private fun parseVoiceIntake(text: String): VoiceIntakeCandidate? {
    val normalizedText = text.normalizeDigits()
    val amountMl = Regex("\\d+")
        .find(normalizedText)
        ?.value
        ?.toIntOrNull()
        ?: return null
    val drinkType = classifyVoiceDrinkType(normalizedText)

    return VoiceIntakeCandidate(
        drinkType = drinkType,
        amountMl = amountMl,
        rawText = text,
    )
}

private fun classifyVoiceDrinkType(text: String): String =
    when {
        text.containsAny("ポカリ", "ポカリスエット", "アクエリアス", "スポーツドリンク") -> "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF"
        text.containsAny("コーラ", "炭酸") -> "\u70AD\u9178\u98F2\u6599"
        text.containsAny("牛乳", "カツゲン", "コーヒー牛乳", "乳飲料") -> "\u4E73\u98F2\u6599"
        text.containsAny("ビール", "酒", "ワイン", "ハイボール") -> "\u30A2\u30EB\u30B3\u30FC\u30EB"
        text.containsAny("午後ティー", "午後の紅茶", "ジュース", "オレンジ", "りんご") -> "\u30B8\u30E5\u30FC\u30B9"
        text.containsAny("お茶", "緑茶", "麦茶") -> "\u304A\u8336"
        text.containsAny("コーヒー", "ブラック") -> "\u30B3\u30FC\u30D2\u30FC"
        text.contains("水") -> "\u6C34"
        text.contains("その他") -> "\u305D\u306E\u4ED6"
        else -> "\u305D\u306E\u4ED6"
    }

private fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { contains(it) }

private fun String.normalizeDigits(): String =
    map { char ->
        when (char) {
            in '\uFF10'..'\uFF19' -> '0' + (char - '\uFF10')
            else -> char
        }
    }.joinToString(separator = "")

private fun buildDrinkNotices(
    records: List<IntakeRecord>,
    todayTotalMl: Int,
): List<DrinkNotice> {
    val sweetDrinkTotalMl = records
        .filter { it.drinkType in SweetDrinkTypes }
        .sumOf { it.amountMl }
    val hasAlcohol = records.any { it.drinkType == "\u30A2\u30EB\u30B3\u30FC\u30EB" }
    val waterTeaTotalMl = records
        .filter { it.drinkType in WaterTeaDrinkTypes }
        .sumOf { it.amountMl }

    return buildList {
        if (sweetDrinkTotalMl >= SweetDrinkWarningThresholdMl) {
            add(
                DrinkNotice(
                    title = "\u7518\u3044\u98F2\u307F\u7269\u304C\u5C11\u3057\u591A\u3081\u3067\u3059",
                    message = "\u6B21\u306E\u4E00\u676F\u306F\u6C34\u304B\u304A\u8336\u3092\u9078\u3093\u3067\u307F\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
        if (hasAlcohol) {
            add(
                DrinkNotice(
                    title = "\u30A2\u30EB\u30B3\u30FC\u30EB\u306E\u8A18\u9332\u304C\u3042\u308A\u307E\u3059",
                    message = "\u4ECA\u65E5\u306F\u6C34\u3082\u4E00\u7DD2\u306B\u3068\u3063\u3066\u304A\u304D\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
        if (
            todayTotalMl >= BalancedDrinkTotalThresholdMl &&
            waterTeaTotalMl < WaterTeaMinimumThresholdMl
        ) {
            add(
                DrinkNotice(
                    title = "\u6C34\u30FB\u304A\u8336\u304C\u5C11\u306A\u3081\u3067\u3059",
                    message = "\u6B21\u306F\u3084\u3055\u3057\u304F\u4E00\u676F\u8DB3\u3057\u3066\u304A\u304D\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
    }
}

private data class DrinkSummary(
    val drinkType: String,
    val amountMl: Int,
)

private data class DrinkNotice(
    val title: String,
    val message: String,
)

private data class VoiceInputState(
    val rawText: String?,
    val candidate: VoiceIntakeCandidate?,
    val errorMessage: String?,
)

private data class VoiceIntakeCandidate(
    val drinkType: String,
    val amountMl: Int,
    val rawText: String,
)

private enum class AppTab(
    val label: String,
    val symbol: String,
) {
    Home(label = "ホーム", symbol = "⌂"),
    Record(label = "記録", symbol = "+"),
    History(label = "履歴", symbol = "≡"),
    Settings(label = "設定", symbol = "⚙"),
}

private data class DailyIntake(
    val date: LocalDate,
    val dayLabel: String,
    val amountMl: Int,
    val isToday: Boolean,
)

private data class PaceStatus(
    val targetTimeLabel: String,
    val expectedMl: Int,
    val actualMl: Int,
    val remainingMl: Int,
    val message: String,
    val detail: String,
    val color: Color,
)

private enum class PaceState {
    OnTrack,
    SlightlyBehind,
    Behind,
}

@Preview(showBackground = true)
@Composable
private fun MizunomiAppPreview() {
    MizunomiAppContent(
        todayTotalMl = 400,
        todayRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "\u6C34",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
            IntakeRecord(
                id = 2,
                drinkType = "\u304A\u8336",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        recentRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "\u6C34",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        reminderEnabled = true,
        dailyGoalMl = DefaultDailyGoalMl,
        wakeTimeMinutes = DefaultWakeTimeMinutes,
        bedTimeMinutes = DefaultBedTimeMinutes,
        onAddRecord = { _, amountMl, _, _, onSaved -> onSaved(400 + amountMl) },
        onUpdateRecord = { _, _, _ -> },
        onDeleteRecord = {},
        onReminderEnabledChange = {},
        onDailyGoalChange = {},
        onWakeTimeChange = {},
        onBedTimeChange = {},
        onPrepareCsvExport = { onReady, _ -> onReady("date,time,drinkType,amountMl,memo\n") },
        onAnalyzeCsvImport = { _, onReady, _ ->
            onReady(
                CsvImportPreview(
                    records = emptyList(),
                    skippedRowCount = 0,
                    duplicateCount = 0,
                    unknownDrinkTypeCount = 0,
                    totalErrorCount = 0,
                    errors = emptyList(),
                ),
            )
        },
        onImportCsvRecords = { records, onSuccess, _ -> onSuccess(records.size) },
    )
}

private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > maxBytes) {
            throw CsvImportException("ファイルサイズが5MBを超えています")
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
