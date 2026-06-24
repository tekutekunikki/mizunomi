package com.tekutekunikki.mizunomi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tekutekunikki.mizunomi.data.IntakeRecord
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val DailyGoalMl = 2000
private const val WeeklyTrendDays = 7L

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HydrationReminderNotifications.createChannel(this)
        HydrationReminderScheduler.scheduleDailyChecks(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            MizunomiApp(repository = repository)
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
fun MizunomiApp(repository: IntakeRecordRepository) {
    val todayTotalMl by repository.observeTotalAmountForDay(LocalDate.now())
        .collectAsState(initial = 0)
    val todayRecords by repository.observeTodayRecords()
        .collectAsState(initial = emptyList())
    val weeklyRecords by repository.observeRecentRecords(WeeklyTrendDays)
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    MizunomiAppContent(
        todayTotalMl = todayTotalMl,
        todayRecords = todayRecords,
        weeklyRecords = weeklyRecords,
        onAddRecord = { drinkType, amountMl ->
            scope.launch {
                repository.addRecord(
                    drinkType = drinkType,
                    amountMl = amountMl,
                )
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
    )
}

@Composable
fun MizunomiAppContent(
    todayTotalMl: Int,
    todayRecords: List<IntakeRecord>,
    weeklyRecords: List<IntakeRecord>,
    onAddRecord: (drinkType: String, amountMl: Int) -> Unit,
    onUpdateRecord: (record: IntakeRecord, drinkType: String, amountMl: Int) -> Unit,
    onDeleteRecord: (record: IntakeRecord) -> Unit,
) {
    val drinkTypes = listOf(
        "\u6C34",
        "\u304A\u8336",
        "\u30B3\u30FC\u30D2\u30FC",
        "\u305D\u306E\u4ED6",
    )
    val amounts = listOf(100, 200, 300, 500)
    var selectedDrinkType by remember { mutableStateOf(drinkTypes.first()) }
    var editingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    val remainingMl = (DailyGoalMl - todayTotalMl).coerceAtLeast(0)
    val progress = (todayTotalMl.toFloat() / DailyGoalMl).coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt()
    val isGoalAchieved = todayTotalMl >= DailyGoalMl
    val paceStatus = buildPaceStatus(todayTotalMl, LocalTime.now())
    val weeklyTrend = remember(weeklyRecords) {
        buildWeeklyTrend(weeklyRecords)
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

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F8FB)),
            color = Color(0xFFF4F8FB),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = "mizunomi",
                        color = Color(0xFF13314B),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                item {
                    SummaryCard(
                        todayTotalMl = todayTotalMl,
                        remainingMl = remainingMl,
                        progress = progress,
                        progressPercent = progressPercent,
                        isGoalAchieved = isGoalAchieved,
                    )
                }

                item {
                    PaceStatusCard(status = paceStatus)
                }

                item {
                    AddIntakeCard(
                        drinkTypes = drinkTypes,
                        amounts = amounts,
                        selectedDrinkType = selectedDrinkType,
                        onDrinkTypeSelected = { selectedDrinkType = it },
                        onQuickAdd = { amountMl -> onAddRecord(selectedDrinkType, amountMl) },
                    )
                }

                item {
                    WeeklyTrendCard(days = weeklyTrend)
                }

                if (drinkSummaries.isNotEmpty()) {
                    item {
                        TypeSummaryCard(summaries = drinkSummaries)
                    }
                }

                item {
                    Text(
                        text = "Recent records",
                        color = Color(0xFF25384A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (todayRecords.isEmpty()) {
                    item {
                        Text(
                            text = "No records yet.",
                            color = Color(0xFF6C7A86),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(todayRecords.take(6), key = { it.id }) { record ->
                        IntakeRecordRow(
                            record = record,
                            onEdit = { editingRecord = it },
                            onDelete = { deletingRecord = it },
                        )
                    }
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = record.timestamp.toTimeText(),
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
) {
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
                Text(text = "Goal 2,000 ml", color = Color(0xFF6C7A86))
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
private fun WeeklyTrendCard(days: List<DailyIntake>) {
    val maxBarAmount = maxOf(DailyGoalMl, days.maxOfOrNull { it.amountMl } ?: 0)

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
                TrendBarRow(day = day, maxBarAmount = maxBarAmount)
            }
        }
    }
}

@Composable
private fun TrendBarRow(
    day: DailyIntake,
    maxBarAmount: Int,
) {
    val progress = (day.amountMl.toFloat() / maxBarAmount).coerceIn(0f, 1f)
    val barColor = when {
        day.amountMl >= DailyGoalMl -> Color(0xFF2EAD5B)
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
            if (day.amountMl >= DailyGoalMl) {
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
    onQuickAdd: (Int) -> Unit,
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
private fun ChoiceRow(
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            SelectButton(
                text = value,
                selected = selectedValue == value,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(value) },
            )
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
            fontSize = 14.sp,
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
                    text = record.timestamp.toTimeText(),
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

private fun Long.toTimeText(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

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
): PaceStatus {
    val target = paceTargets.lastOrNull { !now.isBefore(it.time) } ?: paceTargets.first()
    val remainingMl = (target.expectedMl - actualMl).coerceAtLeast(0)
    val state = when {
        actualMl >= target.expectedMl -> PaceState.OnTrack
        target.expectedMl < 300 -> PaceState.OnTrack
        remainingMl < 300 -> PaceState.SlightlyBehind
        else -> PaceState.Behind
    }

    return PaceStatus(
        targetTimeLabel = target.time.format(DateTimeFormatter.ofPattern("H:mm")),
        expectedMl = target.expectedMl,
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

private val paceTargets = listOf(
    PaceTarget(time = LocalTime.of(8, 0), expectedMl = 0),
    PaceTarget(time = LocalTime.of(10, 0), expectedMl = 300),
    PaceTarget(time = LocalTime.of(12, 0), expectedMl = 700),
    PaceTarget(time = LocalTime.of(15, 0), expectedMl = 1100),
    PaceTarget(time = LocalTime.of(18, 0), expectedMl = 1500),
    PaceTarget(time = LocalTime.of(21, 0), expectedMl = 1900),
    PaceTarget(time = LocalTime.of(22, 0), expectedMl = 2000),
)

private data class DrinkSummary(
    val drinkType: String,
    val amountMl: Int,
)

private data class DailyIntake(
    val date: LocalDate,
    val dayLabel: String,
    val amountMl: Int,
    val isToday: Boolean,
)

private data class PaceTarget(
    val time: LocalTime,
    val expectedMl: Int,
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
        weeklyRecords = listOf(
            IntakeRecord(
                id = 1,
                drinkType = "\u6C34",
                amountMl = 200,
                timestamp = 0,
                memo = null,
            ),
        ),
        onAddRecord = { _, _ -> },
        onUpdateRecord = { _, _, _ -> },
        onDeleteRecord = {},
    )
}
