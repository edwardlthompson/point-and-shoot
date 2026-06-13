package dev.pointandshoot

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Heritage / About content per BUILD_PLAN §6 (Phase 3 / Part 4).
 *
 * **Preview route:** [AboutRailSheetContent] inside the Settings modal ([docs/preview-chrome-settings-style-guide.md]).
 * **Probe hub:** [AboutScreen] full-page shell with the same chrome tokens for engineering entry.
 */
@Composable
fun AboutRailSheetContent(
    liveSummary: EncoderSummary? = null,
    liveHalHfrMaxByCameraId: Map<String, Int?> = emptyMap(),
) {
    // Parent Settings [Dialog] already applies [Modifier.verticalScroll] — do not nest another scroll here.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AboutHeritageBody(
            liveSummary = liveSummary,
            liveHalHfrMaxByCameraId = liveHalHfrMaxByCameraId,
        )
    }
}

/**
 * Full-page About (probe hub / legacy entry) — same blocks as the rail sheet, charcoal shell.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    liveSummary: EncoderSummary? = null,
    liveHalHfrMaxByCameraId: Map<String, Int?> = emptyMap(),
) {
    val insets = rememberSystemInsetsDp()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PreviewChromeMenuColors.dialogSurface)
                .padding(insets.asPaddingValues(extra = 16.dp)),
    ) {
        FpsQuickChip(
            label = "Back",
            selected = false,
            requiresRoot = false,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = AboutScreenA11y.BACK,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AboutHeritageBody(
                liveSummary = liveSummary,
                liveHalHfrMaxByCameraId = liveHalHfrMaxByCameraId,
            )
        }
    }
}

@Composable
private fun AboutHeritageBody(
    liveSummary: EncoderSummary?,
    liveHalHfrMaxByCameraId: Map<String, Int?>,
) {
    ChromeSettingsIntroText(
        "FOSS pro camera for legacy dodge-class devices on LineageOS 23 (Android 16 / API 36). " +
            "Apache-2.0 — no proprietary blobs, no Google Play Services.",
    )
    ChromeSettingsIntroText(
        "Imaging profiles: Standard Pro and Ultra-Max write DNG; JPEG only uses the " +
            "hardware JPEG still path (no RAW) when selected from the HUD or Settings.",
    )

    val context = LocalContext.current
    val installedVersion = PnsAppInfo.versionName(context)
    PreviewRailSectionTitle("App & updates")
    ChromeSettingsIntroText(
        "Installed $installedVersion · Release notes and APK downloads live on GitHub " +
            "(Obtainium tracks the same releases feed).",
    )
    FpsQuickChip(
        label = "What's new (GitHub release notes)",
        selected = false,
        requiresRoot = false,
        onClick = {
            val ok = openExternalUrl(context, PNS_GITHUB_RELEASES_LATEST_URL)
            if (!ok) {
                Toast.makeText(context, "No browser found to open release notes.", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("PNS.ChromeUx", "aboutReleaseNotes=opened tag=$PNS_GITHUB_LATEST_RELEASE_TAG")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = AboutScreenA11y.RELEASE_NOTES,
    )
    FpsQuickChip(
        label = "Full changelog (GitHub)",
        selected = false,
        requiresRoot = false,
        onClick = {
            val ok = openExternalUrl(context, PNS_GITHUB_CHANGELOG_URL)
            if (!ok) {
                Toast.makeText(context, "No browser found to open changelog.", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("PNS.ChromeUx", "aboutChangelog=opened")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = AboutScreenA11y.CHANGELOG,
    )
    FpsQuickChip(
        label = "Privacy policy (GitHub)",
        selected = false,
        requiresRoot = false,
        onClick = {
            val ok = openExternalUrl(context, PNS_GITHUB_PRIVACY_URL)
            if (!ok) {
                Toast.makeText(context, "No browser found to open privacy policy.", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("PNS.ChromeUx", "aboutPrivacy=opened")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = AboutScreenA11y.PRIVACY,
    )

    PreviewRailSectionTitle("Heritage")
    HeritageCreditsBlock()

    PreviewRailSectionTitle("Support development")
    ChromeSettingsIntroText(
        "Optional tips help cover device testing and release time — opens Venmo in your browser, not inside the app.",
    )
    FpsQuickChip(
        label = "Support development (Venmo)",
        selected = false,
        requiresRoot = false,
        onClick = {
            val ok = openExternalUrl(context, PNS_VENMO_DONATION_URL)
            if (!ok) {
                Toast.makeText(context, "No browser found to open Venmo.", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("PNS.ChromeUx", "aboutVenmo=opened")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = AboutScreenA11y.VENMO,
    )

    PreviewRailSectionTitle("Command dial — Snap (street)")
    Text(
        text =
            "Heritage block credits Ricoh for Snap Focus: on the live preview, dial S runs that idea — " +
                "with no tap metering, focus stays at an infinity-style snap so you can lift-and-fire; " +
                "tap the finder when you need to refocus.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.88f),
    )

    PreviewRailSectionTitle("What works on legacy dodge-class devices")
    ChromeSettingsIntroText(
        "Validated by the on-device probe suite (PROBE_BUILD_PLAN.md §5). " +
            "Known-good starting points for new capture pipelines.",
    )
    KNOWN_GOOD_RECIPES.forEach { recipe -> AboutRecipePanel(recipe) }

    val liveCounts = EncoderRecipeBuilder.headlineCounts(liveSummary)
    if (liveCounts != null && liveSummary != null) {
        PreviewRailSectionTitle("From the latest probe (live)")
        ChromeSettingsIntroText(
            "${liveCounts.totalAttempts} attempts across ${liveCounts.cameraCount} " +
                "cameras — ${liveCounts.totalOk} ok / ${liveCounts.totalFail} fail " +
                "(${liveCounts.okPercent}% pass).",
        )
        EncoderRecipeBuilder.recipesFromSummary(liveSummary, liveHalHfrMaxByCameraId).forEach { row ->
            AboutLiveRecipePanel(row)
        }
        val errorRows = EncoderRecipeBuilder.errorRowsFromSummary(liveSummary, maxRows = 5)
        if (errorRows.isNotEmpty()) {
            ChromeSettingsIntroText("Top failure modes (canonical errors, most-frequent first):")
            errorRows.forEach { row -> AboutLiveErrorPanel(row) }
        }
    }

    PreviewRailSectionTitle("Known-bad paths")
    ChromeSettingsIntroText("Combinations the probe has confirmed do NOT work on this device.")
    KNOWN_BAD_PATHS.forEach { row ->
        ChromeMonospaceBlock(
            text = "x  ${row.path}\n   error: ${row.canonicalError}\n   workaround: ${row.workaround}",
        )
    }

    PreviewRailSectionTitle("Color & LUT credits")
    ChromeSettingsIntroText(
        "Bundled FOSS LUTs (auto-derived from LutCatalog.kt). User-imported .cube files are not listed here.",
    )
    LutCreditsBuilder.creditsFromCatalog().forEach { row -> AboutLutCreditPanel(row) }

    PreviewRailSectionTitle("Source of truth")
    ChromeSettingsIntroText(
        "PROBE_RESULTS.md + hfr-runs/*.json. Re-run scripts/pns_hfr_autorun.ps1 -RunFullSuite to refresh.",
    )
}

@Composable
private fun AboutRecipePanel(recipe: KnownGoodRecipe) {
    ChromeInsetPanel {
        Text(recipe.title, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(
            "cameraId : ${recipe.cameraId}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "size     : ${recipe.size}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "fpsRange : ${recipe.fpsRange}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "mime     : ${recipe.mime}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (recipe.requestNotes.isNotBlank()) {
            Text(
                "request  : ${recipe.requestNotes}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            recipe.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun AboutLiveRecipePanel(row: EncoderRecipeBuilder.Row) {
    ChromeInsetPanel {
        Text(row.title, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(
            "session  : ${row.sessionKind.name}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "size     : ${row.sizeLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "fpsRange : ${row.fpsLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "mime     : ${row.mime}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        row.halAdvertisedHfrMaxFps?.let { halMax ->
            Text(
                "HAL HFR max (catalog) : $halMax fps",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Text(
            "measured : ${"%.1f".format(row.measuredFps)} fps",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun AboutLiveErrorPanel(row: EncoderRecipeBuilder.ErrorRow) {
    ChromeInsetPanel {
        Text(row.canonicalError, style = MaterialTheme.typography.labelLarge, color = PnsColors.RecordRed)
        Text(
            "count    : ${row.count}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun AboutLutCreditPanel(row: LutCreditsBuilder.LutCreditRow) {
    ChromeInsetPanel {
        Text(row.displayName, style = MaterialTheme.typography.labelLarge, color = PnsColors.PhotoOrange)
        Text(
            "spdx     : ${row.spdx}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "scope    : ${row.scope}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            "source   : ${row.source}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (row.description.isNotBlank()) {
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun HeritageCreditsBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HERITAGE_CREDITS.forEach { credit ->
            HeritageCreditLine(brand = credit.brand, line = credit.line)
        }
    }
}

@Composable
private fun HeritageCreditLine(brand: String, line: String) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = PnsColors.PhotoOrange)) {
                    append(brand)
                }
                append(": ")
                append(line)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.88f),
    )
}

private data class HeritageCredit(val brand: String, val line: String)

private val HERITAGE_CREDITS: List<HeritageCredit> =
    listOf(
        HeritageCredit("SONY", "Speed and sticky Eye-AF."),
        HeritageCredit("RICOH", "Snap Focus and highlight discipline."),
        HeritageCredit("OLYMPUS", "Super Macro and the compact pro tool."),
        HeritageCredit("HASSELBLAD", "Natural Colour and the orange shutter."),
        HeritageCredit("CANON & NIKON", "Focus bracketing, 3D tracking, pro reliability."),
        HeritageCredit(
            "LG",
            "Dual recording: rear + front in one clip (stacked preview). Not affiliated.",
        ),
    )

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
        requestNotes = "API 35+ uses SessionConfiguration(sessionType, outputs) + setInputConfiguration",
        evidence = "PROBE_BUILD_PLAN.md §5: PH5 device validation row.",
    ),
)

private data class KnownBadPath(val path: String, val canonicalError: String, val workaround: String)

private val KNOWN_BAD_PATHS: List<KnownBadPath> = listOf(
    KnownBadPath(
        path = "exhaustive HEVC/HFR encoder configurations on certain size/fps combos",
        canonicalError = "Function not implemented (-38) from MediaCodec configure",
        workaround = "Filter by enc_probe_*.json before runtime; prefer AVC where -38 occurs.",
    ),
    KnownBadPath(
        path = "headless probe runs that leave Compose mid-navigation",
        canonicalError = "LeftCompositionCancellationException in coroutine scopes tied to composition",
        workaround = "Use application/work CoroutineScope for long probes; cancel via DisposableEffect.",
    ),
    KnownBadPath(
        path = "marking BUILD_PLAN items [x] without a verification gate",
        canonicalError = "Drift between docs and reality",
        workaround = "Follow PROBE_BUILD_PLAN.md §3; append to §5 Progress log.",
    ),
)
