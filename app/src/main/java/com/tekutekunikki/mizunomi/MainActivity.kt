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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WeeklyTrendDays = 7L
private const val PastRecordLimitDays = 30L
private const val SweetDrinkWarningThresholdMl = 500
private const val BalancedDrinkTotalThresholdMl = 1000
private const val WaterTeaMinimumThresholdMl = 500
internal const val VoiceAmountMinMl = 50
internal const val VoiceAmountMaxMl = 2_000
private val MonthDayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)
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

private val DrinkTypeIcons = mapOf(
    "\u6C34" to "\uD83D\uDCA7",
    "\u304A\u8336" to "\uD83C\uDF75",
    "\u30B3\u30FC\u30D2\u30FC" to "\u2615",
    "\u30B8\u30E5\u30FC\u30B9" to "\uD83E\uDDC3",
    "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF" to "\uD83C\uDFC3",
    "\u4E73\u98F2\u6599" to "\uD83E\uDD5B",
    "\u70AD\u9178\u98F2\u6599" to "\uD83E\uDEE7",
    "\u30A2\u30EB\u30B3\u30FC\u30EB" to "\uD83C\uDF7A",
    "\u305D\u306E\u4ED6" to "\uD83E\uDD64",
)

private const val DefaultDrinkTypeIcon = "\uD83E\uDD64"

private fun drinkTypeDisplayLabel(drinkType: String): String =
    "${DrinkTypeIcons[drinkType] ?: DefaultDrinkTypeIcon} $drinkType"

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

private val HomeQuickRecordOptions = listOf(
    HomeQuickRecordOption(drinkType = "\u6C34", amountMl = 100),
    HomeQuickRecordOption(drinkType = "\u304A\u8336", amountMl = 100),
    HomeQuickRecordOption(drinkType = "\u30B3\u30FC\u30D2\u30FC", amountMl = 200),
)

private val HydrationGuideTopics = listOf(
    HydrationGuideTopic(
        title = "\u3053\u307E\u3081\u306B\u98F2\u3080",
        description = "\u4E00\u5EA6\u306B\u305F\u304F\u3055\u3093\u3067\u306F\u306A\u304F\u3001\u5C11\u3057\u305A\u3064\u6C34\u5206\u3092\u3068\u308B\u3068\u7D9A\u3051\u3084\u3059\u304F\u306A\u308A\u307E\u3059\u3002",
        imageResId = R.drawable.hydrate_often,
        contentDescription = "\u3053\u307E\u3081\u306A\u6C34\u5206\u88DC\u7D66\u3092\u6848\u5185\u3059\u308B\u30A4\u30E9\u30B9\u30C8",
    ),
    HydrationGuideTopic(
        title = "\u8D77\u5E8A\u5F8C\u306B1\u676F",
        description = "\u671D\u306E\u751F\u6D3B\u30EA\u30BA\u30E0\u306B\u5408\u308F\u305B\u3066\u3001\u307E\u305A\u306F1\u676F\u304B\u3089\u59CB\u3081\u307E\u3057\u3087\u3046\u3002",
        imageResId = R.drawable.morning_water,
        contentDescription = "\u8D77\u5E8A\u5F8C\u306E\u6C34\u5206\u88DC\u7D66\u3092\u6848\u5185\u3059\u308B\u30A4\u30E9\u30B9\u30C8",
    ),
    HydrationGuideTopic(
        title = "\u904B\u52D5\u524D\u5F8C\u306B\u88DC\u7D66",
        description = "\u4F53\u3092\u52D5\u304B\u3059\u524D\u5F8C\u306F\u3001\u6C34\u5206\u88DC\u7D66\u3092\u610F\u8B58\u3057\u3084\u3059\u3044\u30BF\u30A4\u30DF\u30F3\u30B0\u3067\u3059\u3002",
        imageResId = R.drawable.exercise_hydration,
        contentDescription = "\u904B\u52D5\u524D\u5F8C\u306E\u6C34\u5206\u88DC\u7D66\u3092\u6848\u5185\u3059\u308B\u30A4\u30E9\u30B9\u30C8",
    ),
    HydrationGuideTopic(
        title = "\u5165\u6D74\u524D\u5F8C\u3082\u610F\u8B58",
        description = "\u5165\u6D74\u306E\u524D\u5F8C\u306F\u3001\u5FD8\u308C\u305A\u306B\u6C34\u5206\u3092\u3068\u308B\u304D\u3063\u304B\u3051\u306B\u3057\u307E\u3057\u3087\u3046\u3002",
        imageResId = R.drawable.bath_hydration,
        contentDescription = "\u5165\u6D74\u524D\u5F8C\u306E\u6C34\u5206\u88DC\u7D66\u3092\u6848\u5185\u3059\u308B\u30A4\u30E9\u30B9\u30C8",
    ),
    HydrationGuideTopic(
        title = "\u5C31\u5BDD\u524D\u306F\u63A7\u3048\u3081\u306B",
        description = "\u5BDD\u308B\u524D\u306F\u7121\u7406\u306B\u591A\u304F\u98F2\u307E\u305A\u3001\u6C17\u306B\u306A\u308B\u5834\u5408\u306F\u63A7\u3048\u3081\u306B\u3057\u307E\u3057\u3087\u3046\u3002",
        imageResId = R.drawable.before_sleep_water,
        contentDescription = "\u5C31\u5BDD\u524D\u306E\u63A7\u3048\u3081\u306A\u6C34\u5206\u88DC\u7D66\u3092\u6848\u5185\u3059\u308B\u30A4\u30E9\u30B9\u30C8",
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
    private val mainViewModel by lazy {
        ViewModelProvider(
            this,
            MainViewModel.Factory(
                repository = repository,
                reminderSettingsRepository = reminderSettingsRepository,
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
            .background(Color(0xFFF4F8FB))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.mizunomi_splash02),
            contentDescription = "\u6C34\u5206\u88DC\u7D66\u3092\u4F1D\u3048\u308Bmizunomi\u306E\u30B9\u30D7\u30E9\u30C3\u30B7\u30E5\u753B\u50CF",
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
                actionLabel = "\u5143\u306B\u623B\u3059",
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
                                message = "\u76F4\u524D\u306E\u8A18\u9332\u3092\u5143\u306B\u623B\u3057\u307E\u3057\u305F",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        uiScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "\u5143\u306B\u623B\u305B\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u3082\u3046\u4E00\u5EA6\u304A\u8A66\u3057\u304F\u3060\u3055\u3044",
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
                    recordDateTimeError = "\u8A18\u9332\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F\u3002\u3082\u3046\u4E00\u5EA6\u304A\u8A66\u3057\u304F\u3060\u3055\u3044"
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
            containerColor = Color(0xFFF4F8FB),
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
                        onQuickRecord = addHomeQuickRecord,
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
                                    errorMessage = "\u7AEF\u672B\u306E\u97F3\u58F0\u5165\u529B\u3092\u8D77\u52D5\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F\u3002",
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
    quickRecordOptions: List<HomeQuickRecordOption>,
    quickRecordStatus: HomeQuickRecordStatus?,
    isQuickRecordSaving: Boolean,
    onQuickRecord: (HomeQuickRecordOption) -> Unit,
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
private fun HydrationGuideSection(topics: List<HydrationGuideTopic>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "\u6C34\u5206\u88DC\u7D66\u30AC\u30A4\u30C9",
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "\u6BCE\u65E5\u7D9A\u3051\u3084\u3059\u3044\u30BF\u30A4\u30DF\u30F3\u30B0\u3092\u3001\u30A4\u30E9\u30B9\u30C8\u3067\u78BA\u8A8D\u3067\u304D\u307E\u3059\u3002",
                color = Color(0xFF6C7A86),
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
            text = "\u5FC5\u8981\u306A\u6C34\u5206\u91CF\u306F\u3001\u4F53\u683C\u30FB\u6D3B\u52D5\u91CF\u30FB\u6C17\u6E29\u30FB\u98DF\u4E8B\u5185\u5BB9\u306B\u3088\u3063\u3066\u5909\u308F\u308A\u307E\u3059\u3002\n" +
                "\u4F53\u8ABF\u306B\u4E0D\u5B89\u304C\u3042\u308B\u5834\u5408\u306F\u3001\u7121\u7406\u305B\u305A\u5C02\u9580\u5BB6\u306B\u76F8\u8AC7\u3057\u3066\u304F\u3060\u3055\u3044\u3002",
            color = Color(0xFF6C7A86),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HydrationGuideCard(topic: HydrationGuideTopic) {
    Card(
        modifier = Modifier.width(268.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    .background(Color(0xFFEAF5FC)),
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
                color = Color(0xFF25384A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = topic.description,
                color = Color(0xFF526777),
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        color = Color(0xFF25384A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "よく使う記録をワンタップで追加",
                        color = Color(0xFF6C7A86),
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
                            containerColor = Color(0xFFF7FBFE),
                            contentColor = Color(0xFF116DAE),
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
                        color = Color(0xFF168344),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HomeQuickRecordStatus.Error -> {
                    Text(
                        text = "記録できませんでした。もう一度お試しください",
                        color = Color(0xFFB3261E),
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
                    append(drinkTypeDisplayLabel(feedback.drinkType))
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
            .background(Color(0xFFF4F8FB))
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
        "\u4ECA\u65E5\u306E\u76EE\u6A19\u3092\u9054\u6210\u3057\u307E\u3057\u305F\n" +
            "$drinkLabel ${numberFormat.format(feedback.amountMl)}ml\u3092\u8A18\u9332\u30FB${totalText}ml\u9054\u6210"
    } else {
        "$drinkLabel ${numberFormat.format(feedback.amountMl)}ml\u3092\u8A18\u9332\u3057\u307E\u3057\u305F\n" +
            "\u4ECA\u65E5\u306E\u5408\u8A08 ${totalText}ml / ${goalText}ml\u30FB\u3042\u3068${numberFormat.format(remainingMl)}ml"
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
                        text = "${drinkTypeDisplayLabel(state.candidate.drinkType)} ${state.candidate.amountMl}ml \u3068\u3057\u3066\u8A18\u9332\u3057\u307E\u3059\u304B\uFF1F",
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
                text = "${drinkTypeDisplayLabel(record.drinkType)} ${record.amountMl} ml",
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
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Today's intake",
                color = Color(0xFF6C7A86),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$todayTotalMl ml",
                color = Color(0xFF0F2F47),
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        text = "\u9031\u9593\u306E\u6C34\u5206\u6442\u53D6",
                        color = Color(0xFF25384A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrentWeek) {
                        Text(
                            text = "\u4ECA\u9031",
                            color = Color(0xFF0F6FAE),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!isCurrentWeek) {
                    TextButton(onClick = { onWeekStartChange(normalizedCurrentWeekStart) }) {
                        Text(text = "\u4ECA\u9031\u3078\u623B\u308B")
                    }
                }
            }

            Text(
                text = "${normalizedWeekStart.format(WeekRangeFormatter)} - " +
                    weekEnd.format(WeekRangeFormatter),
                color = Color(0xFF526777),
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
                    Text(text = "\u2039 \u524D\u306E\u9031")
                }
                TextButton(
                    onClick = { onWeekStartChange(normalizedWeekStart.plusWeeks(1)) },
                    enabled = canGoToNextWeek,
                ) {
                    Text(text = "\u6B21\u306E\u9031 \u203A")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                WeeklyMetric(
                    label = "\u5408\u8A08",
                    value = "${numberFormat.format(weeklyTotalMl)}ml",
                    modifier = Modifier.weight(1f),
                )
                WeeklyMetric(
                    label = "\u5E73\u5747",
                    value = "${numberFormat.format(dailyAverageMl)}ml/\u65E5",
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "\u76EE\u6A19 ${numberFormat.format(dailyGoalMl)}ml/\u65E5",
                color = Color(0xFF5C6F7E),
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
            color = Color(0xFF6C7A86),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = Color(0xFF17324A),
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
        day.amountMl >= dailyGoalMl -> Color(0xFF2EAD5B)
        day.isToday -> Color(0xFF1683D8)
        else -> Color(0xFF76B9E8)
    }
    val labelColor = if (day.isToday) Color(0xFF0F6FAE) else Color(0xFF6C7A86)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = if (day.isToday) "\u4ECA\u65E5" else "",
            color = Color(0xFF0F6FAE),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            text = day.amountMl.toString(),
            color = if (day.isToday) Color(0xFF17324A) else Color(0xFF5C6F7E),
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp)
                .background(
                    color = Color(0xFFEAF2F7),
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
                        text = drinkTypeDisplayLabel(summary.drinkType),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${drinkTypeDisplayLabel(selectedDrinkType)}\u3092\u9078\u629E\u4E2D",
                    color = Color(0xFF0F5F94),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "\u91CF\u3092\u9078\u3093\u3067\u8A18\u9332\u3057\u3066\u304F\u3060\u3055\u3044",
                    color = Color(0xFF527189),
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
                    text = drinkTypeDisplayLabel(record.drinkType),
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

private fun buildWeeklyTrend(
    records: List<IntakeRecord>,
    displayedWeekStart: LocalDate,
): List<DailyIntake> {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()
    val weekStart = displayedWeekStart.startOfWeek()
    val totalsByDate = records.groupBy {
        Instant.ofEpochMilli(it.timestamp)
            .atZone(zoneId)
            .toLocalDate()
    }.mapValues { (_, dayRecords) ->
        dayRecords.sumOf { it.amountMl }
    }

    return (0L until WeeklyTrendDays).map { dayOffset ->
        val date = weekStart.plusDays(dayOffset)
        DailyIntake(
            date = date,
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN),
            dateLabel = date.format(MonthDayFormatter),
            amountMl = totalsByDate[date] ?: 0,
            isToday = date == today,
        )
    }
}

private fun LocalDate.startOfWeek(): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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

internal fun parseVoiceIntake(text: String): VoiceIntakeCandidate? {
    val normalizedText = text.normalizeDigits()
    val amountMl = Regex("\\d+")
        .findAll(normalizedText)
        .mapNotNull { match -> match.value.toIntOrNull() }
        .firstOrNull { amount -> amount in VoiceAmountMinMl..VoiceAmountMaxMl }
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

internal data class VoiceIntakeCandidate(
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
    val dateLabel: String,
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
        weeklyRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "\u6C34",
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
