from pathlib import Path

root = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\app\src\main\java\dev\pointandshoot")
screen_path = root / "PreviewEngineScreen.kt"
lines = screen_path.read_text(encoding="utf-8").splitlines()

idx_fun = next(i for i, l in enumerate(lines) if l.startswith("private fun PreviewEngineContent("))
idx_params_end = next(i for i, l in enumerate(lines) if i > idx_fun and l.strip() == ") {")
idx_shell_start = next(
    i
    for i, l in enumerate(lines)
    if "// Preview tile: **3:4** width:height (4:3 sensor upright" in l
)
idx_tail = next(i for i, l in enumerate(lines) if l.startswith("private tailrec fun Context.findHostActivity"))
idx_content_close = idx_tail - 1

param_lines = lines[idx_fun + 1 : idx_params_end]
shell_lines = lines[idx_shell_start:idx_content_close]

header = Path(r"c:\Users\edwar\AndroidStudioProjects\point-and-shoot\_split_head_chrome.py").read_text(encoding="utf-8")
# reuse minimal header inline
header = """package dev.pointandshoot

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope

"""

extras = """
    previewChromeModifier: Modifier,
    focusRequester: FocusRequester,
    statusBarLine: String,
    captureScope: CoroutineScope,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    chrome: PreviewChromePreferences,
    settings: HudSettings,
    commandDialMode: CommandDialMode,
    onCommandDialModeChange: (CommandDialMode) -> Unit,
    centerViewSize: IntSize,
    onCenterViewSize: (IntSize) -> Unit,
    previewTilePx: IntSize,
    onPreviewTilePx: (IntSize) -> Unit,
    previewHudParentPx: IntSize,
    previewHudInnerOffsetPx: IntOffset,
    onPreviewHudViewport: (IntSize, IntOffset) -> Unit,
    eyeMarksView: List<EyeMark>,
    faceTrackBoxesView: List<FaceTrackBoxView>,
    uiRotationDeg: Float,
    uiRotationDegSmooth: Float,
    readoutMenuSnapshot: ReadoutMenuSnapshot,
    afShutterGateActiveForUi: Boolean,
    selfTimerRemaining: Int,
    chartCorners: List<Offset>,
    onChartCornersChange: (List<Offset>) -> Unit,
    liveChartTarget: BundledReferenceTarget,
    calibrateOverlayActive: Boolean,
    calibratePendingInitialBitmap: Bitmap?,
    onCalibratePendingBitmapConsumed: () -> Unit,
    onExitChartCalibration: () -> Unit,
    onCalibrationProfileSaved: (CalibrationProfile, Double) -> Unit,
    openCalibrateFromPreviewFrame: () -> Unit,
    triggerStillCapture: () -> Unit,
""".strip().splitlines()

column_fn = ["@Composable", "internal fun PreviewEngineChromeColumn("]
column_fn.extend(param_lines)
for e in extras:
    if e.strip():
        column_fn.append("    " + e.strip())
column_fn.append(") {")
# rewrite commandDialMode assignments in shell to use callback
shell_text = "\n".join(shell_lines)
shell_text = shell_text.replace(
    "commandDialMode = mode",
    "onCommandDialModeChange(mode)",
)
column_fn.append(shell_text)
column_fn.append("}")

(root / "PreviewEngineChromeColumn.kt").write_text(header + "\n".join(column_fn) + "\n", encoding="utf-8")

param_names = []
for ln in param_lines:
    s = ln.strip().rstrip(",")
    if not s or s.startswith("//") or s.startswith("/**") or s.startswith("*"):
        continue
    param_names.append(s.split(":")[0].split("=")[0].strip())

new_content = lines[: idx_params_end + 1]
new_content.extend(lines[idx_params_end + 1 : idx_shell_start])
new_content.append("    PreviewEngineChromeColumn(")
for n in param_names:
    new_content.append(f"        {n} = {n},")
for extra in [
    "previewChromeModifier = previewChromeModifier",
    "focusRequester = focusRequester",
    "statusBarLine = statusBarLine",
    "captureScope = captureScope",
    "snackbarHostState = snackbarHostState",
    "chrome = chrome",
    "settings = settings",
    "commandDialMode = commandDialMode",
    "onCommandDialModeChange = { commandDialMode = it }",
    "centerViewSize = centerViewSize",
    "onCenterViewSize = { centerViewSize = it }",
    "previewTilePx = previewTilePx",
    "onPreviewTilePx = { previewTilePx = it }",
    "previewHudParentPx = previewHudParentPx",
    "previewHudInnerOffsetPx = previewHudInnerOffsetPx",
    "onPreviewHudViewport = { sz, off -> previewHudParentPx = sz; previewHudInnerOffsetPx = off }",
    "eyeMarksView = eyeMarksView",
    "faceTrackBoxesView = faceTrackBoxesView",
    "uiRotationDeg = uiRotationDeg",
    "uiRotationDegSmooth = uiRotationDegSmooth",
    "readoutMenuSnapshot = readoutMenuSnapshot",
    "afShutterGateActiveForUi = afShutterGateActiveForUi",
    "selfTimerRemaining = selfTimerRemaining",
    "chartCorners = chartCorners",
    "onChartCornersChange = { chartCorners = it }",
    "liveChartTarget = liveChartTarget",
    "calibrateOverlayActive = calibrateOverlayActive",
    "calibratePendingInitialBitmap = calibratePendingInitialBitmap",
    "onCalibratePendingBitmapConsumed = { calibratePendingInitialBitmap = null }",
    "onExitChartCalibration = { exitChartCalibrationMode() }",
    "onCalibrationProfileSaved = { p, s -> onCalibrationProfileSaved(p, s) }",
    "openCalibrateFromPreviewFrame = { openCalibrateFromPreviewFrame() }",
    "triggerStillCapture = { triggerStillCapture() }",
]:
    new_content.append(f"        {extra},")
new_content.append("    )")
new_content.append("}")
new_content.extend(lines[idx_tail :])

screen_path.write_text("\n".join(new_content) + "\n", encoding="utf-8")
print("shell lines", len(shell_lines), "setup lines", idx_shell_start - idx_params_end)
