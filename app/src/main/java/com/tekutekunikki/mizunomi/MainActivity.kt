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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.tekutekunikki.mizunomi.data.CsvImportException
import com.tekutekunikki.mizunomi.data.CsvImportPreview
import com.tekutekunikki.mizunomi.data.IntakeRecord
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MaxCsvImportBytes
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import com.tekutekunikki.mizunomi.data.decodeUtf8Csv
import com.tekutekunikki.mizunomi.data.remote.GeminiClient
import com.tekutekunikki.mizunomi.domain.CoachUseCase
import com.tekutekunikki.mizunomi.domain.DailyIntake
import com.tekutekunikki.mizunomi.domain.DrinkNotice
import com.tekutekunikki.mizunomi.domain.DrinkTypes
import com.tekutekunikki.mizunomi.domain.PaceState
import com.tekutekunikki.mizunomi.domain.PaceStatus
import com.tekutekunikki.mizunomi.domain.VoiceIntakeCandidate
import com.tekutekunikki.mizunomi.domain.WeeklyTrendDays
import com.tekutekunikki.mizunomi.domain.buildDrinkNotices
import com.tekutekunikki.mizunomi.domain.buildPaceStatus
import com.tekutekunikki.mizunomi.domain.buildWeeklyTrend
import com.tekutekunikki.mizunomi.domain.parseVoiceIntake
import com.tekutekunikki.mizunomi.domain.startOfWeek
import com.tekutekunikki.mizunomi.ui.theme.MizunomiColors
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PastRecordLimitDays = 30L
private val WeekRangeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)

private data class RecordFeedback(
    val drinkType: String,
    val amountMl: Int,
    val recordedAt: LocalDateTime,
    val dayTotalMl: Int,
)

private data class HomeQuickRecordOption(
    val drinkType: String,
    val amountMl: Int,
)

private data class HydrationGuideTopic(
    val title: String,
    val description: String,
    val imageResId: Int,
    val contentDescription: String,
)

private sealed interface HomeQuickRecordStatus {
    data class Success(val drinkType: String, val amountMl: Int) : HomeQuickRecordStatus
    data object Error : HomeQuickRecordStatus
}

private data class RecordSnackbarFeedback(
    val recordId: Long,
    val drinkType: String,
    val amountMl: Int,
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

private val DrinkTypeIcons = mapOf(
    "水" to "💧",
    "お茶" to "🍵",
    "コーヒー" to "☕",
    "ジュース" to "🧃",
    "スポーツドリンク" to "🏃",
    "乳飲料" to "🥛",
    "炭酸飲料" to "🫧",
    "アルコール" to "🍺",
    "その他" to "🥤",
)

private const val DefaultDrinkTypeIcon = "🥤"

private fun drinkTypeDisplayLabel(drinkType: String): String =
    "${DrinkTypeIcons[drinkType] ?: DefaultDrinkTypeIcon} $drinkType"

private val HomeQuickRecordOptions = listOf(
    HomeQuickRecordOption(drinkType = "水", amountMl = 100),
    HomeQuickRecordOption(drinkType = "お茶", amountMl = 100),
    HomeQuickRecordOption(drinkType = "コーヒー", amountMl = 200),
)

private val HydrationGuideTopics = listOf(
    HydrationGuideTopic(
        title = "こまめに飲む",
        description = "一度にたくさんではなく、少しずつ水分をとると続けやすくなります。",
        imageResId = R.drawable.hydrate_often,
        contentDescription = "こまめな水分補給を案内するイラスト",
    ),
    HydrationGuideTopic(
        title = "起床後に1杯",
        description = "朝の生活リズムに合わせて、まずは1杯から始めましょう。",
        imageResId = R.drawable.morning_water,
        contentDescription = "起床後の水分補給を案内するイラスト",
    ),
    HydrationGuideTopic(
        title = "運動前後に補給",
        description = "体を動かす前後は、水分補給を意識しやすいタイミングです。",
        imageResId = R.drawable.exercise_hydration,
        contentDescription = "運動前後の水分補給を案内するイラスト",
    ),
    HydrationGuideTopic(
        title = "入浴前後も意識",
        description = "入浴の前後は、忘れずに水分をとるきっかけにしましょう。",
        imageResId = R.drawable.bath_hydration,
        contentDescription = "入浴前後の水分補給を案内するイラスト",
    ),
    HydrationGuideTopic(
        title = "就寝前は控えめに",
        description = "寝る前は無理に多く飲まず、気になる場合は控えめにしましょう。",
        imageResId = R.drawable.before_sleep_water,
        contentDescription = "就寝前の控えめな水分補給を案内するイラスト",
    ),
)

class MainActivity : ComponentActivity() {
    private val currentDateState = mutableStateOf(LocalDate.now())

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
    private val coachUseCase by lazy {
        CoachUseCase(GeminiClient())
    }
    private val mainViewModel by lazy {
        ViewModelProvider(
            this,
            MainViewModel.Factory(
                repository = repository,
                reminderSettingsRepository = reminderSettingsRepository,
                coachUseCase = coachUseCase,
            ),
        )[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        HydrationReminderNotifications.createChannel(this)
        HydrationReminderScheduler.scheduleDailyChecks(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            MizunomiRootApp(
                viewModel = mainViewModel,
                currentDate = currentDateState.value,
                onRefreshCurrentDate = ::refreshCurrentDate,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentDate()
    }

    private fun refreshCurrentDate() {
        currentDateState.value = LocalDate.now()
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
private fun MizunomiRootApp(
    viewModel: MainViewModel,
    currentDate: LocalDate,
    onRefreshCurrentDate: () -> Unit,
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1_000)
        showSplash = false
    }

    if (showSplash) {
        MizunomiSplashScreen()
    } else {
        MizunomiApp(
            viewModel = viewModel,
            currentDate = currentDate,
            onRefreshCurrentDate = onRefreshCurrentDate,
        )
    }
}

@Composable
private fun MizunomiSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MizunomiColors.ScreenBackground)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.mizunomi_splash02),
            contentDescription = "水分補給を伝えるmizunomiのスプラッシュ画像",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun MizunomiApp(
    viewModel: MainViewModel,
    currentDate: LocalDate,
    onRefreshCurrentDate: () -> Unit,
) {
    val todayTotalFlow = remember(viewModel, currentDate) {
        viewModel.observeTotalAmountForDay(currentDate)
    }
    val todayTotalMl by todayTotalFlow.collectAsState(initial = 0)
    val todayRecordsFlow = remember(viewModel, currentDate) {
        viewModel.observeRecordsForDay(currentDate)
    }
    val todayRecords by todayRecordsFlow.collectAsState(initial = emptyList())
    val recentRecordsFlow = remember(viewModel, currentDate) {
        viewModel.observeRecentRecords(PastRecordLimitDays + 1)
    }
    val recentRecords by recentRecordsFlow.collectAsState(initial = emptyList())
    val currentWeekStart = remember(currentDate) { currentDate.startOfWeek() }
    var displayedWeekStart by remember { mutableStateOf(currentWeekStart) }
    LaunchedEffect(currentWeekStart) {
        displayedWeekStart = currentWeekStart
    }
    val weeklyRecordsFlow = remember(viewModel, displayedWeekStart) {
        viewModel.observeRecordsForWeekContaining(displayedWeekStart)
    }
    val weeklyRecords by weeklyRecordsFlow.collectAsState(initial = emptyList())
    val reminderEnabled by viewModel.reminderEnabled
        .collectAsState(initial = true)
    val dailyGoalMl by viewModel.dailyGoalMl
        .collectAsState(initial = DefaultDailyGoalMl)
    val wakeTimeMinutes by viewModel.wakeTimeMinutes
        .collectAsState(initial = DefaultWakeTimeMinutes)
    val bedTimeMinutes by viewModel.bedTimeMinutes
        .collectAsState(initial = DefaultBedTimeMinutes)
    val coachUiState by viewModel.coachUiState
        .collectAsState(initial = CoachUiState.Idle)

    MizunomiAppContent(
        todayTotalMl = todayTotalMl,
        todayRecords = todayRecords,
        recentRecords = recentRecords,
        weeklyRecords = weeklyRecords,
        displayedWeekStart = displayedWeekStart,
        currentWeekStart = currentWeekStart,
        reminderEnabled = reminderEnabled,
        dailyGoalMl = dailyGoalMl,
        wakeTimeMinutes = wakeTimeMinutes,
        bedTimeMinutes = bedTimeMinutes,
        coachUiState = coachUiState,
        currentDate = currentDate,
        onRefreshCurrentDate = onRefreshCurrentDate,
        onAddRecord = { drinkType, amountMl, timestamp, recordDate, onSaved, onError ->
            viewModel.addRecord(
                drinkType = drinkType,
                amountMl = amountMl,
                timestamp = timestamp,
                recordDate = recordDate,
                onSaved = onSaved,
                onError = onError,
            )
        },
        onUpdateRecord = { record, drinkType, amountMl ->
            viewModel.updateRecord(record, drinkType, amountMl)
        },
        onDeleteRecord = { record ->
            viewModel.deleteRecord(record)
        },
        onDeleteRecordById = { recordId, onSuccess, onError ->
            viewModel.deleteRecordById(recordId, onSuccess, onError)
        },
        onReminderEnabledChange = { enabled ->
            viewModel.setReminderEnabled(enabled)
        },
        onDailyGoalChange = { dailyGoalMl ->
            viewModel.setDailyGoalMl(dailyGoalMl)
        },
        onWakeTimeChange = { minutes ->
            viewModel.setWakeTimeMinutes(minutes)
        },
        onBedTimeChange = { minutes ->
            viewModel.setBedTimeMinutes(minutes)
        },
        onCoachAdviceRequest = {
            viewModel.requestCoachAdvice(todayRecords)
        },
        onCoachErrorShown = {
            viewModel.clearCoachError()
        },
        onDisplayedWeekStartChange = { requestedWeekStart ->
            onRefreshCurrentDate()
            val normalizedWeekStart = requestedWeekStart.startOfWeek()
            if (!normalizedWeekStart.isAfter(currentWeekStart)) {
                displayedWeekStart = normalizedWeekStart
            }
        },
        onPrepareCsvExport = { onReady, onError ->
            viewModel.prepareCsvExport(onReady, onError)
        },
        onAnalyzeCsvImport = { csvText, onReady, onError ->
            viewModel.analyzeCsvImport(csvText, onReady, onError)
        },
        onImportCsvRecords = { records, onSuccess, onError ->
            viewModel.importCsvRecords(records, onSuccess, onError)
        },
    )
}

@Composable
fun MizunomiAppContent(
    todayTotalMl: Int,
    todayRecords: List<IntakeRecord>,
    recentRecords: List<IntakeRecord>,
    weeklyRecords: List<IntakeRecord>,
    displayedWeekStart: LocalDate,
    currentWeekStart: LocalDate,
    reminderEnabled: Boolean,
    dailyGoalMl: Int,
    wakeTimeMinutes: Int,
    bedTimeMinutes: Int,
    coachUiState: CoachUiState,
    currentDate: LocalDate,
    onRefreshCurrentDate: () -> Unit,
    onAddRecord: (
        drinkType: String,
        amountMl: Int,
        timestamp: Long,
        recordDate: LocalDate,
        onSaved: (recordId: Long, dayTotalMl: Int) -> Unit,
        onError: () -> Unit,
    ) -> Unit,
    onUpdateRecord: (record: IntakeRecord, drinkType: String, amountMl: Int) -> Unit,
    onDeleteRecord: (record: IntakeRecord) -> Unit,
    onDeleteRecordById: (recordId: Long, onSuccess: () -> Unit, onError: () -> Unit) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onWakeTimeChange: (Int) -> Unit,
    onBedTimeChange: (Int) -> Unit,
    onCoachAdviceRequest: () -> Unit,
    onCoachErrorShown: () -> Unit,
    onDisplayedWeekStartChange: (LocalDate) -> Unit,
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
    var selectedDrinkType by rememberSaveable { mutableStateOf(drinkTypes.first()) }
    var editingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    var voiceInputState by remember { mutableStateOf<VoiceInputState?>(null) }
    var recordFeedback by remember { mutableStateOf<RecordFeedback?>(null) }
    var selectedRecordDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedRecordTime by remember { mutableStateOf<LocalTime?>(null) }
    var recordDateTimeError by remember { mutableStateOf<String?>(null) }
    var homeQuickRecordStatus by remember { mutableStateOf<HomeQuickRecordStatus?>(null) }
    var isHomeQuickRecordSaving by remember { mutableStateOf(false) }
    var csvExportStatus by remember { mutableStateOf<CsvExportStatus?>(null) }
    var csvImportStatus by remember { mutableStateOf<CsvImportStatus?>(null) }
    var pendingCsvImport by remember { mutableStateOf<CsvImportPreview?>(null) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var autoScrollToAmountRequest by remember { mutableStateOf(0) }
    var isRecordScrollInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissSnackbar: () -> Unit = {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
    val showRecordSnackbar = { feedback: RecordSnackbarFeedback ->
        dismissSnackbar()
        uiScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = buildRecordSnackbarMessage(feedback, dailyGoalMl),
                actionLabel = "元に戻す",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onDeleteRecordById(
                    feedback.recordId,
                    {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        uiScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "直前の記録を元に戻しました",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        uiScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "元に戻せませんでした。もう一度お試しください",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                )
            }
        }
    }
    LaunchedEffect(selectedTab, currentDate) {
        if (selectedTab == AppTab.Home) {
            onRefreshCurrentDate()
        }
    }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60_000)
        }
    }
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
        onRefreshCurrentDate()
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
                { recordId, updatedDayTotalMl ->
                    recordFeedback = RecordFeedback(
                        drinkType = drinkType,
                        amountMl = amountMl,
                        recordedAt = recordDateTime,
                        dayTotalMl = updatedDayTotalMl,
                    )
                    showRecordSnackbar(
                        RecordSnackbarFeedback(
                            recordId = recordId,
                            drinkType = drinkType,
                            amountMl = amountMl,
                            dayTotalMl = updatedDayTotalMl,
                        ),
                    )
                },
                {
                    recordDateTimeError = "記録できませんでした。もう一度お試しください"
                },
            )
        }
    }
    val addHomeQuickRecord = { option: HomeQuickRecordOption ->
        if (!isHomeQuickRecordSaving) {
            onRefreshCurrentDate()
            val recordDateTime = LocalDateTime.now().withSecond(0).withNano(0)
            isHomeQuickRecordSaving = true
            homeQuickRecordStatus = null
            onAddRecord(
                option.drinkType,
                option.amountMl,
                recordDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                recordDateTime.toLocalDate(),
                { recordId, updatedDayTotalMl ->
                    isHomeQuickRecordSaving = false
                    homeQuickRecordStatus = HomeQuickRecordStatus.Success(
                        drinkType = option.drinkType,
                        amountMl = option.amountMl,
                    )
                    showRecordSnackbar(
                        RecordSnackbarFeedback(
                            recordId = recordId,
                            drinkType = option.drinkType,
                            amountMl = option.amountMl,
                            dayTotalMl = updatedDayTotalMl,
                        ),
                    )
                },
                {
                    isHomeQuickRecordSaving = false
                    homeQuickRecordStatus = HomeQuickRecordStatus.Error
                },
            )
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
        now = currentTime,
        dailyGoalMl = dailyGoalMl,
        wakeTimeMinutes = wakeTimeMinutes,
        bedTimeMinutes = bedTimeMinutes,
    )
    val weeklyTrend = remember(weeklyRecords, displayedWeekStart) {
        buildWeeklyTrend(weeklyRecords, displayedWeekStart)
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
    LaunchedEffect(coachUiState) {
        val errorState = coachUiState as? CoachUiState.Error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = errorState.message,
            duration = SnackbarDuration.Short,
        )
        onCoachErrorShown()
    }

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
                            onRefreshCurrentDate()
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
                    onRefreshCurrentDate()
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
                    onRefreshCurrentDate()
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
            containerColor = MizunomiColors.ScreenBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                MizunomiBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        dismissSnackbar()
                        onRefreshCurrentDate()
                        selectedTab = tab
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .dismissSnackbarOnPointerDown(snackbarHostState),
            ) {
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
                        quickRecordOptions = HomeQuickRecordOptions,
                        quickRecordStatus = homeQuickRecordStatus,
                        isQuickRecordSaving = isHomeQuickRecordSaving,
                        coachUiState = coachUiState,
                        onQuickRecord = addHomeQuickRecord,
                        onCoachAdviceRequest = onCoachAdviceRequest,
                        onGoToRecordTab = {
                            dismissSnackbar()
                            selectedTab = AppTab.Record
                        },
                        onScrollStarted = dismissSnackbar,
                    )

                    AppTab.Record -> RecordTabContent(
                        contentPadding = innerPadding,
                        drinkTypes = drinkTypes,
                        amounts = amounts,
                        selectedDrinkType = selectedDrinkType,
                        onDrinkTypeSelected = { drinkType ->
                            if (drinkType != selectedDrinkType) {
                                selectedDrinkType = drinkType
                                if (!isRecordScrollInProgress) {
                                    autoScrollToAmountRequest += 1
                                }
                            }
                        },
                        feedback = recordFeedback,
                        dailyGoalMl = dailyGoalMl,
                        selectedRecordDate = selectedRecordDate,
                        selectedRecordTime = selectedRecordTime,
                        recordDateTimeError = recordDateTimeError,
                        autoScrollToAmountRequest = autoScrollToAmountRequest,
                        autoScrollToAmountEnabled = !isRecordScrollInProgress,
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
                                    errorMessage = "端末の音声入力を起動できませんでした。",
                                )
                            }
                        },
                        onScrollStarted = dismissSnackbar,
                        onScrollInProgressChanged = { isScrollInProgress ->
                            isRecordScrollInProgress = isScrollInProgress
                        },
                    )

                    AppTab.History -> HistoryTabContent(
                        contentPadding = innerPadding,
                        weeklyTrend = weeklyTrend,
                        displayedWeekStart = displayedWeekStart,
                        currentWeekStart = currentWeekStart,
                        dailyGoalMl = dailyGoalMl,
                        drinkSummaries = drinkSummaries,
                        recentRecords = recentRecords.sortedByDescending { it.timestamp },
                        onEdit = { editingRecord = it },
                        onDelete = { deletingRecord = it },
                        onDisplayedWeekStartChange = onDisplayedWeekStartChange,
                        onScrollStarted = dismissSnackbar,
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
                        onScrollStarted = dismissSnackbar,
                    )
                }
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
        containerColor = MizunomiColors.CardBackground,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
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
                    selectedIconColor = MizunomiColors.AccentBlueSelected,
                    selectedTextColor = MizunomiColors.AccentBlueSelected,
                    indicatorColor = MizunomiColors.SelectedBackground,
                    unselectedIconColor = MizunomiColors.TextSecondary,
                    unselectedTextColor = MizunomiColors.TextSecondary,
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
    quickRecordOptions: List<HomeQuickRecordOption>,
    quickRecordStatus: HomeQuickRecordStatus?,
    isQuickRecordSaving: Boolean,
    coachUiState: CoachUiState,
    onQuickRecord: (HomeQuickRecordOption) -> Unit,
    onCoachAdviceRequest: () -> Unit,
    onGoToRecordTab: () -> Unit,
    onScrollStarted: () -> Unit,
) {
    MizunomiTabList(contentPadding = contentPadding, onScrollStarted = onScrollStarted) {
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
        item {
            CoachCard(
                state = coachUiState,
                onCoachAdviceRequest = onCoachAdviceRequest,
            )
        }
        item {
            HomeQuickRecordCard(
                options = quickRecordOptions,
                status = quickRecordStatus,
                isSaving = isQuickRecordSaving,
                onQuickRecord = onQuickRecord,
                onGoToRecordTab = onGoToRecordTab,
            )
        }
        if (drinkNotices.isNotEmpty()) {
            item { DrinkNoticeCard(notices = drinkNotices) }
        }
        item { HydrationGuideSection(topics = HydrationGuideTopics) }
    }
}

@Composable
private fun CoachCard(
    state: CoachUiState,
    onCoachAdviceRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "コーチに相談",
                    color = MizunomiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "今日の記録をもとに、次の一杯をやさしく提案します。",
                    color = MizunomiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onCoachAdviceRequest,
                enabled = state !is CoachUiState.Loading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MizunomiColors.AccentBlueStrong),
            ) {
                if (state is CoachUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        color = MizunomiColors.CardBackground,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "相談中…")
                } else {
                    Text(text = "コーチに相談")
                }
            }

            when (state) {
                CoachUiState.Idle,
                is CoachUiState.Error -> Unit

                CoachUiState.Loading -> Text(
                    text = "コーチが今日の飲み方を確認しています。",
                    color = MizunomiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )

                is CoachUiState.Success -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MizunomiColors.SoftBlueBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(14.dp),
                        color = MizunomiColors.TextBody,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HydrationGuideSection(topics: List<HydrationGuideTopic>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "水分補給ガイド",
                color = MizunomiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "毎日続けやすいタイミングを、イラストで確認できます。",
                color = MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 28.dp),
        ) {
            items(topics) { topic ->
                HydrationGuideCard(topic = topic)
            }
        }

        Text(
            text = "必要な水分量は、体格・活動量・気温・食事内容によって変わります。\n" +
                "体調に不安がある場合は、無理せず専門家に相談してください。",
            color = MizunomiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HydrationGuideCard(topic: HydrationGuideTopic) {
    Card(
        modifier = Modifier.width(268.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MizunomiColors.SoftBlueBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = topic.imageResId),
                    contentDescription = topic.contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }
            Text(
                text = topic.title,
                color = MizunomiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = topic.description,
                color = MizunomiColors.TextSecondaryDark,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun HomeQuickRecordCard(
    options: List<HomeQuickRecordOption>,
    status: HomeQuickRecordStatus?,
    isSaving: Boolean,
    onQuickRecord: (HomeQuickRecordOption) -> Unit,
    onGoToRecordTab: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "クイック記録",
                        color = MizunomiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "よく使う記録をワンタップで追加",
                        color = MizunomiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onGoToRecordTab) {
                    Text(text = "詳細に記録する")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onQuickRecord(option) },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MizunomiColors.CardBackgroundSoft,
                            contentColor = MizunomiColors.AccentBlueSelected,
                        ),
                    ) {
                        Text(
                            text = "${drinkTypeDisplayLabel(option.drinkType)}\n${option.amountMl}ml",
                            maxLines = 2,
                            overflow = TextOverflow.Clip,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            when (status) {
                is HomeQuickRecordStatus.Success -> {
                    Text(
                        text = "${drinkTypeDisplayLabel(status.drinkType)} ${status.amountMl}mlを記録しました",
                        color = MizunomiColors.AchievedGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HomeQuickRecordStatus.Error -> {
                    Text(
                        text = "記録できませんでした。もう一度お試しください",
                        color = MizunomiColors.DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                null -> Unit
            }
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
    autoScrollToAmountRequest: Int,
    autoScrollToAmountEnabled: Boolean,
    onRecordDateSelected: (LocalDate) -> Unit,
    onUseCurrentTime: () -> Unit,
    onRecordTimeSelected: (LocalTime) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onVoiceInput: () -> Unit,
    onScrollStarted: () -> Unit,
    onScrollInProgressChanged: (Boolean) -> Unit,
) {
    MizunomiTabList(
        contentPadding = contentPadding,
        onScrollStarted = onScrollStarted,
        onScrollInProgressChanged = onScrollInProgressChanged,
    ) {
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
                autoScrollToAmountRequest = autoScrollToAmountRequest,
                autoScrollToAmountEnabled = autoScrollToAmountEnabled,
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
            containerColor = if (goalAchieved) MizunomiColors.SuccessChipBackground else MizunomiColors.SuccessBackgroundAlt,
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
                color = MizunomiColors.AchievedGreen,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildString {
                    if (!isToday) {
                        append(feedback.recordedAt.format(dateTimeFormatter))
                        append(" に ")
                    }
                    append(drinkTypeDisplayLabel(feedback.drinkType))
                    append(' ')
                    append(numberFormat.format(feedback.amountMl))
                    append("ml")
                    if (goalAchieved) append(" を記録しました")
                },
                color = MizunomiColors.SuccessGreenDeep,
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
                color = MizunomiColors.SuccessGreenSoftText,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isToday && !goalAchieved) {
                Text(
                    text = "あと ${numberFormat.format(remainingMl)}ml",
                    color = MizunomiColors.SuccessGreenSoftText,
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
    displayedWeekStart: LocalDate,
    currentWeekStart: LocalDate,
    dailyGoalMl: Int,
    drinkSummaries: List<DrinkSummary>,
    recentRecords: List<IntakeRecord>,
    onEdit: (IntakeRecord) -> Unit,
    onDelete: (IntakeRecord) -> Unit,
    onDisplayedWeekStartChange: (LocalDate) -> Unit,
    onScrollStarted: () -> Unit,
) {
    MizunomiTabList(contentPadding = contentPadding, onScrollStarted = onScrollStarted) {
        item { TabHeader(title = "履歴", subtitle = "最近の記録と7日間の変化") }
        item {
            WeeklyTrendCard(
                days = weeklyTrend,
                weekStart = displayedWeekStart,
                currentWeekStart = currentWeekStart,
                dailyGoalMl = dailyGoalMl,
                onWeekStartChange = onDisplayedWeekStartChange,
            )
        }
        if (drinkSummaries.isNotEmpty()) {
            item { TypeSummaryCard(summaries = drinkSummaries) }
        }
        item {
            Text(
                text = "最近の記録",
                color = MizunomiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (recentRecords.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            items(recentRecords, key = { it.id }) { record ->
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
    onScrollStarted: () -> Unit,
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

    MizunomiTabList(contentPadding = contentPadding, onScrollStarted = onScrollStarted) {
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
    onScrollStarted: () -> Unit,
    onScrollInProgressChanged: (Boolean) -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrollInProgress ->
                if (isScrollInProgress) {
                    onScrollStarted()
                }
                onScrollInProgressChanged(isScrollInProgress)
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MizunomiColors.ScreenBackground)
            .padding(contentPadding),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

private fun Modifier.dismissSnackbarOnPointerDown(
    snackbarHostState: SnackbarHostState,
): Modifier = pointerInput(snackbarHostState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (
                snackbarHostState.currentSnackbarData != null &&
                event.changes.any { it.changedToDownIgnoreConsumed() }
            ) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }
}

private fun buildRecordSnackbarMessage(
    feedback: RecordSnackbarFeedback,
    dailyGoalMl: Int,
): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val totalText = numberFormat.format(feedback.dayTotalMl)
    val goalText = numberFormat.format(dailyGoalMl)
    val remainingMl = (dailyGoalMl - feedback.dayTotalMl).coerceAtLeast(0)
    val drinkLabel = drinkTypeDisplayLabel(feedback.drinkType)

    return if (feedback.dayTotalMl >= dailyGoalMl) {
        "今日の目標を達成しました\n" +
            "$drinkLabel ${numberFormat.format(feedback.amountMl)}mlを記録・${totalText}ml達成"
    } else {
        "$drinkLabel ${numberFormat.format(feedback.amountMl)}mlを記録しました\n" +
            "今日の合計 ${totalText}ml / ${goalText}ml・あと${numberFormat.format(remainingMl)}ml"
    }
}

@Composable
private fun TabHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = MizunomiColors.AccentBlueDeep,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = MizunomiColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = "まだ記録がありません。記録タブから最初の一杯を追加しましょう。",
            modifier = Modifier.padding(18.dp),
            color = MizunomiColors.TextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "基本設定",
                color = MizunomiColors.TextPrimary,
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
                color = MizunomiColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "音声入力は端末の音声認識機能を利用します。\n認識精度や通信の有無は端末環境に依存します。",
                color = MizunomiColors.TextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "生活リズム",
                color = MizunomiColors.TextPrimary,
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
                    color = MizunomiColors.DangerRed,
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
            Text(text = label, color = MizunomiColors.TextBody, fontWeight = FontWeight.Medium)
            Text(
                text = supportingText,
                color = MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "$value  ›",
            color = MizunomiColors.AccentBlueSelected,
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
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
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
                        color = MizunomiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "記録を端末に保存",
                        color = MizunomiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MizunomiColors.SoftBlueBackgroundAlt),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = "CSV",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = MizunomiColors.AccentBlueSelected,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = "すべての水分記録を、日時・飲み物・量・メモを含むCSVファイルに書き出します。",
                color = MizunomiColors.TextSecondaryDark,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MizunomiColors.AccentBlueStrong),
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
                    color = MizunomiColors.AchievedGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                is CsvExportStatus.Error -> Text(
                    text = exportStatus.message,
                    color = MizunomiColors.DangerRed,
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
                        color = MizunomiColors.AchievedGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${importStatus.skippedCount}件をスキップ（重複 ${importStatus.duplicateCount}件）",
                        color = MizunomiColors.TextSecondaryDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (importStatus.unknownDrinkTypeCount > 0) {
                        Text(
                            text = "未知の飲み物 ${importStatus.unknownDrinkTypeCount}件を「その他」で読み込みました",
                            color = MizunomiColors.TextSecondaryDark,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is CsvImportStatus.Error -> Text(
                    text = importStatus.message,
                    color = MizunomiColors.DangerRed,
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
                        color = MizunomiColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    preview.errors.forEach { error ->
                        Text(
                            text = "${error.rowNumber}行目: ${error.reason}",
                            color = MizunomiColors.WarningAmberText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.hiddenErrorCount > 0) {
                        Text(
                            text = "ほか${preview.hiddenErrorCount}件のエラーがあります",
                            color = MizunomiColors.WarningAmberText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (preview.records.isEmpty()) {
                    Text(
                        text = "読み込み可能な記録がありません。",
                        color = MizunomiColors.DangerRed,
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
        Text(text = label, color = MizunomiColors.TextSecondaryDark)
        Text(text = "$count 件", color = MizunomiColors.SuccessGreenDeep, fontWeight = FontWeight.Bold)
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
                color = MizunomiColors.TextBody,
            )
            Text(
                text = "タップして変更",
                color = MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "${numberFormat.format(dailyGoalMl)} ml  ›",
            color = MizunomiColors.AccentBlueSelected,
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
                    color = MizunomiColors.TextSecondary,
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
                            color = MizunomiColors.TextPrimary,
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
                color = MizunomiColors.TextBody,
            )
            Text(
                text = if (enabled) "ON" else "OFF",
                color = if (enabled) MizunomiColors.AccentBlueSelected else MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MizunomiColors.CardBackground,
                checkedTrackColor = MizunomiColors.AccentBlueStrong,
                uncheckedThumbColor = MizunomiColors.CardBackground,
                uncheckedTrackColor = MizunomiColors.BorderMuted,
                uncheckedBorderColor = MizunomiColors.BorderMuted,
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
            Text(text = "音声入力の結果")
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
                        text = "認識結果：$rawText",
                        color = MizunomiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (state.candidate != null && !isEditing) {
                    Text(
                        text = "${drinkTypeDisplayLabel(state.candidate.drinkType)} ${state.candidate.amountMl}ml として記録しますか？",
                        color = MizunomiColors.TextBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = state.errorMessage
                            ?: "飲み物と量を選んでください。",
                        color = MizunomiColors.TextBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ChoiceRow(
                        values = drinkTypes,
                        selectedValue = selectedDrinkType,
                        onSelected = { selectedDrinkType = it },
                        labelForValue = ::drinkTypeDisplayLabel,
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
                    Text(text = "保存")
                }
            } else {
                TextButton(
                    onClick = { onSave(selectedDrinkType, selectedAmountMl) },
                ) {
                    Text(text = "保存")
                }
            }
        },
        dismissButton = {
            if (state.candidate != null && !isEditing) {
                TextButton(onClick = { isEditing = true }) {
                    Text(text = "修正")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = "キャンセル")
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
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "飲み物バランス",
                color = MizunomiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            notices.forEach { notice ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = notice.title,
                        color = MizunomiColors.WarningAmberText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = notice.message,
                        color = MizunomiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaceStatusCard(status: PaceStatus) {
    val statusColor = paceStateColor(status.state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "今日のペース",
                color = MizunomiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "目安：${status.targetTimeLabel}までに",
                    color = MizunomiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${status.expectedMl} ml",
                    color = MizunomiColors.TextPrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "現在：${status.actualMl} ml",
                    color = MizunomiColors.TextBody,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (status.remainingMl > 0) {
                        "あと ${status.remainingMl} ml"
                    } else {
                        "順調です"
                    },
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = status.message,
                color = statusColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status.detail,
                color = MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun paceStateColor(state: PaceState): Color =
    when (state) {
        PaceState.OnTrack -> MizunomiColors.AchievedGreen
        PaceState.SlightlyBehind -> MizunomiColors.WarningAmber
        PaceState.Behind -> MizunomiColors.DangerRed
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
            Text(text = "記録を編集")
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
                    color = MizunomiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                ChoiceRow(
                    values = drinkTypes,
                    selectedValue = selectedDrinkType,
                    onSelected = { selectedDrinkType = it },
                    labelForValue = ::drinkTypeDisplayLabel,
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
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "キャンセル")
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
            Text(text = "この記録を削除しますか？")
        },
        text = {
            Text(
                text = "${drinkTypeDisplayLabel(record.drinkType)} ${record.amountMl} ml",
                color = MizunomiColors.TextBody,
                fontWeight = FontWeight.SemiBold,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(text = "削除", color = MizunomiColors.DangerRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "キャンセル")
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
            containerColor = if (isGoalAchieved) MizunomiColors.GoalAchievedBackground else MizunomiColors.CardBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "今日の水分量",
                color = MizunomiColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$todayTotalMl ml",
                color = MizunomiColors.TextPrimaryDark,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 44.sp,
            )
            if (isGoalAchieved) {
                AchievementBadge()
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (isGoalAchieved) MizunomiColors.SuccessGreen else MizunomiColors.AccentBlueProgress,
                trackColor = MizunomiColors.ProgressTrack,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "目標 ${numberFormat.format(dailyGoalMl)} ml",
                    color = MizunomiColors.TextSecondary,
                )
                Text(text = "$progressPercent%", color = MizunomiColors.AccentBlue, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = if (isGoalAchieved) {
                    "おめでとうございます！"
                } else {
                    "あと $remainingMl ml"
                },
                color = if (isGoalAchieved) MizunomiColors.SuccessGreenText else MizunomiColors.AccentBlueMessage,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AchievementBadge() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.SuccessBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓",
                color = MizunomiColors.AchievedGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Column {
                Text(
                    text = "今日の目標を達成しました！",
                    color = MizunomiColors.SuccessGreenDark,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "達成バッジ",
                    color = MizunomiColors.SuccessGreenBody,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(
    days: List<DailyIntake>,
    weekStart: LocalDate,
    currentWeekStart: LocalDate,
    dailyGoalMl: Int,
    onWeekStartChange: (LocalDate) -> Unit,
) {
    val maxBarAmount = maxOf(dailyGoalMl, days.maxOfOrNull { it.amountMl } ?: 0)
    val weeklyTotalMl = days.sumOf { it.amountMl }
    val dailyAverageMl = if (days.isEmpty()) 0 else weeklyTotalMl / days.size
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val normalizedWeekStart = weekStart.startOfWeek()
    val normalizedCurrentWeekStart = currentWeekStart.startOfWeek()
    val isCurrentWeek = normalizedWeekStart == normalizedCurrentWeekStart
    val canGoToNextWeek = normalizedWeekStart.isBefore(normalizedCurrentWeekStart)
    val weekEnd = normalizedWeekStart.plusDays(WeeklyTrendDays - 1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(normalizedWeekStart, canGoToNextWeek) {
                val swipeThreshold = 48.dp.toPx()
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag > swipeThreshold ->
                                onWeekStartChange(normalizedWeekStart.minusWeeks(1))

                            totalDrag < -swipeThreshold && canGoToNextWeek ->
                                onWeekStartChange(normalizedWeekStart.plusWeeks(1))
                        }
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "週間の水分摂取",
                        color = MizunomiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrentWeek) {
                        Text(
                            text = "今週",
                            color = MizunomiColors.AccentBlue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!isCurrentWeek) {
                    TextButton(onClick = { onWeekStartChange(normalizedCurrentWeekStart) }) {
                        Text(text = "今週へ戻る")
                    }
                }
            }

            Text(
                text = "${normalizedWeekStart.format(WeekRangeFormatter)} - " +
                    weekEnd.format(WeekRangeFormatter),
                color = MizunomiColors.TextSecondaryDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { onWeekStartChange(normalizedWeekStart.minusWeeks(1)) },
                ) {
                    Text(text = "‹ 前の週")
                }
                TextButton(
                    onClick = { onWeekStartChange(normalizedWeekStart.plusWeeks(1)) },
                    enabled = canGoToNextWeek,
                ) {
                    Text(text = "次の週 ›")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                WeeklyMetric(
                    label = "合計",
                    value = "${numberFormat.format(weeklyTotalMl)}ml",
                    modifier = Modifier.weight(1f),
                )
                WeeklyMetric(
                    label = "平均",
                    value = "${numberFormat.format(dailyAverageMl)}ml/日",
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "目標 ${numberFormat.format(dailyGoalMl)}ml/日",
                color = MizunomiColors.TextMutedBlue,
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    WeeklyBarColumn(
                        day = day,
                        maxBarAmount = maxBarAmount,
                        dailyGoalMl = dailyGoalMl,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MizunomiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = MizunomiColors.TextPrimaryNavy,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeeklyBarColumn(
    day: DailyIntake,
    maxBarAmount: Int,
    dailyGoalMl: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (maxBarAmount == 0) {
        0f
    } else {
        (day.amountMl.toFloat() / maxBarAmount).coerceIn(0f, 1f)
    }
    val barColor = when {
        day.amountMl >= dailyGoalMl -> MizunomiColors.SuccessGreen
        day.isToday -> MizunomiColors.AccentBlueStrong
        else -> MizunomiColors.AccentBlueLight
    }
    val labelColor = if (day.isToday) MizunomiColors.AccentBlue else MizunomiColors.TextSecondary

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = if (day.isToday) "今日" else "",
            color = MizunomiColors.AccentBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            text = day.amountMl.toString(),
            color = if (day.isToday) MizunomiColors.TextPrimaryNavy else MizunomiColors.TextMutedBlue,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp)
                .background(
                    color = MizunomiColors.BarTrack,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .background(
                            color = barColor,
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
            }
        }
        Text(
            text = day.dayLabel,
            color = labelColor,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = day.dateLabel,
            color = labelColor,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TypeSummaryCard(summaries: List<DrinkSummary>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "飲み物別",
                color = MizunomiColors.TextPrimary,
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
                        text = drinkTypeDisplayLabel(summary.drinkType),
                        color = MizunomiColors.TextBody,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${summary.amountMl} ml",
                        color = MizunomiColors.TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AddIntakeCard(
    drinkTypes: List<String>,
    amounts: List<Int>,
    selectedDrinkType: String,
    onDrinkTypeSelected: (String) -> Unit,
    selectedRecordDate: LocalDate,
    selectedRecordTime: LocalTime?,
    recordDateTimeError: String?,
    autoScrollToAmountRequest: Int,
    autoScrollToAmountEnabled: Boolean,
    onRecordDateSelected: (LocalDate) -> Unit,
    onUseCurrentTime: () -> Unit,
    onRecordTimeSelected: (LocalTime) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onVoiceInput: () -> Unit,
) {
    val amountAreaRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(autoScrollToAmountRequest, autoScrollToAmountEnabled) {
        if (autoScrollToAmountRequest > 0 && autoScrollToAmountEnabled) {
            delay(80)
            amountAreaRequester.bringIntoView()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "クイック追加",
                color = MizunomiColors.TextPrimary,
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
                    text = "🎤 音声で記録",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ChoiceRow(
                values = drinkTypes,
                selectedValue = selectedDrinkType,
                onSelected = onDrinkTypeSelected,
                labelForValue = ::drinkTypeDisplayLabel,
            )
            AmountSelectionPrompt(
                selectedDrinkType = selectedDrinkType,
                amounts = amounts,
                onQuickAdd = onQuickAdd,
                modifier = Modifier.bringIntoViewRequester(amountAreaRequester),
            )
        }
    }
}

@Composable
private fun AmountSelectionPrompt(
    selectedDrinkType: String,
    amounts: List<Int>,
    onQuickAdd: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MizunomiColors.SoftBlueBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${drinkTypeDisplayLabel(selectedDrinkType)}を選択中",
                    color = MizunomiColors.AccentBlueText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "量を選んで記録してください",
                    color = MizunomiColors.AccentBlueBody,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        QuickAmountGrid(
            amounts = amounts,
            onQuickAdd = onQuickAdd,
        )
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
            colors = CardDefaults.cardColors(containerColor = MizunomiColors.SoftBlueBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "記録日時",
                    color = MizunomiColors.AccentBlueBody,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$displayDate ${displayTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = MizunomiColors.AccentBlueText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "日付",
            color = MizunomiColors.TextSecondaryDark,
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
            color = MizunomiColors.TextSecondaryDark,
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
                color = MizunomiColors.DangerRed,
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
    labelForValue: (String) -> String = { it },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowValues.forEach { value ->
                    SelectButton(
                        text = labelForValue(value),
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
                        colors = ButtonDefaults.buttonColors(containerColor = MizunomiColors.AccentBlueStrong),
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
            containerColor = if (selected) MizunomiColors.SelectedBackground else MizunomiColors.CardBackground,
            contentColor = if (selected) MizunomiColors.AccentBlueSelected else MizunomiColors.TextBody,
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
        colors = CardDefaults.cardColors(containerColor = MizunomiColors.CardBackground),
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
                    text = drinkTypeDisplayLabel(record.drinkType),
                    color = MizunomiColors.TextBodyDark,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = record.timestamp.toRecordDateTimeText(),
                    color = MizunomiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.amountMl} ml",
                    color = MizunomiColors.TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { onEdit(record) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(text = "編集", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onDelete(record) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            text = "削除",
                            color = MizunomiColors.DangerRed,
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

private fun formatTimeMinutes(minutes: Int): String =
    "%02d:%02d".format(Locale.JAPAN, minutes / 60, minutes % 60)

private fun buildVoiceRecognitionIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "飲み物と量を話してください")
    }

private fun buildVoiceInputState(text: String?): VoiceInputState =
    if (text.isNullOrBlank()) {
        VoiceInputState(
            rawText = text,
            candidate = null,
            errorMessage = "うまく読み取れませんでした。飲み物と量を選んでください。",
        )
    } else {
        val candidate = parseVoiceIntake(text)
        VoiceInputState(
            rawText = text,
            candidate = candidate,
            errorMessage = if (candidate == null) {
                "うまく読み取れませんでした。飲み物と量を選んでください。"
            } else {
                null
            },
        )
    }

private data class DrinkSummary(
    val drinkType: String,
    val amountMl: Int,
)

private data class VoiceInputState(
    val rawText: String?,
    val candidate: VoiceIntakeCandidate?,
    val errorMessage: String?,
)

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home(label = "ホーム", icon = Icons.Filled.Home),
    Record(label = "記録", icon = Icons.Filled.Add),
    History(label = "履歴", icon = Icons.AutoMirrored.Filled.List),
    Settings(label = "設定", icon = Icons.Filled.Settings),
}

@Preview(showBackground = true)
@Composable
private fun MizunomiAppPreview() {
    MizunomiAppContent(
        todayTotalMl = 400,
        todayRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "水",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
            IntakeRecord(
                id = 2,
                drinkType = "お茶",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        recentRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "水",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        weeklyRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "水",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        displayedWeekStart = LocalDate.now().startOfWeek(),
        currentWeekStart = LocalDate.now().startOfWeek(),
        reminderEnabled = true,
        dailyGoalMl = DefaultDailyGoalMl,
        wakeTimeMinutes = DefaultWakeTimeMinutes,
        bedTimeMinutes = DefaultBedTimeMinutes,
        coachUiState = CoachUiState.Idle,
        currentDate = LocalDate.now(),
        onRefreshCurrentDate = {},
        onAddRecord = { _, amountMl, _, _, onSaved, _ -> onSaved(99, 400 + amountMl) },
        onUpdateRecord = { _, _, _ -> },
        onDeleteRecord = {},
        onDeleteRecordById = { _, onSuccess, _ -> onSuccess() },
        onReminderEnabledChange = {},
        onDailyGoalChange = {},
        onWakeTimeChange = {},
        onBedTimeChange = {},
        onCoachAdviceRequest = {},
        onCoachErrorShown = {},
        onDisplayedWeekStartChange = {},
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
