package dev.pointandshoot.fleet

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pointandshoot.ProbeLiveLogPanel
import dev.pointandshoot.appendProbeLine
import dev.pointandshoot.asPaddingValues
import dev.pointandshoot.rememberSystemInsetsDp
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "PNS.FleetMatrixHub"

/**
 * Engineering hub — fleet device capability matrix (Milestone **16.2**).
 */
@Composable
fun FleetMatrixHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    val insets = rememberSystemInsetsDp()
    var matrix by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf("Loading…") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }
    var reloadNonce by remember { mutableIntStateOf(0) }

    fun reloadFromDisk() {
        matrix = FleetDeviceMatrixStore.loadValid(appCtx)
            ?: runCatching {
                val f = FleetDeviceMatrixStore.matrixFile(appCtx)
                if (f.exists()) JSONObject(f.readText()) else null
            }.getOrNull()
        status = FleetDeviceMatrixStore.summaryLine(appCtx)
    }

    LaunchedEffect(reloadNonce) {
        reloadFromDisk()
    }

    LaunchedEffect(Unit) {
        if (FleetDeviceMatrixStore.loadValid(appCtx) == null && !isRunning) {
            runCatching { FleetDeviceMatrixBuilder.buildQuickAndSave(appCtx, forceRescan = true) }
            reloadFromDisk()
        }
    }

    fun launchQuick() {
        if (isRunning) return
        isRunning = true
        status = "Quick refresh…"
        scanLines.clear()
        scanLines.add("${Instant.now()} — quick tier")
        scope.launch {
            try {
                val built =
                    withContext(Dispatchers.IO) {
                        FleetDeviceMatrixBuilder.buildQuickAndSave(appCtx, forceRescan = true)
                    }
                reloadFromDisk()
                status = "Quick OK cameras=${built?.cameraCount ?: 0} ms=${built?.scanDurationMs ?: 0}"
                scanLines.appendProbeLine(status)
            } catch (e: Throwable) {
                status = "Quick failed: ${e.message}"
                Log.e(TAG, "quick refresh failed", e)
            } finally {
                isRunning = false
                reloadNonce++
            }
        }
    }

    fun launchFull() {
        if (isRunning) return
        isRunning = true
        status = "Full scan (may take minutes)…"
        scanLines.clear()
        scanLines.add("${Instant.now()} — full tier")
        scope.launch {
            try {
                val built =
                    FleetDeviceMatrixBuilder.buildFullAndSave(appCtx) { msg ->
                        withContext(Dispatchers.Main) { scanLines.appendProbeLine(msg) }
                    }
                reloadFromDisk()
                val diffLine = built.diff?.summaryLines?.firstOrNull() ?: "done"
                status = "Full OK cameras=${built.cameraCount} ms=${built.scanDurationMs} — $diffLine"
                scanLines.appendProbeLine(status)
            } catch (e: Throwable) {
                status = "Full failed: ${e.message}"
                Log.e(TAG, "full scan failed", e)
            } finally {
                isRunning = false
                reloadNonce++
            }
        }
    }

    fun exportJson() {
        val root = matrix ?: return
        scope.launch(Dispatchers.IO) {
            val ts =
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now())
            val dir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
            val out = File(dir, "fleet_device_matrix_$ts.json")
            out.writeText(root.toString(2), Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                status = "Exported ${out.name} (${out.length()} bytes)"
                scanLines.appendProbeLine(status)
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(insets.asPaddingValues(extra = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBack, enabled = !isRunning) { Text("Back") }
            Button(onClick = { launchQuick() }, enabled = !isRunning, modifier = Modifier.weight(1f)) {
                Text("Quick refresh")
            }
            Button(onClick = { launchFull() }, enabled = !isRunning, modifier = Modifier.weight(1f)) {
                Text("Rescan full")
            }
        }
        OutlinedButton(onClick = { exportJson() }, enabled = matrix != null && !isRunning, modifier = Modifier.fillMaxWidth()) {
            Text("Export JSON")
        }
        Text(
            "Rescan playbook: docs/FLEET_DEVICE_CAPABILITY_MATRIX.md (when app/OS/fleet code changes)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        matrix?.let { root ->
            MatrixSummaryCard(root)
            DiffCard(root)
            EncoderCard(root.optJSONObject(FleetDeviceMatrix.KEY_ENCODER))
            CameraTable(root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS))
        }
        ProbeLiveLogPanel(
            title = "Scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MatrixSummaryCard(root: JSONObject) {
    val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text("Tier: ${meta?.optString("scanTier") ?: "?"}", color = Color.White.copy(alpha = 0.85f))
            Text("Cameras: ${FleetDeviceMatrix.cameraCount(root)}", color = Color.White.copy(alpha = 0.85f))
            Text("Scan ms: ${meta?.optLong("scanDurationMs") ?: 0}", color = Color.White.copy(alpha = 0.85f))
            Text(
                "Device: ${root.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)?.optString("model") ?: "?"}",
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun EncoderCard(encoder: JSONObject?) {
    if (encoder == null || !encoder.has("surfaceEncoding")) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2433)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Video codecs", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                "Source: ${encoder.optString("source")}${encoder.optString("sourceFile").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
            )
            val surface = encoder.optJSONObject("surfaceEncoding")
            if (surface != null) {
                val parts = mutableListOf<String>()
                val keys = surface.keys()
                while (keys.hasNext()) {
                    val mime = keys.next()
                    parts += "${mime.substringAfterLast('/')}= ${surface.optBoolean(mime)}"
                }
                Text(
                    "Surface encoders: ${parts.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
            val rows = encoder.optJSONArray("bestByCameraFps")
            if (rows != null && rows.length() > 0) {
                for (i in 0 until minOf(rows.length(), 8)) {
                    val r = rows.optJSONObject(i) ?: continue
                    Text(
                        "cam ${r.optString("cameraId")} @${r.opt("targetFps")}fps → " +
                            "${"%.0f".format(r.optDouble("measuredFps"))} fps ok=${r.optBoolean("ok")} " +
                            "${r.optString("size", "")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAEECC),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "USB: pns_video_matrix_verify.ps1 · pns_video_codec_color_compare.ps1",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DiffCard(root: JSONObject) {
    val diff = root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("diffVsPrevious") ?: return
    val lines = diff.optJSONArray("summaryLines") ?: return
    if (lines.length() == 0) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2F)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Compare previous", style = MaterialTheme.typography.titleSmall, color = Color(0xFFAAEECC))
            for (i in 0 until lines.length()) {
                Text(lines.optString(i), color = Color.White.copy(alpha = 0.88f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CameraTable(cameras: JSONArray?) {
    if (cameras == null || cameras.length() == 0) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Per camera", style = MaterialTheme.typography.titleSmall, color = Color.White)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.12f))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((0 until cameras.length()).toList()) { i ->
                    val cam = cameras.optJSONObject(i) ?: return@items
                    CameraRow(cam)
                }
            }
        }
    }
}

@Composable
private fun CameraRow(cam: JSONObject) {
    val id = cam.optString("cameraId")
    val gates = cam.optJSONObject("featureGates")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Camera $id · ${cam.optString("lensFacing")}", color = Color(0xFFAAEECC))
        Text(
            "HFR@1080=${cam.opt("hfrMaxFpsAt1080")} · RAW=${cam.opt("rawPickEffective")} · ${cam.opt("hardwareLevel")}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
        )
        gates?.let { g ->
            Text(
                gateLine("RAW", g.optJSONObject("raw")) +
                    " · " + gateLine("HFR", g.optJSONObject("hfr")) +
                    " · " + gateLine("face", g.optJSONObject("face")),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

private fun gateLine(label: String, gate: JSONObject?): String {
    if (gate == null) return "$label=?"
    return "$label adv=${gate.optBoolean("advertised")} sess=${gate.optBoolean("sessionOk")} app=${gate.optBoolean("appEnabled")}"
}
