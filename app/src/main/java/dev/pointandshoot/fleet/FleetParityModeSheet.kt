package dev.pointandshoot.fleet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetParityModeSheet(
    onDismiss: () -> Unit,
    onRun: (FleetParitySweepRunner.Mode, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var includeRecord = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Fleet Parity Sweep",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            ParityModeRow("Full (~15–30 min)", "Every catalog row; optional record saves") {
                includeRecord.value = false
                onRun(FleetParitySweepRunner.Mode.FULL, includeRecord.value)
            }
            ParityModeRow("Delta", "Rows changed since last catalog version") {
                onRun(FleetParitySweepRunner.Mode.DELTA, false)
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Include record saves (Full only)", color = Color.White.copy(alpha = 0.85f))
                Switch(checked = includeRecord.value, onCheckedChange = { includeRecord.value = it })
            }
            ParityModeRow("Full + record saves", "Delivery verification with artifacts") {
                onRun(FleetParitySweepRunner.Mode.FULL, includeRecord.value)
            }
        }
    }
}

@Composable
private fun ParityModeRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
    }
}
