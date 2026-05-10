package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Heritage / About screen per BUILD_PLAN §6 (Phase 3 / Part 4).
 *
 *   * Top block: tribute text from the spec, monospaced verbatim.
 *   * Bottom block: developer-facing "What works on OnePlus 13 (dodge)"
 *     summary so other contributors can find the known-good capture recipes
 *     and the canonical failure modes without re-running the full probe suite.
 *
 * The "what works" block is currently sourced from the probe runs recorded in
 * `PROBE_BUILD_PLAN.md` §5. Future work will hydrate it from the latest pulled
 * JSON artifacts at runtime so it stays current with the device under test.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    liveSummary: EncoderSummary? = null,
) {
    val insets = rememberSystemInsetsDp()
    AboutScreenContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        onBack = onBack,
        liveSummary = liveSummary,
    )
}

@Composable
private fun AboutScreenContent(
    padding: PaddingValues,
    onBack: () -> Unit,
    liveSummary: EncoderSummary? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        item {
            Text("About Point & Shoot", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "FOSS pro camera for OnePlus 13 (dodge) on LineageOS 23 (Android 16 / API 36).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Apache-2.0. No proprietary blobs. No Google Play Services.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        item {
            SectionTitle("Heritage")
        }
        item {
            MonospaceBlock(text = TRIBUTE_TEXT)
        }

        item {
            SectionTitle("Command dial — Snap (street)")
            Text(
                text =
                    "Heritage block credits Ricoh for Snap Focus: on the live preview, dial S runs that idea — " +
                        "with no tap metering, focus stays at an infinity-style snap so you can lift-and-fire; " +
                        "tap the finder when you need to refocus.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            SectionTitle("What works on OnePlus 13 (dodge)")
            Text(
                text = "Validated by the on-device probe suite (PROBE_BUILD_PLAN.md §5). " +
                    "Use these as the known-good starting points for new capture pipelines.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        items(KNOWN_GOOD_RECIPES) { recipe -> RecipeCard(recipe) }

        val liveCounts = EncoderRecipeBuilder.headlineCounts(liveSummary)
        if (liveCounts != null && liveSummary != null) {
            item {
                SectionTitle("From the latest probe (live)")
                Text(
                    text = "${liveCounts.totalAttempts} attempts across ${liveCounts.cameraCount} " +
                        "cameras — ${liveCounts.totalOk} ok / ${liveCounts.totalFail} fail " +
                        "(${liveCounts.okPercent}% pass).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            items(EncoderRecipeBuilder.recipesFromSummary(liveSummary)) { row -> LiveRecipeCard(row) }
            val errorRows = EncoderRecipeBuilder.errorRowsFromSummary(liveSummary, maxRows = 5)
            if (errorRows.isNotEmpty()) {
                item {
                    Text(
                        text = "Top failure modes (canonical errors, most-frequent first):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                items(errorRows) { row -> LiveErrorCard(row) }
            }
        }

        item {
            SectionTitle("Known-bad paths")
            Text(
                text = "Combinations the probe has confirmed do NOT work on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        items(KNOWN_BAD_PATHS) { row ->
            MonospaceBlock(
                text = "x  ${row.path}\n   error: ${row.canonicalError}\n   workaround: ${row.workaround}",
            )
        }

        item {
            SectionTitle("Color & LUT credits")
            Text(
                text = "Bundled FOSS LUTs (auto-derived from LutCatalog.kt - this list cannot drift). " +
                    "User-imported .cube files are not credited here; the user owns their own license compliance.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        items(LutCreditsBuilder.creditsFromCatalog()) { row -> LutCreditCard(row) }

        item {
            SectionTitle("Source of truth")
            Text(
                text = "PROBE_RESULTS.md (Markdown export) + hfr-runs/*.json (machine-readable). " +
                    "Re-run scripts/pns_hfr_autorun.ps1 -RunFullSuite to refresh.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun MonospaceBlock(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RecipeCard(recipe: KnownGoodRecipe) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = recipe.title, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(text = "cameraId : ${recipe.cameraId}", style = MaterialTheme.typography.bodySmall)
        Text(text = "size     : ${recipe.size}", style = MaterialTheme.typography.bodySmall)
        Text(text = "fpsRange : ${recipe.fpsRange}", style = MaterialTheme.typography.bodySmall)
        Text(text = "mime     : ${recipe.mime}", style = MaterialTheme.typography.bodySmall)
        if (recipe.requestNotes.isNotBlank()) {
            Text(text = "request  : ${recipe.requestNotes}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = recipe.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun LiveRecipeCard(row: EncoderRecipeBuilder.Row) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = row.title, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(text = "session  : ${row.sessionKind.name}", style = MaterialTheme.typography.bodySmall)
        Text(text = "size     : ${row.sizeLabel}", style = MaterialTheme.typography.bodySmall)
        Text(text = "fpsRange : ${row.fpsLabel}", style = MaterialTheme.typography.bodySmall)
        Text(text = "mime     : ${row.mime}", style = MaterialTheme.typography.bodySmall)
        Text(
            text = "measured : ${"%.1f".format(row.measuredFps)} fps",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun LiveErrorCard(row: EncoderRecipeBuilder.ErrorRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = row.canonicalError, style = MaterialTheme.typography.labelLarge, color = PnsColors.RecordRed)
        Text(text = "count    : ${row.count}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LutCreditCard(row: LutCreditsBuilder.LutCreditRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = row.displayName, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(text = "spdx     : ${row.spdx}", style = MaterialTheme.typography.bodySmall)
        Text(text = "scope    : ${row.scope}", style = MaterialTheme.typography.bodySmall)
        Text(text = "source   : ${row.source}", style = MaterialTheme.typography.bodySmall)
        if (row.description.isNotBlank()) {
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/** Exact heritage tribute text from BUILD_PLAN §6 Part 4. Intentionally not localized. */
private val TRIBUTE_TEXT: String = """
    SONY: For the relentless pursuit of speed and the intelligence of the "sticky" Eye-AF.
    RICOH: For the "Snap Focus" philosophy and the courage to protect the highlights.
    OLYMPUS: For the pioneering "Super Macro" and the soul of the compact professional tool.
    HASSELBLAD: For the legendary Natural Colour Solution and the iconic Orange shutter.
    CANON & NIKON: For the gold standard of focus bracketing, 3D tracking, and the unwavering reliability of the professional instrument.
""".trimIndent()

/**
 * Known-good capture recipes extracted from the probe artifacts. Update this
 * list whenever a new combination is validated end-to-end on the OnePlus 13.
 */
private data class KnownGoodRecipe(
    val title: String,
    val cameraId: String,
    val size: String,
    val fpsRange: String,
    val mime: String,
    val requestNotes: String,
    val evidence: String,
)

private val KNOWN_GOOD_RECIPES: List<KnownGoodRecipe> = listOf(
    KnownGoodRecipe(
        title = "Logical back: 480fps HFR preview",
        cameraId = "0 (logical, physicals=[2,3,4])",
        size = "1920x1080 / 1280x720",
        fpsRange = "[480, 480]",
        mime = "n/a (preview SurfaceTexture only)",
        requestNotes = "createConstrainedHighSpeedCaptureSession + createHighSpeedRequestList; TEMPLATE_RECORD",
        evidence = "DODGE_PROFILE.md HFR section; PreviewEngineScreen sweep results.",
    ),
    KnownGoodRecipe(
        title = "Wide back (LYT-808): 480fps HFR preview",
        cameraId = "2",
        size = "1920x1080 / 1280x720",
        fpsRange = "[480, 480]",
        mime = "n/a (preview SurfaceTexture only)",
        requestNotes = "createConstrainedHighSpeedCaptureSession + createHighSpeedRequestList; TEMPLATE_RECORD",
        evidence = "DODGE_PROFILE.md HFR section.",
    ),
    KnownGoodRecipe(
        title = "Ultra-wide (S5KJN5): 240fps HFR preview",
        cameraId = "3",
        size = "1280x720 / 1920x1080",
        fpsRange = "up to [240, 240]",
        mime = "n/a (preview SurfaceTexture only)",
        requestNotes = "createConstrainedHighSpeedCaptureSession; SurfaceTexture target",
        evidence = "DODGE_PROFILE.md HFR section.",
    ),
    KnownGoodRecipe(
        title = "Tele (LYT-600): 240fps HFR preview",
        cameraId = "4",
        size = "1280x720 / 1920x1080",
        fpsRange = "up to [240, 240]",
        mime = "n/a (preview SurfaceTexture only)",
        requestNotes = "createConstrainedHighSpeedCaptureSession; SurfaceTexture target",
        evidence = "DODGE_PROFILE.md HFR section.",
    ),
    KnownGoodRecipe(
        title = "Front (IMX615): 120fps HFR preview",
        cameraId = "1",
        size = "1280x720 / 1920x1080",
        fpsRange = "up to [120, 120]",
        mime = "n/a (preview SurfaceTexture only)",
        requestNotes = "createConstrainedHighSpeedCaptureSession; SurfaceTexture target",
        evidence = "DODGE_PROFILE.md HFR section.",
    ),
    KnownGoodRecipe(
        title = "RAW_SENSOR feasibility (all back cameras)",
        cameraId = "0 / 2 / 3 / 4",
        size = "4096x3072",
        fpsRange = "(snapshot)",
        mime = "DNG (RAW_SENSOR)",
        requestNotes = "android.request.maxNumOutputRaw == 1; gated by Phase 1 capture stability test",
        evidence = "PROBE_RESULTS.md cameras section; DODGE_PROFILE.md RAW feasibility.",
    ),
    KnownGoodRecipe(
        title = "Reprocess input-to-JPEG session configuration",
        cameraId = "(per-camera as advertised)",
        size = "session-configuration probe only",
        fpsRange = "n/a",
        mime = "JPEG output",
        requestNotes = "API 35+ uses SessionConfiguration(sessionType, outputs) + setInputConfiguration; Builder reflection not available on Android 16/CPH2655",
        evidence = "PROBE_BUILD_PLAN.md §5: PH5 device validation row (capture_latency_*.json reprocessInputToJpegSessionSupported=true).",
    ),
)

private data class KnownBadPath(val path: String, val canonicalError: String, val workaround: String)

private val KNOWN_BAD_PATHS: List<KnownBadPath> = listOf(
    KnownBadPath(
        path = "exhaustive HEVC/HFR encoder configurations on certain size/fps combos",
        canonicalError = "Function not implemented (-38) from MediaCodec configure",
        workaround = "Filter by `enc_probe_*.json` results before attempting the combo at runtime; prefer AVC where -38 occurs.",
    ),
    KnownBadPath(
        path = "headless probe runs that leave Compose mid-navigation",
        canonicalError = "LeftCompositionCancellationException in coroutine scopes tied to composition",
        workaround = "Use an application/work CoroutineScope (SupervisorJob + Dispatchers.IO) for long probes; cancel via DisposableEffect on dispose.",
    ),
    KnownBadPath(
        path = "marking BUILD_PLAN items [x] without a verification gate",
        canonicalError = "Drift between docs and reality",
        workaround = "Follow PROBE_BUILD_PLAN.md §3 verification-before-tick protocol; append to §5 Progress log.",
    ),
)
