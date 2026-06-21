package com.tekutekunikki.mizunomi

import android.os.Bundle
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val DailyGoalMl = 2000
private const val WeeklyTrendDays = 7L

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        IntakeRecordRepository(
            MizunomiDatabase.getInstance(this).intakeRecordDao(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MizunomiApp(repository = repository)
        }
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
    )
}

@Composable
fun MizunomiAppContent(
    todayTotalMl: Int,
    todayRecords: List<IntakeRecord>,
    weeklyRecords: List<IntakeRecord>,
    onAddRecord: (drinkType: String, amountMl: Int) -> Unit,
) {
    val drinkTypes = listOf(
        "\u6C34",
        "\u304A\u8336",
        "\u30B3\u30FC\u30D2\u30FC",
        "\u305D\u306E\u4ED6",
    )
    val amounts = listOf(100, 200, 300, 500)
    var selectedDrinkType by remember { mutableStateOf(drinkTypes.first()) }
    val remainingMl = (DailyGoalMl - todayTotalMl).coerceAtLeast(0)
    val progress = (todayTotalMl.toFloat() / DailyGoalMl).coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt()
    val isGoalAchieved = todayTotalMl >= DailyGoalMl
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
                        IntakeRecordRow(record = record)
                    }
                }
            }
        }
    }
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
private fun IntakeRecordRow(record: IntakeRecord) {
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
            Column {
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
            Text(
                text = "${record.amountMl} ml",
                color = Color(0xFF0F2F47),
                fontWeight = FontWeight.Bold,
            )
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
    )
}
