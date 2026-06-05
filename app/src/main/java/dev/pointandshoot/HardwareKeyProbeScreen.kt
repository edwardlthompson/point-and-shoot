package dev.pointandshoot

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.pointandshoot.fleet.ProductHardwareLaunchScan
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "PNS.HardwareKeyProbe"

/**
 * Engineering probe: press each hardware button; exports [ProductHardwareLaunchScan.HARDWARE_KEY_PROBE_FILENAME].
 * ADB: `--es pns_screen hardwarekeyprobe` · auto: `--ez pns_auto_hardware_key_probe true`
 */
@Composable
fun HardwareKeyProbeScreen(
    onBack: () -> Unit,
    autoProbe: Boolean = false,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Press each hardware button (camera key, shortcut key, volume).") }
    val lines = remember { mutableStateListOf<String>() }
    val insets = rememberSystemInsetsDp()

    DisposableEffect(Unit) {
        HardwareKeyProbeRecorder.clear()
        HardwareKeyProbeRecorder.active = true
        onDispose {
            HardwareKeyProbeRecorder.active = false
        }
    }

    BackHandler(onBack = onBack)

    fun refreshLines() {
        lines.clear()
        HardwareKeyProbeRecorder.snapshot().forEach { e ->
            lines += "${e.actionLabel} keyCode=${e.keyCode} scanCode=${e.scanCode} src=${e.source}"
        }
    }

    fun saveProbe(): Boolean {
        refreshLines()
        val events = HardwareKeyProbeRecorder.snapshot()
        if (events.isEmpty()) return false
        val probe = ProductHardwareLaunchScan.buildInteractiveProbeFromEvents(events)
        val md = buildProbeMarkdown(events, probe)
        ProductHardwareLaunchScan.saveInteractiveProbe(context.applicationContext, probe, md)
        status =
            "Saved ${events.size} events → files/${ProductHardwareLaunchScan.HARDWARE_KEY_PROBE_FILENAME}"
        Log.i(TAG, "saved events=${events.size} distinct=${probe.optJSONArray("distinctKeyCodes")}")
        Log.i(SWEEP_SIGNAL_TAG, "HARDWARE_KEY_PROBE_DONE events=${events.size}")
        return true
    }

    LaunchedEffect(autoProbe) {
        if (!autoProbe) return@LaunchedEffect
        status = "Auto probe: waiting for recorder…"
        delay(500)
        HardwareKeyProbeRecorder.handleKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAMERA),
        )
        HardwareKeyProbeRecorder.handleKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA),
        )
        delay(200)
        if (saveProbe()) {
            status = "Auto probe saved KEYCODE_CAMERA → ${ProductHardwareLaunchScan.HARDWARE_KEY_PROBE_FILENAME}"
        } else {
            status = "Auto probe failed — no key events recorded"
            Log.w(TAG, "auto probe save failed events=0")
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(insets.asPaddingValues())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hardware key probe")
        Text(status)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(lines) { line -> Text(line) }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { refreshLines() },
        ) {
            Text("Refresh list")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { saveProbe() },
        ) {
            Text("Save probe JSON")
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        ) {
            Text("Back")
        }
    }
}

private fun buildProbeMarkdown(
    events: List<ProductHardwareLaunchScan.HardwareKeyProbeEvent>,
    probe: org.json.JSONObject,
): String {
    val ts =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
    return buildString {
        appendLine("# Hardware key probe")
        appendLine()
        appendLine("- **Generated:** $ts")
        appendLine("- **Events:** ${events.size}")
        appendLine("- **Distinct keyCodes:** ${probe.optJSONArray("distinctKeyCodes")}")
        appendLine("- **Camera key confirmed:** ${probe.optBoolean("cameraKeyConfirmed")}")
        appendLine("- **Focus key confirmed:** ${probe.optBoolean("focusKeyConfirmed")}")
        appendLine()
        appendLine("## Events")
        events.forEach { e ->
            appendLine("- ${e.actionLabel} keyCode=${e.keyCode} scanCode=${e.scanCode} source=${e.source}")
        }
    }
}
