package dev.pointandshoot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun SnapshotStateList<String>.appendProbeLine(line: String, maxLines: Int = 100) {
    // Use Main (not immediate): snapshot list updates must run on the UI thread reliably
    // when the probe runs on Dispatchers.Default.
    withContext(Dispatchers.Main) {
        add(line)
        while (size > maxLines) removeAt(0)
    }
}

@Composable
fun ProbeLiveLogPanel(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            Text(
                text = if (lines.isEmpty()) "…" else lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
            )
        }
    }
}
