package com.tekutekunikki.mizunomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekutekunikki.mizunomi.data.IntakeRecordRepository
import com.tekutekunikki.mizunomi.data.MizunomiDatabase
import java.time.LocalDate
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    MizunomiAppContent(
        todayTotalMl = todayTotalMl,
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
    onAddRecord: (drinkType: String, amountMl: Int) -> Unit,
) {
    val drinkTypes = listOf("水", "お茶", "コーヒー", "その他")
    val amounts = listOf(100, 200, 300, 500)
    var selectedDrinkType by remember { mutableStateOf(drinkTypes.first()) }
    var selectedAmountMl by remember { mutableIntStateOf(amounts[1]) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "mizunomi", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Today's intake: $todayTotalMl ml",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Drink type", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    drinkTypes.forEach { drinkType ->
                        OutlinedButton(
                            onClick = { selectedDrinkType = drinkType },
                            enabled = selectedDrinkType != drinkType,
                        ) {
                            Text(text = drinkType)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Amount", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    amounts.forEach { amountMl ->
                        OutlinedButton(
                            onClick = { selectedAmountMl = amountMl },
                            enabled = selectedAmountMl != amountMl,
                        ) {
                            Text(text = "$amountMl ml")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { onAddRecord(selectedDrinkType, selectedAmountMl) },
                ) {
                    Text(text = "Add")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MizunomiAppPreview() {
    MizunomiAppContent(
        todayTotalMl = 0,
        onAddRecord = { _, _ -> },
    )
}
