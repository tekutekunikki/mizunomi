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
import androidx.compose.foundation.layout.padding
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
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val DailyGoalMl = 2000

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
    val scope = rememberCoroutineScope()

    MizunomiAppContent(
        todayTotalMl = todayTotalMl,
        todayRecords = todayRecords,
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = Color(0xFF2A9DF4),
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
                text = "\u3042\u3068 $remainingMl ml",
                color = Color(0xFF2D6A9F),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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

private data class DrinkSummary(
    val drinkType: String,
    val amountMl: Int,
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
        onAddRecord = { _, _ -> },
    )
}
