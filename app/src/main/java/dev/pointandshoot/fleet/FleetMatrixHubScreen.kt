package dev.pointandshoot.fleet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.widget.Toast
import dev.pointandshoot.BuildConfig
import dev.pointandshoot.EXTRA_PNS_AUTO_PARITY_SWEEP
import dev.pointandshoot.PnsConnectivity
import dev.pointandshoot.EXTRA_PNS_PARITY_SWEEP_INCLUDE_RECORD
import dev.pointandshoot.EXTRA_PNS_PARITY_SWEEP_MODE
import dev.pointandshoot.ProbeLiveLogPanel
import dev.pointandshoot.PROBE_EXPORT_LATEST_FILE
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

private enum class MatrixHubTab(val label: String) {
    Summary("Summary"),
    ByCamera("By camera"),
    Features("Features"),
    RawJson("Raw JSON"),
}

private val AdbPullMatrix =
    "adb exec-out run-as dev.pointandshoot cat files/${FleetDeviceMatrixStore.MATRIX_FILE_NAME}"
private val AdbPullSummary =
    "adb exec-out run-as dev.pointandshoot cat files/${FleetDeviceMatrixStore.SUMMARY_FILE_NAME}"

/**
 * Engineering hub — unified **Device capability matrix** (Milestones **16.2** + **17.3**).
 *
 * Merges fleet matrix JSON, capability catalog, and human summary in one screen.
 */
@Composable
fun FleetMatrixHubScreen(
    onBack: () -> Unit,
    initialFeaturesQuery: String? = null,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    val insets = rememberSystemInsetsDp()
    var matrix by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf("Loading…") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var selectedTab by remember {
        mutableIntStateOf(
            if (!initialFeaturesQuery.isNullOrBlank()) MatrixHubTab.Features.ordinal else MatrixHubTab.Summary.ordinal,
        )
    }
    var featuresQuery by remember(initialFeaturesQuery) { mutableStateOf(initialFeaturesQuery.orEmpty()) }
    var showParitySheet by remember { mutableStateOf(false) }
    var lastParitySummary by remember { mutableStateOf<String?>(null) }
    var lastParityJson by remember { mutableStateOf<JSONObject?>(null) }
    var lastParityModeWire by remember { mutableStateOf<String?>(null) }
    var romReported by remember { mutableStateOf(LeaderboardRomReport.Reported.UNSPECIFIED) }
    var antutuTotalText by remember {
        mutableStateOf(LeaderboardAntutuPrefs.read(appCtx)?.total?.toString().orEmpty())
    }

    fun runParity(mode: FleetParitySweepRunner.Mode, includeRecord: Boolean) {
        if (isRunning) return
        isRunning = true
        showParitySheet = false
        status = "Parity sweep ${mode.wire}…"
        scanLines.clear()
        scanLines.add("${Instant.now()} — parity ${mode.wire}")
        scope.launch {
            try {
                val autoSweep =
                    (context as? Activity)?.intent?.getBooleanExtra(EXTRA_PNS_AUTO_PARITY_SWEEP, false) == true
                val root =
                    withContext(Dispatchers.IO) {
                        var m =
                            if (autoSweep) {
                                FleetDeviceMatrixStore.loadValid(appCtx)
                                    ?: runCatching {
                                        val f = FleetDeviceMatrixStore.matrixFile(appCtx)
                                        if (f.exists()) JSONObject(f.readText()) else null
                                    }.getOrNull()
                            } else {
                                matrix ?: FleetDeviceMatrixStore.loadValid(appCtx)
                            }
                        if (m == null) {
                            FleetDeviceMatrixBuilder.buildQuickAndSave(appCtx, forceRescan = true)
                            m = FleetDeviceMatrixStore.loadValid(appCtx)
                        }
                        m
                    } ?: throw IllegalStateException("no matrix")
                val report =
                    withContext(Dispatchers.Default) {
                        FleetParitySweepRunner.run(appCtx, root, mode, includeRecord)
                    }
                val gaps = report.gapCounts[FleetParitySweep.GapClass.GAP_ADVERTISED_NOT_PROVEN] ?: 0
                val mismatch = report.gapCounts[FleetParitySweep.GapClass.GAP_DELIVERY_MISMATCH] ?: 0
                lastParitySummary = "cells=${report.cells.size} gaps=$gaps mismatch=$mismatch"
                lastParityModeWire = mode.wire
                lastParityJson = report.toJson()
                status = "Parity ${mode.wire} OK — $lastParitySummary"
                scanLines.appendProbeLine(status)
                val closurePlan = FleetParitySweepRunner.writeClosurePlan(report)
                scanLines.appendProbeLine(closurePlan)
                val reportJson = report.toJson().toString(2)
                val reportFile = File(appCtx.filesDir, FleetParitySweepRunner.reportFileName(mode))
                reportFile.writeText(reportJson, Charsets.UTF_8)
                scanLines.appendProbeLine("Wrote ${reportFile.name}")
                val probeExport = File(appCtx.filesDir, PROBE_EXPORT_LATEST_FILE)
                val parityBlock =
                    buildString {
                        appendLine()
                        appendLine("## Fleet Parity Sweep (${mode.wire})")
                        appendLine()
                        appendLine(lastParitySummary)
                        appendLine()
                        append(closurePlan)
                    }
                if (probeExport.exists()) {
                    probeExport.appendText(parityBlock, Charsets.UTF_8)
                } else {
                    probeExport.writeText("# Probe export\n$parityBlock", Charsets.UTF_8)
                }
            } catch (e: Throwable) {
                status = "Parity failed: ${e.message}"
                Log.e(TAG, "parity sweep failed", e)
            } finally {
                isRunning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val act = context as? Activity ?: return@LaunchedEffect
        if (!act.intent.getBooleanExtra(EXTRA_PNS_AUTO_PARITY_SWEEP, false)) return@LaunchedEffect
        val modeWire = act.intent.getStringExtra(EXTRA_PNS_PARITY_SWEEP_MODE)?.lowercase()?.trim() ?: "quick"
        val mode =
            when (modeWire) {
                "full" -> FleetParitySweepRunner.Mode.FULL
                "delta" -> FleetParitySweepRunner.Mode.DELTA
                else -> FleetParitySweepRunner.Mode.DELTA
            }
        val includeRecord = act.intent.getBooleanExtra(EXTRA_PNS_PARITY_SWEEP_INCLUDE_RECORD, false)
        runParity(mode, includeRecord)
    }

    fun reloadFromDisk() {
        val raw =
            FleetDeviceMatrixStore.loadValid(appCtx)
                ?: runCatching {
                    val f = FleetDeviceMatrixStore.matrixFile(appCtx)
                    if (f.exists()) JSONObject(f.readText()) else null
                }.getOrNull()
        matrix = raw?.let { FleetDeviceMatrix.withCatalogIfMissing(it) }
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

    fun copyToClipboard(label: String, text: String) {
        val mgr = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mgr.setPrimaryClip(ClipData.newPlainText(label, text))
        status = "Copied: $label"
        scope.launch { scanLines.appendProbeLine(status) }
    }

    fun exportArtifacts() {
        val root = matrix ?: return
        scope.launch(Dispatchers.IO) {
            val ts =
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now())
            val dir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
            val jsonOut = File(dir, "fleet_device_matrix_$ts.json")
            val mdOut = File(dir, "fleet_device_capability_summary_$ts.md")
            jsonOut.writeText(root.toString(2), Charsets.UTF_8)
            mdOut.writeText(FleetCapabilitySummaryMarkdown.render(root), Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                status = "Exported ${jsonOut.name} + ${mdOut.name}"
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Device capability matrix",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { exportArtifacts() },
                enabled = matrix != null && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text("Export JSON + summary")
            }
            Button(
                onClick = { showParitySheet = true },
                enabled = matrix != null && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text("Run Parity Sweep")
            }
        }
        lastParitySummary?.let {
            Text("Last parity: $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
        LeaderboardReadinessCard(
            matrix = matrix,
            parityReport = lastParityJson,
            ingestConfigured = FleetLeaderboardSubmit.ingestUrl() != null,
            publicBaseUrl = BuildConfig.LEADERBOARD_PUBLIC_BASE_URL,
            romReported = romReported,
            onRomReportedChange = { romReported = it },
        )
        if (lastParityJson != null && lastParityModeWire == "full" && PnsConnectivity.isLeaderboardContributeEnabled(appCtx)) {
            val readiness =
                LeaderboardReadiness.evaluate(
                    matrix,
                    lastParityJson,
                    FleetLeaderboardSubmit.ingestUrl() != null,
                    BuildConfig.LEADERBOARD_PUBLIC_BASE_URL,
                )
            OutlinedTextField(
                value = antutuTotalText,
                onValueChange = { raw ->
                    antutuTotalText = raw.filter { it.isDigit() }.take(7)
                    val total = antutuTotalText.toIntOrNull()
                    LeaderboardAntutuPrefs.save(
                        appCtx,
                        total?.let { LeaderboardAntutuPrefs.Score(total = it) },
                    )
                },
                label = { Text("AnTuTu total (optional)") },
                placeholder = { Text("e.g. 2080000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Optional on-device AnTuTu score averaged with other submissions on the public leaderboard.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
            OutlinedButton(
                onClick = {
                    val parity = lastParityJson ?: return@OutlinedButton
                    val mat = matrix ?: return@OutlinedButton
                    scope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                FleetLeaderboardSubmit.submit(appCtx, parity, mat, romReported)
                            }
                        Toast.makeText(
                            appCtx,
                            if (result.ok) "Leaderboard submitted (${result.submissionId ?: "ok"})" else "Submit failed: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                enabled = !isRunning && readiness.contributeEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Contribute to public leaderboard")
            }
        }
        if (showParitySheet) {
            FleetParityModeSheet(
                onDismiss = { showParitySheet = false },
                onRun = { mode, include -> runParity(mode, include) },
            )
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))

        if (FleetDeviceMatrix.needsFullRescan(matrix)) {
            NewDeviceRescanBanner(
                onRescanFull = { launchFull() },
                enabled = !isRunning,
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            edgePadding = 0.dp,
        ) {
            MatrixHubTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label) },
                )
            }
        }

        matrix?.let { root ->
            when (MatrixHubTab.entries[selectedTab]) {
                MatrixHubTab.Summary ->
                    SummaryTabContent(
                        root = root,
                        onCopyAdb = { label, cmd -> copyToClipboard(label, cmd) },
                        modifier = Modifier.weight(1f),
                    )
                MatrixHubTab.ByCamera ->
                    ByCameraTabContent(
                        root = root,
                        modifier = Modifier.weight(1f),
                    )
                MatrixHubTab.Features ->
                    FeaturesTabContent(
                        root = root,
                        modifier = Modifier.weight(1f),
                        initialQuery = featuresQuery,
                        onQueryChange = { featuresQuery = it },
                    )
                MatrixHubTab.RawJson ->
                    RawJsonTabContent(
                        root = root,
                        modifier = Modifier.weight(1f),
                    )
            }
        } ?: Text("No matrix on disk — run Quick refresh.", color = Color.White.copy(alpha = 0.7f))

        if (scanLines.isNotEmpty()) {
            ProbeLiveLogPanel(
                title = "Scan log",
                lines = scanLines,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NewDeviceRescanBanner(onRescanFull: () -> Unit, enabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2E14)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "New device or incomplete scan",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFFFCC80),
            )
            Text(
                "Run Rescan full for stream/format inventory, face-detect modes, and the feature catalog. " +
                    "Quick tier alone is not enough for chrome visibility or video format gates.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
            )
            Button(onClick = onRescanFull, enabled = enabled) { Text("Rescan full") }
        }
    }
}

@Composable
private fun SummaryTabContent(
    root: JSONObject,
    onCopyAdb: (label: String, command: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summaryMd = remember(root) { FleetCapabilitySummaryMarkdown.render(root) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MatrixSummaryCard(root)
        DiffCard(root)
        EncoderCard(root.optJSONObject(FleetDeviceMatrix.KEY_ENCODER))
        AdbPullCard(onCopyAdb = onCopyAdb)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Human summary (markdown)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    summaryMd,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun AdbPullCard(onCopyAdb: (label: String, command: String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2433)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ADB pull paths", style = MaterialTheme.typography.titleSmall, color = Color.White)
            AdbPathRow("Matrix JSON", AdbPullMatrix, onCopyAdb)
            AdbPathRow("Summary markdown", AdbPullSummary, onCopyAdb)
            Text(
                "Host: scripts/pns_fleet_matrix_scan.ps1 pulls both after hub scan.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AdbPathRow(label: String, command: String, onCopy: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAEECC))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                command,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(onClick = { onCopy(label, command) }) { Text("Copy") }
        }
    }
}

@Composable
private fun ByCameraTabContent(root: JSONObject, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Structured cameras[] + deep caps stream hints when full tier is present.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        val cameras = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: JSONArray()
        items((0 until cameras.length()).toList()) { i ->
            val cam = cameras.optJSONObject(i) ?: return@items
            CameraDetailCard(cam, root)
        }
    }
}

@Composable
private fun CameraDetailCard(cam: JSONObject, root: JSONObject) {
    val id = cam.optString("cameraId")
    val deepStream = findDeepStream(root, id)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Camera $id · ${cam.optString("lensFacing")}", color = Color(0xFFAAEECC))
            Text(
                "HFR@1080=${cam.opt("hfrMaxFpsAt1080")} · RAW=${cam.optString("rawPickEffective")} · ${cam.optString("hardwareLevel")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            cam.optJSONObject("featureGates")?.let { g ->
                Text(
                    gateLine("RAW", g.optJSONObject("raw")) +
                        " · " + gateLine("HFR", g.optJSONObject("hfr")) +
                        " · " + gateLine("face", g.optJSONObject("face")),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            val faceModes = cam.optJSONArray("faceDetectModes")
            if (faceModes != null && faceModes.length() > 0) {
                Text(
                    "Face detect modes: ${(0 until faceModes.length()).joinToString(",") { faceModes.optInt(it).toString() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            deepStream?.let { stream ->
                val byFmt = stream.optJSONObject("outputSizesByFormat")
                if (byFmt != null) {
                    Text("Stream formats (deep caps):", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    val keys = byFmt.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = byFmt.optJSONArray(k) ?: continue
                        Text(
                            "  $k: ${arr.length()} sizes",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.68f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturesTabContent(
    root: JSONObject,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val rows = remember(root) { catalogRowsFrom(root) }
    val filtered =
        remember(rows, query) {
            if (query.isBlank()) {
                rows
            } else {
                val q = query.trim().lowercase()
                rows.filter { row ->
                    row.optString("displayName").lowercase().contains(q) ||
                        row.optString("id").lowercase().contains(q) ||
                        row.optString("category").lowercase().contains(q) ||
                        row.optString("sourceLayer").lowercase().contains(q)
                }
            }
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search features…") },
            singleLine = true,
        )
        Text(
            "${filtered.size} / ${rows.size} catalog rows",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.optString("id") }) { row ->
                CatalogFeatureCard(row)
            }
        }
    }
}

@Composable
private fun CatalogFeatureCard(row: JSONObject) {
    val supported = row.optBoolean("deviceSupported")
    val rootOnly = row.optBoolean("rootOnly")
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        rootOnly -> Color(0xFF1A2840)
                        supported -> Color(0xFF1E3A2F)
                        else -> Color(0xFF2A2222)
                    },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.optString("displayName"), color = Color.White)
            Text(
                row.optString("id"),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                "${row.optString("category")} · ${row.optString("sourceLayer")} · app=${row.optString("appStatus")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
            )
            Text(
                buildString {
                    append(if (supported) "On device" else "Not on device")
                    append(" · policy=${row.optString("visibilityPolicy")}")
                    if (rootOnly) append(" · root-only")
                    row.optString("detail").takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAEECC),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RawJsonTabContent(root: JSONObject, modifier: Modifier = Modifier) {
    val jsonText = remember(root) { root.toString(2) }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Pretty-printed matrix JSON (includes capabilityCatalog when built).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            jsonText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

private fun catalogRowsFrom(root: JSONObject): List<JSONObject> {
    val withCatalog = FleetDeviceMatrix.withCatalogIfMissing(root)
    val arr = withCatalog.optJSONArray(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}

private fun findDeepStream(root: JSONObject, cameraId: String): JSONObject? {
    val deep = root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("deepCaps") ?: return null
    val cams = deep.optJSONArray("cameras") ?: return null
    for (i in 0 until cams.length()) {
        val c = cams.optJSONObject(i) ?: continue
        if (c.optString("cameraId") == cameraId) return c.optJSONObject("streamConfigurationMap")
    }
    return null
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
            Text(
                "Catalog rows: ${root.optJSONArray(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)?.length() ?: 0}",
                color = Color.White.copy(alpha = 0.85f),
            )
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
        }
    }
}

@Composable
private fun LeaderboardReadinessCard(
    matrix: JSONObject?,
    parityReport: JSONObject?,
    ingestConfigured: Boolean,
    publicBaseUrl: String,
    romReported: LeaderboardRomReport.Reported,
    onRomReportedChange: (LeaderboardRomReport.Reported) -> Unit,
) {
    val context = LocalContext.current
    val report = LeaderboardReadiness.evaluate(matrix, parityReport, ingestConfigured, publicBaseUrl)
    val border =
        when (report.overall) {
            LeaderboardReadiness.Level.GREEN -> Color(0xFF2E7D4F)
            LeaderboardReadiness.Level.YELLOW -> Color(0xFFB8860B)
            LeaderboardReadiness.Level.RED -> Color(0xFF8B3A3A)
        }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F2E)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Leaderboard readiness", style = MaterialTheme.typography.titleSmall, color = border)
            report.checks.forEach { check ->
                val dot =
                    when (check.level) {
                        LeaderboardReadiness.Level.GREEN -> "●"
                        LeaderboardReadiness.Level.YELLOW -> "◐"
                        LeaderboardReadiness.Level.RED -> "○"
                    }
                Text(
                    "$dot ${check.label}: ${check.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            report.publicDeviceUrl?.let { url ->
                Text(
                    "Public profile: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF88CCFF),
                    modifier = Modifier.padding(top = 2.dp),
                )
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("leaderboard_url", url))
                        Toast.makeText(context, "Leaderboard URL copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy public device URL")
                }
            }
            val romDetail = report.checks.firstOrNull { it.label.contains("ROM") }?.detail.orEmpty()
            if (romDetail.contains("custom") || romDetail.contains("lineage", ignoreCase = true)) {
                Text(
                    "On custom ROM? Also contribute a stock-ROM Camera2 sweep on the same phone for product comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAACCEE),
                )
            }
            Text("ROM self-tag (optional)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                LeaderboardRomReport.Reported.entries.forEach { option ->
                    val selected = romReported == option
                    OutlinedButton(
                        onClick = { onRomReportedChange(option) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when (option) {
                                LeaderboardRomReport.Reported.UNSPECIFIED -> "Auto"
                                LeaderboardRomReport.Reported.STOCK -> "Stock"
                                LeaderboardRomReport.Reported.LINEAGE -> "Lineage"
                                LeaderboardRomReport.Reported.OTHER_CUSTOM -> "Custom"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                if (report.contributeEnabled) "Ready to contribute (Full sweep + full matrix)." else "Complete full matrix rescan + Full parity sweep to contribute.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
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

private fun gateLine(label: String, gate: JSONObject?): String {
    if (gate == null) return "$label=?"
    return "$label adv=${gate.optBoolean("advertised")} sess=${gate.optBoolean("sessionOk")} app=${gate.optBoolean("appEnabled")}"
}
