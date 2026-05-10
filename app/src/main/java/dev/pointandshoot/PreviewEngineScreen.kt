package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.Image
import android.media.ImageReader
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.util.Log
import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.location.Location
import java.util.concurrent.Executor
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.CameraEnhance
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.text.Charsets

/**
 * Right rail holds collapsible quick-setting blocks. We give it
 * extra width so the shutter (64.dp) sits comfortably with padding and the rail can hold the
 * "more settings" the BUILD_PLAN UI milestone calls for, instead of putting the shutter over
 * the preview.
 */
private val PreviewChromeGridIconSize = 24.dp

private fun Modifier.chromeGlyphRotation(degrees: Float): Modifier =
    graphicsLayer {
        rotationZ = degrees
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }

private enum class ChromeGridQuickAction {
    CycleStillsLut,
    FlashStub,
    TimerStub,
    ToggleHistogram,
    ToggleHorizonLevel,
    ToggleEyeAfOverlay,
    ToggleVideoTally,
    ToggleMaxBrightnessPreview,
    ToggleDndInPreview,
    ToggleTapPreviewCapture,
    ToggleVolumeKeysCapture,
    /** Icon-only: embed GPS in DNG/JPEG when permission allows. */
    ToggleSaveLocation,
    /** Icon-only: cycle static preview rotation. */
    CyclePreviewSpin,
}

private sealed class ChromeGridSlotSpec {
    abstract val row: Int
    abstract val col: Int

    data class ExpandShortcut(
        override val row: Int,
        override val col: Int,
        val title: String,
        val icon: ImageVector,
        val contentDescription: String,
    ) : ChromeGridSlotSpec()

    data class QuickAction(
        override val row: Int,
        override val col: Int,
        val icon: ImageVector,
        val contentDescription: String,
        val kind: ChromeGridQuickAction,
    ) : ChromeGridSlotSpec()
}

/** Scroll-area shortcuts (packed into rows of 7); focal-length row is separate. Target FPS lives on the readout strip. */
private val previewChromeGridSlots: List<ChromeGridSlotSpec> =
    listOf(
        ChromeGridSlotSpec.ExpandShortcut(1, 1, "Guides", Icons.Outlined.GridOn, "Guides"),
        ChromeGridSlotSpec.ExpandShortcut(1, 2, "Looks / LUT", Icons.Outlined.Palette, "Looks and LUT"),
        ChromeGridSlotSpec.ExpandShortcut(1, 3, "Preview & keys", Icons.Outlined.TouchApp, "Preview & keys"),
        ChromeGridSlotSpec.ExpandShortcut(1, 5, "Capture & tools", Icons.Outlined.PhotoCamera, "Capture & tools"),
        ChromeGridSlotSpec.QuickAction(2, 0, Icons.Outlined.Palette, "Cycle stills LUT", ChromeGridQuickAction.CycleStillsLut),
        ChromeGridSlotSpec.QuickAction(2, 1, Icons.Outlined.FlashOn, "Flash", ChromeGridQuickAction.FlashStub),
        ChromeGridSlotSpec.QuickAction(2, 2, Icons.Outlined.Timer, "Self timer", ChromeGridQuickAction.TimerStub),
        ChromeGridSlotSpec.QuickAction(2, 3, Icons.Outlined.BarChart, "Histogram", ChromeGridQuickAction.ToggleHistogram),
        ChromeGridSlotSpec.QuickAction(2, 4, Icons.Outlined.Landscape, "Horizon level", ChromeGridQuickAction.ToggleHorizonLevel),
        ChromeGridSlotSpec.QuickAction(2, 5, Icons.Outlined.Face, "Eye AF overlay", ChromeGridQuickAction.ToggleEyeAfOverlay),
        ChromeGridSlotSpec.QuickAction(2, 6, Icons.Outlined.Videocam, "Video tally", ChromeGridQuickAction.ToggleVideoTally),
        ChromeGridSlotSpec.QuickAction(3, 0, Icons.Outlined.BrightnessHigh, "Max brightness in preview", ChromeGridQuickAction.ToggleMaxBrightnessPreview),
        ChromeGridSlotSpec.QuickAction(3, 1, Icons.Outlined.DoNotDisturb, "DND while in preview", ChromeGridQuickAction.ToggleDndInPreview),
        ChromeGridSlotSpec.QuickAction(3, 2, Icons.Outlined.TouchApp, "Tap preview to capture", ChromeGridQuickAction.ToggleTapPreviewCapture),
        ChromeGridSlotSpec.QuickAction(3, 3, Icons.AutoMirrored.Outlined.VolumeUp, "Volume keys capture", ChromeGridQuickAction.ToggleVolumeKeysCapture),
        ChromeGridSlotSpec.QuickAction(3, 4, Icons.Outlined.LocationOn, "Save location in files", ChromeGridQuickAction.ToggleSaveLocation),
        ChromeGridSlotSpec.QuickAction(3, 5, Icons.Outlined.RotateRight, "Spin preview", ChromeGridQuickAction.CyclePreviewSpin),
        ChromeGridSlotSpec.ExpandShortcut(3, 6, "Settings", Icons.Outlined.Settings, "Settings"),
    )

private fun cycleStillsLutQuick(hudState: HudSettingsState) {
    val options = LutCatalog.forScope(LutCatalog.Scope.Stills)
    if (options.isEmpty()) return
    val curName = hudState.current.selectedLutForStills
    val idx = options.indexOfFirst { it.name == curName }.let { i -> if (i >= 0) i else 0 }
    val next = options[(idx + 1) % options.size]
    hudState.update(hudState.current.copy(selectedLutForStills = next.name))
}

private val PreviewBottomTrayHeight = 92.dp
private val PreviewGalleryThumbSize = 56.dp

private fun formatDngSoftwareLine(context: Context, lut: LutCatalog): String =
    DngLutMetadata.formatSoftwareTag(
        appVersion = PnsAppInfo.versionName(context),
        activeLut = lut.identityForDngMetadata(),
    )

private fun formatDngUniqueCameraModelLine(cameraId: String, lut: LutCatalog): String =
    DngLutMetadata.formatUniqueCameraModel(
        deviceModel = Build.MODEL,
        cameraId = cameraId,
        activeLut = lut.identityForDngMetadata(),
        includeLutMarkerInUniqueCameraModel = false,
    )

@Composable
fun PreviewEngineScreen(
    onBack: () -> Unit,
    onOpenDeveloperMenu: () -> Unit = {},
    onOpenHudSettings: (HudSettingsFocus) -> Unit = {},
    startAutoSweep: Boolean = false,
    /** From `am start` extras — see `EXTRA_PNS_PREVIEW_*` in [CameraCapabilitiesProbe]. */
    adbInitialDial: CommandDialMode? = null,
    adbSequentialRawStills: Int = 0,
    adbBracketPattern: BracketPattern? = null,
    adbInitialImagingProfile: ImagingProfile? = null,
    adbSeedCameraId: String? = null,
    adbSuperMacroProbe: Boolean = false,
    /** [LutCatalog] enum name — optional `am start` seed for scripted LUT capture / M6 V&V. */
    adbPreviewStillsLutName: String? = null,
    /** Milestone 6.3 — logs `m6 lutFps*` lines after baseline vs [LutCatalog.PnsCinematic] preview. */
    adbM6FpsLutProbe: Boolean = false,
    /** Milestone 6.2 — ADB grab of one preview frame; logs `calibrate preview frame grab ok`. */
    adbCalibrateGrabSmoke: Boolean = false,
    /** `--ei pns_preview_self_timer_sec N` — seeds [PreviewChromePreferences.selfTimerDelaySec] (normalized). */
    adbInitialSelfTimerSec: Int? = null,
) {
    val context = LocalContext.current
    val controller = remember { PreviewController(context.applicationContext) }
    val cameraIdsList = controller.cameraIds()
    val cameraRoles =
        remember(cameraIdsList.toSortedSet().joinToString()) {
            val cm = context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager
            BackCameraRoleResolver.resolve(cm, cameraIdsList)
        }

    // Must run during composition (not SideEffect): LaunchedEffect can schedule `openCamera` in the
    // same pass; SideEffect runs after and would leave `suppressPeriodicFpsLogs` false for the
    // first `applyFaceDetectMode` / FPS callback.
    // Suppress FPS spam for any `am start` preview automation (dial / RAW / bracket).
    val automationIntentActive =
        adbSequentialRawStills > 0 ||
            adbBracketPattern != null ||
            adbInitialDial != null ||
            !adbSeedCameraId.isNullOrBlank() ||
            adbSuperMacroProbe ||
            adbM6FpsLutProbe ||
            adbCalibrateGrabSmoke ||
            !adbPreviewStillsLutName.isNullOrBlank() ||
            adbInitialSelfTimerSec != null
    controller.suppressPeriodicFpsLogs =
        adbSequentialRawStills > 0 ||
            adbBracketPattern != null ||
            adbInitialDial != null ||
            !adbSeedCameraId.isNullOrBlank() ||
            adbSuperMacroProbe ||
            adbM6FpsLutProbe ||
            adbCalibrateGrabSmoke ||
            adbInitialSelfTimerSec != null
    controller.superMacroAdbProbe = adbSuperMacroProbe
    // RAW + bracket runs disable face stats to cut CameraMetadataJV noise; dial-only (H, BKT UI) keeps Eye-AF / tracker.
    controller.automationSuppressFacePipeline =
        adbSequentialRawStills > 0 || adbBracketPattern != null

    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var selectedFps by remember { mutableStateOf(60) }
    var status by remember { mutableStateOf("Idle") }
    var measuredFps by remember { mutableStateOf(0.0) }
    var previewReadoutIso by remember { mutableStateOf<Int?>(null) }
    var previewReadoutExposureNs by remember { mutableStateOf<Long?>(null) }
    var previewReadoutAwbMode by remember { mutableStateOf<Int?>(null) }
    var previewJpegCompanion by remember { mutableStateOf(false) }
    var surfaceInfo by remember { mutableStateOf("surface=?") }
    var previewBufferSize by remember { mutableStateOf<Size?>(null) }
    var sensorOrientationDeg by remember { mutableStateOf<Int?>(null) }
    var sweepJob by remember { mutableStateOf<Job?>(null) }
    var sweepRunId by remember { mutableStateOf<String?>(null) }
    val autoSweepConsumed = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }

    LaunchedEffect(
        adbSequentialRawStills,
        adbBracketPattern,
        adbInitialDial,
        adbInitialImagingProfile,
        adbSeedCameraId,
        adbSuperMacroProbe,
        adbPreviewStillsLutName,
        adbM6FpsLutProbe,
        adbCalibrateGrabSmoke,
        adbInitialSelfTimerSec,
    ) {
        if (adbSequentialRawStills > 0 ||
            adbBracketPattern != null ||
            adbInitialDial != null ||
            adbInitialImagingProfile != null ||
            !adbSeedCameraId.isNullOrBlank() ||
            adbSuperMacroProbe ||
            adbM6FpsLutProbe ||
            adbCalibrateGrabSmoke ||
            !adbPreviewStillsLutName.isNullOrBlank() ||
            adbInitialSelfTimerSec != null
        ) {
            Log.i(
                "PNS.AdbValidation",
                "automation extras raw=$adbSequentialRawStills bracket=$adbBracketPattern dial=$adbInitialDial profile=${adbInitialImagingProfile?.id} seedCam=$adbSeedCameraId superMacroProbe=$adbSuperMacroProbe stillsLutSeed=$adbPreviewStillsLutName m6FpsLutProbe=$adbM6FpsLutProbe calibrateGrabSmoke=$adbCalibrateGrabSmoke selfTimerSecSeed=$adbInitialSelfTimerSec suppressFps=${controller.suppressPeriodicFpsLogs} suppressFacePipeline=${controller.automationSuppressFacePipeline}",
            )
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            status = controller.status()
            measuredFps = controller.measuredFps()
            previewReadoutIso = controller.previewMeterIso()
            previewReadoutExposureNs = controller.previewMeterExposureNs()
            previewReadoutAwbMode = controller.previewMeterAwbMode()
            previewJpegCompanion = controller.previewUsesJpegCompanion()
            surfaceInfo = controller.surfaceDebug()
            previewBufferSize = controller.previewBufferSize()
            sensorOrientationDeg = controller.sensorOrientationDegrees()
            delay(350)
        }
    }

    // Auto-pick first camera once, or seed from ADB (e.g. ultra-wide for Super Macro probe).
    LaunchedEffect(controller, adbSeedCameraId) {
        var waited = 0
        while (waited < 60 && controller.cameraIds().isEmpty()) {
            delay(50)
            waited++
        }
        val ids = controller.cameraIds()
        if (ids.isEmpty()) return@LaunchedEffect
        val seed = adbSeedCameraId?.trim()?.takeIf { it.isNotBlank() }
        when {
            seed != null && seed in ids -> {
                selectedCameraId = seed
                Log.i("PNS.AdbValidation", "preview seed cameraId=$seed ok")
            }
            selectedCameraId == null -> {
                val appCtx = context.applicationContext
                val m23 = resolveFocalMmSlot(appCtx, FocalMmSlot.M23, ids)
                val picked = pickCameraIdFromM23Resolve(m23, ids)
                selectedCameraId = picked
                if (m23 != null && picked == m23.first) {
                    Log.i("PNS.ChromeUx", "seedOk slot=M23 cameraId=${m23.first}")
                } else {
                    Log.w(
                        "PNS.ChromeUx",
                        "seedOk fallback cameraId=$picked m23Wide=${m23?.first} ids=$ids",
                    )
                }
            }
        }
        if (seed != null && seed !in ids) {
            Log.w("PNS.Preview", "adb seed cameraId=$seed not in ids=$ids")
        }
    }

    LaunchedEffect(startAutoSweep) {
        if (!startAutoSweep) return@LaunchedEffect
        if (!autoSweepConsumed.compareAndSet(false, true)) return@LaunchedEffect
        if (sweepJob != null) return@LaunchedEffect
        // Start sweep after first camera id is available.
        while (selectedCameraId == null) delay(100)
        val runId = UUID.randomUUID().toString()
        sweepRunId = runId
        sweepJob = CoroutineScope(Dispatchers.Main).launch {
            val allIds = controller.cameraIds()
            if (allIds.isEmpty()) {
                Log.w("PNS.Preview", "SWEEP aborted: no camera ids")
                Log.i(SWEEP_SIGNAL_TAG, "SWEEP_DONE runId=$runId ok=false reason=no_camera_ids")
                sweepJob = null
                return@launch
            }

            val preferred = listOf("2", "3", "4", "0")
            val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
                .distinct()
                .filter { it != "1" }

            val sequence = listOf(60, 120, 240, 480)
            Log.d("PNS.Preview", "SWEEP cameras=${sweepCameras.joinToString(",")} allIds=${allIds.joinToString(",")}")
            Log.i(SWEEP_SIGNAL_TAG, "SWEEP_START runId=$runId cameras=${sweepCameras.joinToString(",")} sequence=${sequence.joinToString(",")}")
            for (cam in sweepCameras) {
                selectedCameraId = cam
                Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                delay(700)

                for (fps in sequence) {
                    selectedFps = fps
                    Log.d("PNS.Preview", "SWEEP start cameraId=$cam fps=$fps")
                    delay(3000)
                    Log.d(
                        "PNS.Preview",
                        "SWEEP sample cameraId=$cam fps=$fps status=${controller.status()} fpsMeasured=${"%.1f".format(controller.measuredFps())} ${controller.surfaceDebug()}",
                    )
                }
            }

            Log.d("PNS.Preview", "SWEEP done cameras=${sweepCameras.joinToString(",")}")
            Log.i(SWEEP_SIGNAL_TAG, "SWEEP_DONE runId=$runId ok=true cameras=${sweepCameras.joinToString(",")}")
            sweepJob = null
        }
    }

    LaunchedEffect(selectedCameraId, selectedFps) {
        controller.setDesired(selectedCameraId = selectedCameraId, desiredFps = selectedFps)
    }

    val insets = rememberSystemInsetsDp()
    val density = LocalDensity.current
    LaunchedEffect(insets.top) {
        val topPx = with(density) { insets.top.toPx() }.toInt()
        Log.i(
            "PNS.ChromeUx",
            "safeInsetsTopPx=$topPx mergedBarsCutout=true",
        )
    }
    val fpsOptions =
        remember(selectedCameraId, context) {
            PreviewFpsSupport.enumerateQuickFpsOptions(context, selectedCameraId)
        }
    val hudState = rememberHudSettings()
    val stillsLutLatest = rememberUpdatedState(hudState.current.stillsLut())
    val compositionGuide = rememberCompositionGuideSettings()

    LaunchedEffect(adbPreviewStillsLutName) {
        val name = adbPreviewStillsLutName?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val lut =
            LutCatalog.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: run {
                    Log.w("PNS.Preview", "unknown adbPreviewStillsLutName=$name")
                    return@LaunchedEffect
                }
        hudState.update(hudState.current.copy(selectedLutForStills = lut.name))
        Log.i("PNS.AdbValidation", "preview seeded stillsLut=${lut.name}")
    }

    LaunchedEffect(adbM6FpsLutProbe) {
        if (!adbM6FpsLutProbe) return@LaunchedEffect
        val prev = hudState.current.selectedLutForStills
        try {
            hudState.update(hudState.current.copy(selectedLutForStills = LutCatalog.None.name))
            var waited = 0
            while (waited < 280 && controller.measuredFps() < 12.0) {
                delay(100)
                waited++
            }
            delay(3200)
            val baseline = controller.measuredFps()
            Log.i("PNS.AdbValidation", "m6 lutFpsBaseline fps=${"%.2f".format(baseline)} lut=None")
            hudState.update(hudState.current.copy(selectedLutForStills = LutCatalog.PnsCinematic.name))
            delay(3500)
            val withLut = controller.measuredFps()
            Log.i(
                "PNS.AdbValidation",
                "m6 lutFpsWithLut fps=${"%.2f".format(withLut)} lut=PnsCinematic",
            )
            val drop =
                if (baseline > 1.0) ((baseline - withLut) / baseline) * 100.0 else 0.0
            val ok = drop <= 5.0
            Log.i(
                "PNS.AdbValidation",
                "m6 lutFpsBudget ok=$ok baseline=${"%.2f".format(baseline)} withLut=${"%.2f".format(withLut)} dropPercent=${"%.1f".format(drop)}",
            )
        } finally {
            hudState.update(hudState.current.copy(selectedLutForStills = prev))
        }
    }
    val chromePrefs = rememberPreviewChromePreferences()
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewNeedsResumeKick by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> previewNeedsResumeKick = true
                    Lifecycle.Event.ON_RESUME -> {
                        if (previewNeedsResumeKick) {
                            previewNeedsResumeKick = false
                            controller.kickPreviewPipelineRestart()
                        }
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SideEffect {
        controller.setPreferredJpegCompanion(chromePrefs.current.stillCaptureJpegCompanion)
    }

    LaunchedEffect(adbInitialSelfTimerSec) {
        if (adbInitialSelfTimerSec != null) {
            val norm = PreviewChromePreferences.normalizeSelfTimerDelaySec(adbInitialSelfTimerSec)
            val cur = chromePrefs.current
            if (cur.selfTimerDelaySec != norm) {
                chromePrefs.update(cur.copy(selfTimerDelaySec = norm))
            }
            Log.i(
                "PNS.AdbValidation",
                "preview adb seed selfTimerDelaySec=$norm raw=$adbInitialSelfTimerSec",
            )
        }
        Log.i("PNS.ChromeUx", "selfTimerSec=${chromePrefs.current.selfTimerDelaySec}")
    }

    var isRecording by remember { mutableStateOf(false) }
    /** Latest indexed capture for gallery thumb + open-in-viewer (typically DNG URI). */
    var lastGalleryUri by remember { mutableStateOf<Uri?>(null) }

    /** BUILD_PLAN §3 digital crops: wide `2` → 35/50mm; tele `4` → 85/150mm; `null` = native FOV. */
    var focalCrop by remember { mutableStateOf<FocalMode?>(null) }

    LaunchedEffect(focalCrop) {
        controller.setFocalCrop(focalCrop)
    }

    LaunchedEffect(selectedCameraId, focalCrop, cameraRoles) {
        val sid = selectedCameraId ?: return@LaunchedEffect
        val clamped =
            when (sid) {
                cameraRoles.ultraWide -> null
                cameraRoles.wide ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 || it == FocalMode.Standard50
                    }
                cameraRoles.tele ->
                    focalCrop?.takeIf {
                        it == FocalMode.Portrait85 || it == FocalMode.LongTele150
                    }
                else -> null
            }
        if (clamped != focalCrop) {
            focalCrop = clamped
        }
    }

    var imagingProfile by remember(adbInitialImagingProfile) {
        mutableStateOf(adbInitialImagingProfile ?: ImagingProfile.default)
    }
    /** Latest profile / camera for ADB automation without restarting the capture coroutine. */
    val imagingProfileState = rememberUpdatedState(imagingProfile)
    val selectedCameraIdState = rememberUpdatedState(selectedCameraId)
    val haptics = remember { CaptureHaptics(context.applicationContext) }

    var pendingEnableGeotag by remember { mutableStateOf(false) }
    var locationPermissionRefresh by remember { mutableIntStateOf(0) }
    val requestLocationForGeotag =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            locationPermissionRefresh++
            if (granted && pendingEnableGeotag) {
                val c = chromePrefs.current
                chromePrefs.update(c.copy(saveLocationWithMedia = true))
            }
            pendingEnableGeotag = false
        }
    val fineLocationGranted =
        remember(locationPermissionRefresh) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    LaunchedEffect(chromePrefs.current.saveLocationWithMedia, fineLocationGranted) {
        val c = chromePrefs.current
        if (c.saveLocationWithMedia && !fineLocationGranted) {
            chromePrefs.update(c.copy(saveLocationWithMedia = false))
            CaptureLocationBridge.update(null)
        }
    }

    DisposableEffect(chromePrefs.current.saveLocationWithMedia, fineLocationGranted) {
        val want = chromePrefs.current.saveLocationWithMedia && fineLocationGranted
        if (!want) {
            CaptureLocationBridge.update(null)
            return@DisposableEffect onDispose { }
        }
        val sampler =
            ForegroundLocationSampler(context.applicationContext) { loc ->
                CaptureLocationBridge.update(loc)
            }
        sampler.start()
        onDispose {
            sampler.stop()
            CaptureLocationBridge.update(null)
        }
    }

    val embedStillLocation =
        chromePrefs.current.saveLocationWithMedia && fineLocationGranted
    SideEffect {
        controller.setStillEmbedLocationInFiles(embedStillLocation)
    }

    PreviewMaxBrightnessEffect(chromePrefs.current.maxBrightnessInPreview)
    PreviewForegroundDndEffect(optionEnabled = chromePrefs.current.dndWhileInPreview)
    RecordingDndEffect(
        optionEnabled = chromePrefs.current.dndWhileRecording,
        isRecording = isRecording,
    )

    LaunchedEffect(isRecording, chromePrefs.current.dndWhileRecording, chromePrefs.current.dndWhileInPreview) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@LaunchedEffect
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@LaunchedEffect
        if (nm.isNotificationPolicyAccessGranted) return@LaunchedEffect
        val chrome = chromePrefs.current
        val needsPolicy =
            chrome.dndWhileInPreview ||
            (isRecording && chrome.dndWhileRecording)
        if (needsPolicy) {
            Toast.makeText(
                context,
                "Do Not Disturb in camera needs notification-policy access — tap “Policy access” in preview options.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Keys intentionally omit imagingProfile — profile changes must not cancel mid-burst (race with UI).
    LaunchedEffect(adbSequentialRawStills, adbBracketPattern, adbInitialDial) {
        val bracket = adbBracketPattern
        val dial = adbInitialDial
        if (bracket != null && dial == CommandDialMode.BKT) {
            Log.i("PNS.AdbValidation", "start bracket automation pattern=$bracket")
            controller.setCommandDialMode(CommandDialMode.BKT)
            delay(400)
            var waited = 0
            while (selectedCameraIdState.value.isNullOrBlank() && waited < 150) {
                delay(100)
                waited++
            }
            while (!controller.canCaptureBracketBurst()) delay(400)
            delay(2500)
            val rot = context.displayRotationCompat()
            suspendCoroutine<Unit> { cont ->
                controller.captureBracketBurst(
                    context.applicationContext,
                    imagingProfileState.value,
                    haptics,
                    rot,
                    bracket,
                    dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                    stillsLut = stillsLutLatest.value,
                ) { result ->
                    Log.i(
                        "PNS.AdbValidation",
                        "captureBracketBurst pattern=$bracket ok=${result.isSuccess} detail=${result.exceptionOrNull()?.message ?: result.getOrNull()?.take(120)}",
                    )
                    cont.resume(Unit)
                }
            }
            return@LaunchedEffect
        }
        val n = adbSequentialRawStills
        if (n > 0) {
            Log.i("PNS.AdbValidation", "start sequential RAW stills n=$n")
            var waited = 0
            while (selectedCameraIdState.value.isNullOrBlank() && waited < 150) {
                delay(100)
                waited++
            }
            var waitCap = 0
            while (!controller.canCaptureRawStill() && waitCap < 300) {
                if (waitCap % 12 == 0) {
                    Log.w(
                        "PNS.AdbValidation",
                        "waiting canCaptureRawStill tries=$waitCap ${controller.rawStillNotReadyReason() ?: "ready"} status=${controller.status()}",
                    )
                }
                delay(400)
                waitCap++
            }
            if (!controller.canCaptureRawStill()) {
                Log.e(
                    "PNS.AdbValidation",
                    "sequential RAW aborted: timeout waiting for RAW session ${controller.rawStillNotReadyReason()} status=${controller.status()}",
                )
                return@LaunchedEffect
            }
            delay(2500)
            val gapMs = 2500L
            repeat(n) { idx ->
                val label = "${idx + 1}/$n"
                Log.i("PNS.AdbValidation", "captureRawStill begin $label")
                val rot = context.displayRotationCompat()
                suspendCoroutine<Unit> { cont ->
                    controller.captureRawStill(
                        context.applicationContext,
                        imagingProfileState.value,
                        haptics,
                        rot,
                        dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                        stillsLut = stillsLutLatest.value,
                        adbValidationShotLabel = label,
                    ) { _ ->
                        cont.resume(Unit)
                    }
                }
                if (idx < n - 1) delay(gapMs)
            }
            Log.i("PNS.AdbValidation", "finished sequential RAW stills n=$n")
        }
    }

    PreviewEngineContent(
        padding = insets.asPaddingValuesWithExtraTopBarBand(),
        lastGalleryUri = lastGalleryUri,
        cameraIds = controller.cameraIds(),
        selectedCameraId = selectedCameraId,
        selectedFps = selectedFps,
        fpsOptions = fpsOptions,
        status = status,
        measuredFps = measuredFps,
        previewReadoutIso = previewReadoutIso,
        previewReadoutExposureNs = previewReadoutExposureNs,
        previewReadoutAwbMode = previewReadoutAwbMode,
        previewJpegCompanion = previewJpegCompanion,
        surfaceInfo = surfaceInfo,
        previewBufferSize = previewBufferSize,
        sensorOrientationDeg = sensorOrientationDeg,
        isSweeping = sweepJob != null,
        hudState = hudState,
        compositionGuide = compositionGuide,
        chromePrefs = chromePrefs,
        fineLocationGranted = fineLocationGranted,
        onPendingEnableGeotagChange = { pendingEnableGeotag = it },
        onRequestLocationForGeotag = { requestLocationForGeotag.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        isRecording = isRecording,
        onRecordingChange = { isRecording = it },
        onOpenDeveloperMenu = {
            sweepJob?.cancel()
            onOpenDeveloperMenu()
        },
        onOpenHudSettings = onOpenHudSettings,
        onPickFirstCamera = {
            val ids = controller.cameraIds()
            val m23 = resolveFocalMmSlot(context.applicationContext, FocalMmSlot.M23, ids)
            selectedCameraId = pickCameraIdFromM23Resolve(m23, ids)
            status = if (selectedCameraId == null) "No cameras" else "Selected cameraId=$selectedCameraId"
        },
        onSetFps = { selectedFps = it },
        onStartSweep = {
            if (sweepJob != null) return@PreviewEngineContent
            sweepJob = CoroutineScope(Dispatchers.Main).launch {
                val allIds = controller.cameraIds()
                if (allIds.isEmpty()) {
                    Log.w("PNS.Preview", "SWEEP aborted: no camera ids")
                    sweepJob = null
                    return@launch
                }

                // Prefer physical back cameras if present (dodge profile), then logical back.
                val preferred = listOf("2", "3", "4", "0")
                val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
                    .distinct()
                    .filter { it != "1" } // skip front by default

                val sequence = listOf(60, 120, 240, 480)
                Log.d("PNS.Preview", "SWEEP cameras=${sweepCameras.joinToString(",")} allIds=${allIds.joinToString(",")}")
                for (cam in sweepCameras) {
                    selectedCameraId = cam
                    Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                    // Give the UI/controller a moment to propagate camera id change + surface resize.
                    delay(700)

                    for (fps in sequence) {
                        selectedFps = fps
                        Log.d("PNS.Preview", "SWEEP start cameraId=$cam fps=$fps")
                        // Allow surface sizing + camera open + session start.
                        delay(3000)
                        Log.d(
                            "PNS.Preview",
                            "SWEEP sample cameraId=$cam fps=$fps status=${controller.status()} fpsMeasured=${"%.1f".format(controller.measuredFps())} ${controller.surfaceDebug()}",
                        )
                    }
                }
                Log.d("PNS.Preview", "SWEEP done cameras=${sweepCameras.joinToString(",")}")
                sweepJob = null
            }
        },
        onStopSweep = {
            sweepJob?.cancel()
            sweepJob = null
        },
        focalCrop = focalCrop,
        onApplyFocalMmSlot = { slot ->
            val pair =
                resolveFocalMmSlot(context.applicationContext, slot, controller.cameraIds())
                    ?: return@PreviewEngineContent
            selectedCameraId = pair.first
            focalCrop = pair.second
        },
        imagingProfile = imagingProfile,
        onCycleImagingProfile = {
            imagingProfile =
                if (imagingProfile == ImagingProfile.StandardPro) {
                    ImagingProfile.UltraMax
                } else {
                    ImagingProfile.StandardPro
                }
        },
        onCaptureDng = {
            val rot = context.displayRotationCompat()
            controller.captureRawStill(
                context.applicationContext,
                imagingProfile,
                haptics,
                rot,
                dngSoftwareDescription = formatDngSoftwareLine(context, hudState.current.stillsLut()),
                stillsLut = hudState.current.stillsLut(),
            ) { result ->
                result.fold(
                    onSuccess = { uri ->
                        lastGalleryUri =
                            runCatching { Uri.parse(uri) }.getOrElse { lastGalleryUri }
                    },
                    onFailure = { e ->
                        Toast.makeText(context, e.message ?: "DNG failed", Toast.LENGTH_LONG).show()
                    },
                )
            }
        },
        onBracketBurst = { pattern ->
            val rot = context.displayRotationCompat()
            controller.captureBracketBurst(
                context.applicationContext,
                imagingProfile,
                haptics,
                rot,
                pattern,
                dngSoftwareDescription = formatDngSoftwareLine(context, hudState.current.stillsLut()),
                stillsLut = hudState.current.stillsLut(),
            ) { result ->
                result.fold(
                    onSuccess = { msg ->
                        val lines = msg.lines().filter { it.isNotBlank() }
                        lines.lastOrNull()?.let { last ->
                            lastGalleryUri =
                                runCatching { Uri.parse(last) }.getOrElse { lastGalleryUri }
                        }
                    },
                    onFailure = { e ->
                        Toast.makeText(context, e.message ?: "Bracket failed", Toast.LENGTH_LONG).show()
                    },
                )
            }
        },
        adbInitialDial = adbInitialDial,
        adbCalibrateGrabSmoke = adbCalibrateGrabSmoke,
        controller = controller,
    )
}

// rememberPreviewChromeTwistDegrees was removed when the activity moved to a fixed
// landscape orientation: the preview is no longer rotated by the system, so chrome no
// longer needs to "twist with the screen". Per-element UI rotation now comes from
// [rememberDeviceUiRotationDegrees] (DeviceUiRotation.kt) — chrome rotates around the
// preview, not with it (Sony Photography Pro behavior).

@Composable
private fun PreviewEngineContent(
    padding: PaddingValues,
    lastGalleryUri: Uri?,
    cameraIds: List<String>,
    selectedCameraId: String?,
    selectedFps: Int,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    status: String,
    measuredFps: Double,
    previewReadoutIso: Int?,
    previewReadoutExposureNs: Long?,
    previewReadoutAwbMode: Int?,
    previewJpegCompanion: Boolean,
    surfaceInfo: String,
    previewBufferSize: Size?,
    sensorOrientationDeg: Int?,
    isSweeping: Boolean,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    onOpenDeveloperMenu: () -> Unit,
    onOpenHudSettings: (HudSettingsFocus) -> Unit,
    onPickFirstCamera: () -> Unit,
    onSetFps: (Int) -> Unit,
    onStartSweep: () -> Unit,
    onStopSweep: () -> Unit,
    focalCrop: FocalMode?,
    onApplyFocalMmSlot: (FocalMmSlot) -> Unit,
    imagingProfile: ImagingProfile,
    onCycleImagingProfile: () -> Unit,
    onCaptureDng: () -> Unit,
    onBracketBurst: (BracketPattern) -> Unit,
    adbInitialDial: CommandDialMode? = null,
    adbCalibrateGrabSmoke: Boolean = false,
    controller: PreviewController,
) {
    val context = LocalContext.current
    val settings = hudState.current
    val chrome = chromePrefs.current
    val captureScope = rememberCoroutineScope()
    var selfTimerRemaining by remember { mutableIntStateOf(0) }
    var selfTimerCountdownActive by remember { mutableStateOf(false) }

    fun triggerStillCapture() {
        val delaySec =
            PreviewChromePreferences.normalizeSelfTimerDelaySec(chromePrefs.current.selfTimerDelaySec)
        if (delaySec <= 0) {
            onCaptureDng()
            return
        }
        if (selfTimerCountdownActive) return
        selfTimerCountdownActive = true
        captureScope.launch {
            try {
                var remaining = delaySec
                while (remaining > 0) {
                    selfTimerRemaining = remaining
                    delay(1000)
                    remaining--
                }
                selfTimerRemaining = 0
                onCaptureDng()
            } finally {
                selfTimerCountdownActive = false
                selfTimerRemaining = 0
            }
        }
    }

    val layoutPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val liveChartTarget = remember { BundledReferenceTargets.Generic24 }
    var chartCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    LaunchedEffect(chrome.liveChartCornerOverlay) {
        if (!chrome.liveChartCornerOverlay) chartCorners = emptyList()
    }
    var centerViewSize by remember { mutableStateOf(IntSize.Zero) }
    /** TextureView / rotated inner box size in px (for buffer→eye-mark mapping; not the full letterboxed viewport). */
    var previewTilePx by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }
    var commandDialMode by remember(adbInitialDial) {
        mutableStateOf(
            adbInitialDial ?: HudSettings.loadCommandDialMode(context),
        )
    }
    // Same-frame sync: LaunchedEffect runs after the first frame, so a TextureView-driven
    // maybeRestart could observe a stale dial on the controller — SideEffect aligns first.
    SideEffect {
        controller.setCommandDialMode(commandDialMode)
    }
    SideEffect {
        controller.setPreviewTextureCoverCrop(chrome.previewTextureCoverCrop)
    }
    TrackModeTransition("camera", selectedCameraId ?: "null")
    TrackModeTransition("fps", selectedFps.toString())
    TrackModeTransition("imaging_profile", imagingProfile.id)
    TrackModeTransition("recording", isRecording.toString())
    TrackModeTransition("focal_crop", focalCrop?.name ?: "null")
    TrackModeTransition("command_dial", commandDialMode.name)
    var eyeMarksBuffer by remember { mutableStateOf<List<EyeMark>>(emptyList()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(settings.showEyeAfOverlay) {
        controller.setHudFaceOverlayEnabled(settings.showEyeAfOverlay)
    }

    LaunchedEffect(settings.showCommandDial) {
        Log.i(
            "PNS.ChromeUx",
            if (settings.showCommandDial) {
                "modeDialPopout=anchorVisible"
            } else {
                "modeDialPopout=skipped_no_dial"
            },
        )
    }

    LaunchedEffect(previewJpegCompanion) {
        Log.i(
            "PNS.ChromeUx",
            "readoutCapture=${if (previewJpegCompanion) "RAW+" else "RAW"}",
        )
    }

    DisposableEffect(controller) {
        controller.setEyeMarksListener { eyeMarksBuffer = it }
        onDispose {
            controller.setEyeMarksListener(null)
        }
    }

    // Sony-Photography-Pro chrome rotation: each rail icon / settings cube counter-rotates
    // about its own centre while the preview texture stays visually fixed (static spin offset
    // only via `staticPreviewRotationDeg`; device rotation does not re-layout the preview).
    // Per-element rotation keeps the rails fixed in screen position while only the glyphs
    // spin to read upright.
    val deviceUiRotationState = rememberDeviceUiRotationState()
    val uiRotationDeg = deviceUiRotationState.snappedDegrees
    val uiRotationDegSmooth = deviceUiRotationState.smoothDegrees

    val previewTextureSlot = remember { PreviewTextureViewSlot() }
    var calibrateOverlayActive by remember { mutableStateOf(false) }
    var calibratePendingInitialBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun openCalibrateFromPreviewFrame() {
        val tv = previewTextureSlot.view
        if (tv == null) {
            Toast.makeText(context, "Preview not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = controller.grabPreviewFrameBitmap(tv)
        if (bmp == null) {
            Toast.makeText(context, "Could not grab preview frame.", Toast.LENGTH_SHORT).show()
            return
        }
        calibratePendingInitialBitmap = bmp
        calibrateOverlayActive = true
    }

    LaunchedEffect(adbCalibrateGrabSmoke, controller) {
        if (!adbCalibrateGrabSmoke) return@LaunchedEffect
        Log.i("PNS.AdbValidation", "calibrate preview grab smoke: polling TextureView")
        repeat(90) {
            delay(400)
            val tv = previewTextureSlot.view
            if (tv != null && tv.width > 0 && tv.height > 0 && tv.surfaceTexture != null) {
                val bmp = controller.grabPreviewFrameBitmap(tv)
                if (bmp != null) {
                    bmp.recycle()
                    return@LaunchedEffect
                }
            }
        }
        Log.e("PNS.AdbValidation", "calibrate preview grab smoke FAILED (no successful grab)")
    }

    val readoutMenuSnapshot =
        remember(selectedCameraId) {
            controller.readoutMenuSnapshot()
        }

    val eyeMarksView =
        remember(eyeMarksBuffer, previewTilePx, previewBufferSize, chrome.previewTextureCoverCrop) {
            val buf = previewBufferSize
            val vw = previewTilePx.width
            val vh = previewTilePx.height
            if (buf == null || vw <= 0 || vh <= 0) {
                emptyList()
            } else {
                eyeMarksBuffer.map { m ->
                    val (vx, vy) =
                        TexturePreviewFit.mapBufferToView(
                            m.position.x,
                            m.position.y,
                            vw,
                            vh,
                            buf.width,
                            buf.height,
                            coverCrop = chrome.previewTextureCoverCrop,
                        )
                    EyeMark(
                        Offset(vx, vy),
                        m.confidence,
                        m.trackingLocked,
                        m.referenceTrack,
                    )
                }
            }
        }

    val previewChromeModifier =
        Modifier
            .fillMaxSize()
            .background(PnsColors.Charcoal)
            .padding(padding)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                if (!chrome.volumeKeysCapture) return@onPreviewKeyEvent false
                when (it.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                        when {
                            commandDialMode == CommandDialMode.BKT && controller.canCaptureBracketBurst() ->
                                onBracketBurst(BracketPattern.Five)
                            controller.canCaptureRawStill() -> triggerStillCapture()
                            else ->
                                Toast.makeText(
                                    context,
                                    "DNG/BKT: switch preview to ≤119 fps (RAW session); BKT needs dial on BKT",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val next = !isRecording
                        onRecordingChange(next)
                        Toast.makeText(
                            context,
                            if (next) "Recording started (volume down)" else "Recording stopped (volume down)",
                            Toast.LENGTH_SHORT,
                        ).show()
                        true
                    }
                    else -> false
                }
            }

    // Preview tile: **3:4** width:height (4:3 sensor upright — long edge vertical). Chrome scroll stack fills remaining height.
    Box(modifier = previewChromeModifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Share vertical space with the chrome rail (2:1) so the finder column can breathe;
            // keep a strict **width / height = 3 / 4** tile (upright 4:3 sensor), centered in the slot.
            Box(
                modifier =
                    Modifier
                        .weight(2f)
                        .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f),
                    ) {
                        PreviewMainViewport(
                            modifier = Modifier.fillMaxSize(),
                            centerViewSize = centerViewSize,
                            onCenterViewSize = { centerViewSize = it },
                            onPreviewTilePx = { previewTilePx = it },
                            previewTextureSlot = previewTextureSlot,
                            controller = controller,
                            uiRotationDeg = uiRotationDeg,
                            uiRotationDegSmooth = uiRotationDegSmooth,
                            hudState = hudState,
                            compositionGuide = compositionGuide,
                            previewBufferSize = previewBufferSize,
                            isRecording = isRecording,
                            eyeMarks = eyeMarksView,
                            focusRequester = focusRequester,
                            previewTextureCoverCrop = chrome.previewTextureCoverCrop,
                            tapPreviewToCapture = chrome.tapPreviewToCapture,
                            liveChartCornerOverlay = chrome.liveChartCornerOverlay,
                            chartCorners = chartCorners,
                            onChartCornersChange = { chartCorners = it },
                            liveChartRows = liveChartTarget.rows,
                            liveChartCols = liveChartTarget.cols,
                            staticPreviewRotationDeg = chrome.staticPreviewRotationDeg,
                            layoutPortrait = layoutPortrait,
                            sensorOrientationDeg = sensorOrientationDeg,
                            onCaptureDng = { triggerStillCapture() },
                        )
                        if (selfTimerRemaining > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selfTimerRemaining.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            PreviewReadoutStrip(
                iso = previewReadoutIso,
                exposureNs = previewReadoutExposureNs,
                awbMode = previewReadoutAwbMode,
                measuredFps = measuredFps,
                stillCaptureJpegCompanion = chrome.stillCaptureJpegCompanion,
                menu = readoutMenuSnapshot,
                fpsOptions = fpsOptions,
                onPickIso = { iso -> controller.setReadoutManualIso(iso) },
                onPickShutter = { ns -> controller.setReadoutManualShutter(ns) },
                onPickAwb = { mode -> controller.setReadoutManualAwbMode(mode) },
                onPickFps = onSetFps,
                stillLut = settings.stillsLut(),
                videoLut = settings.videoLut(),
                onPickStillLut = { entry ->
                    hudState.update(settings.copy(selectedLutForStills = entry.name))
                },
                onPickVideoLut = { entry ->
                    hudState.update(settings.copy(selectedLutForVideo = entry.name))
                },
                onPickStillPipeline = { jpeg ->
                    chromePrefs.update(chrome.copy(stillCaptureJpegCompanion = jpeg))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PreviewRightRail(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                uiRotationDeg = uiRotationDeg,
                cameraIds = cameraIds,
                onApplyFocalMmSlot = onApplyFocalMmSlot,
                onOpenHudSettings = onOpenHudSettings,
                onOpenDeveloperMenu = onOpenDeveloperMenu,
                fpsOptions = fpsOptions,
                selectedFps = selectedFps,
                onSetFps = onSetFps,
                hudState = hudState,
                compositionGuide = compositionGuide,
                chromePrefs = chromePrefs,
                isSweeping = isSweeping,
                onPickFirstCamera = onPickFirstCamera,
                onStartSweep = onStartSweep,
                onStopSweep = onStopSweep,
                status = status,
                surfaceInfo = surfaceInfo,
                measuredFps = measuredFps,
                selectedCameraId = selectedCameraId,
                focalCrop = focalCrop,
                imagingProfile = imagingProfile,
                onCycleImagingProfile = onCycleImagingProfile,
                onCaptureDng = { triggerStillCapture() },
                onBracketBurst = onBracketBurst,
                canCaptureRawStill = controller.canCaptureRawStill(),
                canCaptureBracketBurst = controller.canCaptureBracketBurst(),
                commandDialMode = commandDialMode,
                onCalibrateFromPreviewFrame = { openCalibrateFromPreviewFrame() },
                previewJpegCompanion = previewJpegCompanion,
                rawStillNotReadyReason = controller.rawStillNotReadyReason(),
                fineLocationGranted = fineLocationGranted,
                onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                onRequestLocationForGeotag = onRequestLocationForGeotag,
                layoutPortrait = layoutPortrait,
            )
            val showBottomTray =
                chrome.showOnScreenShutter || lastGalleryUri != null || settings.showCommandDial
            if (showBottomTray) {
                PreviewBottomCaptureTray(
                    lastGalleryUri = lastGalleryUri,
                    showOnScreenShutter = chrome.showOnScreenShutter,
                    canCaptureRawStill = controller.canCaptureRawStill(),
                    onCaptureDng = { triggerStillCapture() },
                    isRecording = isRecording,
                    onRecordingChange = onRecordingChange,
                    shootingModesSlot =
                        if (settings.showCommandDial) {
                            {
                                var modeMenuExpanded by remember { mutableStateOf(false) }
                                Box(contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = "Mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.72f),
                                            maxLines = 1,
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        FloatingActionButton(
                                            onClick = { modeMenuExpanded = true },
                                            modifier =
                                                Modifier
                                                    .size(52.dp)
                                                    .border(
                                                        2.dp,
                                                        Color.White.copy(alpha = 0.88f),
                                                        CircleShape,
                                                    ).semantics {
                                                        contentDescription =
                                                            "Shooting mode ${commandDialMode.label}. Opens menu: Auto, Manual, Highlight, Snap, Bracket."
                                                    },
                                            containerColor = PnsColors.PhotoOrange.copy(alpha = 0.92f),
                                            contentColor = Color.Black,
                                            shape = CircleShape,
                                        ) {
                                            Text(
                                                text = commandDialMode.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontSize =
                                                    if (commandDialMode == CommandDialMode.BKT) {
                                                        11.sp
                                                    } else {
                                                        17.sp
                                                    },
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = modeMenuExpanded,
                                        onDismissRequest = { modeMenuExpanded = false },
                                        modifier = Modifier.widthIn(min = 288.dp),
                                    ) {
                                        Text(
                                            text = "Shooting mode",
                                            modifier =
                                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        HorizontalDivider()
                                        CommandDialMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text("${mode.label} — ${mode.description}")
                                                },
                                                leadingIcon = {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .width(28.dp)
                                                                .height(24.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (mode == commandDialMode) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Check,
                                                                contentDescription = null,
                                                                tint = PnsColors.PhotoOrange,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    commandDialMode = mode
                                                    HudSettings.saveCommandDialMode(context, mode)
                                                    modeMenuExpanded = false
                                                    Log.i(
                                                        "PNS.ChromeUx",
                                                        "modeDialPopout=menuSelect mode=${mode.name}",
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
        if (calibrateOverlayActive) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                CalibrateScreen(
                    initialChartBitmap = calibratePendingInitialBitmap,
                    onInitialChartBitmapConsumed = { calibratePendingInitialBitmap = null },
                    onBack = { calibrateOverlayActive = false },
                )
            }
        }
    }
}

private class PreviewTextureViewSlot {
    var view: TextureView? = null
}

@Composable
private fun PreviewMainViewport(
    modifier: Modifier,
    centerViewSize: IntSize,
    onCenterViewSize: (IntSize) -> Unit,
    onPreviewTilePx: (IntSize) -> Unit,
    previewTextureSlot: PreviewTextureViewSlot,
    controller: PreviewController,
    uiRotationDeg: Float,
    uiRotationDegSmooth: Float,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    previewBufferSize: Size?,
    isRecording: Boolean,
    eyeMarks: List<EyeMark>,
    focusRequester: FocusRequester,
    /** When true, TextureView uses center-crop (fill); when false, center-contain (matches still JPEG framing). */
    previewTextureCoverCrop: Boolean,
    tapPreviewToCapture: Boolean,
    liveChartCornerOverlay: Boolean,
    chartCorners: List<Offset>,
    onChartCornersChange: (List<Offset>) -> Unit,
    liveChartRows: Int,
    liveChartCols: Int,
    staticPreviewRotationDeg: Int,
    /** Reserved — rotation follows [effectivePreviewStaticRotationDeg] only (no orientation coupling). */
    layoutPortrait: Boolean,
    sensorOrientationDeg: Int?,
    onCaptureDng: () -> Unit,
) {
    // The OUTER box is the full-width finder above the chrome grid (no side rails).
    //
    // Layout invariants (read carefully — wrong wiring here is what caused the on-device "warped
    // preview / no preview at all" reports):
    //
    //   * The TextureView ALWAYS exists. The camera can only open once a Surface is available,
    //     and the Surface is produced by the SurfaceTexture, which only exists after the
    //     TextureView has been laid out. If we gated the TextureView on `previewBufferSize`
    //     being known we'd deadlock (camera starts only after surface, surface arrives only
    //     after TextureView, buffer-size signal only after camera starts).
    //
    //   * Distortion handling: once `previewBufferSize` is known, the inner content box uses
    //     **cover** sizing (same aspect as the buffer, minimum size that fills the viewport).
    //     Parent clips overflow so left/right pillarbars disappear; top/bottom may crop. The
    //     TextureView fills that box — center-fit transform stays uniform scale (no stretch).
    //     While `previewBufferSize` is unknown, the TextureView fills the parent.
    //
    //   * Static rotation ([effectivePreviewStaticRotationDeg] from prefs only)
    //     is applied via `graphicsLayer` on the content box. Footprint flips W↔H for 90°/270°.
    //
    // Layout tree:
    //
    //     centerView (BoxWithConstraints, black background)
    //       └ rotated content box (centered, sized to PRE-rotation footprint, graphicsLayer
    //         applies the static rotation; buffer-locked overlays live here so they track the
    //         rotated image)
    //         ├ TextureView (fillMaxSize → matches buffer aspect once known → no distortion)
    //         ├ CompositionGuideOverlay (rule-of-thirds, locked to the buffer)
    //         └ EyeAfOverlay (eye marks live in buffer coords)
    //       ├ HorizonLevelOverlay (drawn outside the rotated box: the bar self-rotates from
    //         gravity, so it stays world-horizontal in the user's view regardless of the
    //         static preview rotation or the device tilt)
    //       └ VideoTallyOverlay (chrome — counter-rotates with `uiRotationDeg` to stay upright)
    BoxWithConstraints(
        modifier =
            modifier
                .background(Color.Black)
                .clip(RoundedCornerShape(0.dp))
                .onSizeChanged { onCenterViewSize(it) },
    ) {
        val parentW = constraints.maxWidth
        val parentH = constraints.maxHeight
        val buf = previewBufferSize
        val bufW = buf?.width ?: 0
        val bufH = buf?.height ?: 0
        val knownBuf = bufW > 0 && bufH > 0 && parentW > 0 && parentH > 0
        val rotationAppliedDeg = effectivePreviewStaticRotationDeg(staticPreviewRotationDeg, layoutPortrait)
        val isQuarterTurn = rotationAppliedDeg == 90 || rotationAppliedDeg == 270

        // Axis-aligned footprint in parent coords after static rotation (matches reality).
        // Integer width/height from cover-fit sizing avoids drift vs scaling k.
        val footprintAspectWH: Float =
            if (!knownBuf) {
                1f
            } else if (isQuarterTurn) {
                bufH.toFloat() / bufW.coerceAtLeast(1)
            } else {
                bufW.toFloat() / bufH.coerceAtLeast(1)
            }
        val (boxW, boxH) =
            if (knownBuf) {
                TexturePreviewFit.smallestCoveringAxisAlignedRectWithAspect(
                    parentW,
                    parentH,
                    footprintAspectWH,
                )
            } else {
                parentW.coerceAtLeast(1) to parentH.coerceAtLeast(1)
            }

        // PRE-rotation content box: inner layout size before `graphicsLayer.rotationZ`; quarter-turn
        // swaps so the post-rotation AABB is (boxW × boxH).
        val (preW, preH) =
            if (knownBuf && isQuarterTurn) boxH to boxW else boxW to boxH

        SideEffect {
            OrientationProbeBridge.update(
                OrientationProbeSnapshot(
                    bufferSize = previewBufferSize,
                    centerViewSize = IntSize(parentW, parentH),
                    sensorOrientationDeg = sensorOrientationDeg,
                    chromeRotationDegSnapped = uiRotationDeg,
                    chromeRotationDegSmooth = uiRotationDegSmooth,
                ),
            )
        }

        SideEffect {
            if (knownBuf && preW > 0 && preH > 0) {
                onPreviewTilePx(IntSize(preW, preH))
            }
        }

        val density = LocalDensity.current
        val preWDp = with(density) { preW.toDp() }
        val preHDp = with(density) { preH.toDp() }

        // Displayed image rect in centerView coords (after static rotation footprint). Used
        // for tap-to-shoot mapping and the chart-corner guide.
        val displayW = boxW
        val displayH = boxH
        // Cover-fit footprint may extend past the clipped viewport — offsets may be negative.
        val displayLeft = (parentW - displayW) / 2
        val displayTop = (parentH - displayH) / 2

        val tapToShootEnabled =
            tapPreviewToCapture &&
                knownBuf &&
                !isRecording &&
                !liveChartCornerOverlay
        val tapShootCallbacks =
            remember(
                onCaptureDng,
                tapPreviewToCapture,
                displayLeft,
                displayTop,
                displayW,
                displayH,
                isRecording,
                controller,
                liveChartCornerOverlay,
            ) {
                object : TapToShootCallbacks {
                    override fun onDown(p: Offset) {
                        focusRequester.requestFocus()
                        // [p] is in the inner-rotated-content frame (since the tap layer lives
                        // inside the rotated content box). Translate back to centerView coords
                        // by adding the displayed rect's offset; the AF region mapping in
                        // [PreviewController] still treats centerView as the viewport.
                        val vx = p.x + displayLeft
                        val vy = p.y + displayTop
                        controller.applyTapFocusFromView(
                            vx,
                            vy,
                            parentW,
                            parentH,
                            0f,
                        )
                    }

                    override fun onFire() {
                        if (!isRecording && tapPreviewToCapture && controller.canCaptureRawStill()) {
                            onCaptureDng()
                        }
                    }

                    override fun onCancel() {}
                }
            }

        // Rotated content container — buffer-aspect-correct PRE-rotation dimensions (or
        // parent-fill while buffer is unknown) plus a graphicsLayer rotation. TextureView and
        // buffer-locked overlays live inside.
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(preWDp, preHDp)
                    .graphicsLayer {
                        rotationZ = rotationAppliedDeg.toFloat()
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        // The TextureView is sized to match the buffer aspect ratio (once known),
                        // so the platform's default "fit surface texture to view rect" already
                        // renders without distortion. Keep `isOpaque=false` for safety on devices
                        // that interpret the default differently when an opaque view is sized
                        // non-natively.
                        isOpaque = false
                        // Re-apply the texture transform on layout changes too — Compose's
                        // `update` block runs on recomposition but isn't guaranteed to run
                        // *after* the TextureView's own layout pass on every device. Wiring the
                        // OnLayoutChangeListener ensures `applyPreviewTextureTransform` always
                        // sees the current `tv.width × tv.height` after a resize, which is
                        // critical when `previewBufferSize` switches the BoxWithConstraints
                        // sizing (e.g. when the camera stream resolution changes mode).
                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            val tv = v as TextureView
                            controller.applyPreviewTextureTransform(
                                tv,
                                tv.width,
                                tv.height,
                                uiTwistDegrees = 0f,
                                coverCrop = previewTextureCoverCrop,
                            )
                        }
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                controller.onSurfaceTextureAvailable(st, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                controller.onSurfaceTextureSizeChanged(width, height)
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                controller.onTextureUpdated()
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                controller.onSurfaceTextureDestroyed(st)
                                return true
                            }
                        }
                    }
                },
                update = { tv ->
                    previewTextureSlot.view = tv
                    // [applyPreviewTextureTransform] resolves to identity when the TextureView's
                    // size matches the buffer aspect (which is always the case once buffer size
                    // is known); the call is still here so the controller can re-apply the
                    // matrix on the rare devices that need an explicit identity transform reset
                    // after a Surface swap. The OnLayoutChangeListener (set in factory) handles
                    // size-change cases the update block can't catch.
                    controller.applyPreviewTextureTransform(
                        tv,
                        tv.width,
                        tv.height,
                        uiTwistDegrees = 0f,
                        coverCrop = previewTextureCoverCrop,
                    )
                },
            )

            if (knownBuf) {
                PreviewCenterOverlay(
                    modifier = Modifier.fillMaxSize(),
                    hudState = hudState,
                    compositionGuide = compositionGuide,
                    isRecording = isRecording,
                    eyeMarks = eyeMarks,
                    uiRotationDeg = uiRotationDeg,
                    tapToShootEnabled = tapToShootEnabled,
                    tapShootCallbacks = tapShootCallbacks,
                    onRequestVolumeKeyFocus = { focusRequester.requestFocus() },
                    showHorizonLevel = false, // drawn outside the rotated box (gravity-locked)
                    showVideoTallyPip = false, // tally pip is chrome; drawn outside the rotated box
                )
                if (liveChartCornerOverlay) {
                    LiveChartCornerGuide(
                        modifier = Modifier.fillMaxSize(),
                        enabled = true,
                        corners = chartCorners,
                        onCornersChange = onChartCornersChange,
                        rows = liveChartRows,
                        cols = liveChartCols,
                    )
                }
            }
        }

        // Horizon line + tally pip live OUTSIDE the rotated container so they keep their own
        // coordinate system: the horizon overlay self-rotates from gravity (always
        // world-horizontal), and the tally pip stays upright in the user's view via chrome's
        // `uiRotationDeg`.
        val settings = hudState.current
        if (settings.showHorizonLevel && knownBuf) {
            HorizonLevelOverlay(modifier = Modifier.fillMaxSize())
        }
        if (isRecording && settings.showVideoTally && knownBuf) {
            VideoTallyOverlay(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = uiRotationDeg
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
            )
        }
    }
}

/** Bottom tray: optional thumbnail (start), dual shutters + shooting-mode dial (end, dial right of shutters). */
@Composable
private fun PreviewBottomCaptureTray(
    lastGalleryUri: Uri?,
    showOnScreenShutter: Boolean,
    canCaptureRawStill: Boolean,
    onCaptureDng: () -> Unit,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Mode dial / Tune FAB — anchored to the bottom-right as the right neighbour of the shutters. */
    shootingModesSlot: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
    /** When true, photo shutter is primary (center); when false, video record is primary. */
    var primaryPhoto by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(showOnScreenShutter) {
        if (showOnScreenShutter) {
            Log.i("PNS.ChromeUx", "dualShutter=visible")
        }
    }

    LaunchedEffect(lastGalleryUri) {
        thumbBitmap?.recycle()
        thumbBitmap = null
        val u = lastGalleryUri ?: return@LaunchedEffect
        thumbBitmap = loadGalleryThumbnail(context.applicationContext, u)
    }

    DisposableEffect(Unit) {
        onDispose {
            thumbBitmap?.recycle()
        }
    }

    /** Match gallery thumb width so the shutter cluster stays centered in the window (symmetric gutters). */
    val edgeSlotWidth = PreviewGalleryThumbSize
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PreviewBottomTrayHeight)
                .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tapUri = lastGalleryUri
            Box(
                modifier = Modifier.width(edgeSlotWidth),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (tapUri != null) {
                    val openLast: () -> Unit = {
                        openMediaWithSystemResolver(context, tapUri)
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(PreviewGalleryThumbSize)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable(onClick = openLast),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = thumbBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Last capture",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                "…",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showOnScreenShutter) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (primaryPhoto) {
                                FloatingActionButton(
                                    onClick = { primaryPhoto = false },
                                    modifier = Modifier.size(52.dp).alpha(0.38f),
                                    containerColor = PnsColors.RecordRed,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "▶",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                FloatingActionButton(
                                    onClick = {
                                        if (canCaptureRawStill) {
                                            onCaptureDng()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "DNG: ≤119 fps preview (RAW session)",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .size(64.dp)
                                            .alpha(if (canCaptureRawStill) 1f else 0.45f),
                                    containerColor = PnsColors.PhotoOrange,
                                    contentColor = Color.Black,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "●",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.Black,
                                    )
                                }
                            } else {
                                FloatingActionButton(
                                    onClick = {
                                        if (isRecording) {
                                            onRecordingChange(false)
                                        }
                                        primaryPhoto = true
                                    },
                                    modifier = Modifier.size(52.dp).alpha(0.38f),
                                    containerColor = PnsColors.PhotoOrange,
                                    contentColor = Color.Black,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "●",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black,
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                FloatingActionButton(
                                    onClick = { onRecordingChange(!isRecording) },
                                    modifier = Modifier.size(64.dp),
                                    containerColor = PnsColors.RecordRed,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        if (isRecording) "■" else "●",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.width(edgeSlotWidth),
                contentAlignment = Alignment.CenterEnd,
            ) {
                shootingModesSlot?.invoke()
            }
        }
    }
}

/**
 * Quick-setting block used under the 7×7 grid. When [showIconHeader] is **false** (preview chrome),
 * expansion opens a **modal [Dialog]** instead of stacking panels below the grid (grid icons still
 * toggle the same [expanded] state).
 */
@Composable
private fun ShortcutBlock(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showIconHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!showIconHeader && expanded) {
        Dialog(onDismissRequest = { onExpandedChange(false) }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 12.dp,
                color = Color.Black.copy(alpha = 0.94f),
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        TextButton(onClick = { onExpandedChange(false) }) {
                            Text("Close", color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    content()
                }
            }
        }
        return
    }
    if (!showIconHeader && !expanded) {
        return
    }
    Column(
        modifier
            .then(modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIconHeader) {
            Box(
                modifier = Modifier.padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconCubeVectorButton(
                    onClick = { onExpandedChange(!expanded) },
                    contentDescription = title,
                    selected = expanded,
                    imageVector = icon,
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PreviewChromeScrollSlot(
    spec: ChromeGridSlotSpec,
    expandedKey: String?,
    onToggleShortcutTitle: (String) -> Unit,
    hudState: HudSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    uiRotationDeg: Float,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    layoutPortrait: Boolean,
) {
    val context = LocalContext.current
    val hud = hudState.current
    val rot = Modifier.chromeGlyphRotation(uiRotationDeg)
    when (spec) {
        is ChromeGridSlotSpec.ExpandShortcut ->
            IconCubeVectorButton(
                onClick = { onToggleShortcutTitle(spec.title) },
                contentDescription = spec.contentDescription,
                imageVector = spec.icon,
                selected = expandedKey == spec.title,
                size = PreviewChromeGridIconSize,
                modifier = rot,
            )
        is ChromeGridSlotSpec.QuickAction -> {
            val selectedQuick =
                when (spec.kind) {
                    ChromeGridQuickAction.CycleStillsLut ->
                        hud.stillsLut() != LutCatalog.None
                    ChromeGridQuickAction.ToggleHistogram ->
                        hud.showHistogram
                    ChromeGridQuickAction.TimerStub ->
                        chromePrefs.current.selfTimerDelaySec > 0
                    ChromeGridQuickAction.FlashStub ->
                        false
                    ChromeGridQuickAction.ToggleHorizonLevel ->
                        hud.showHorizonLevel
                    ChromeGridQuickAction.ToggleEyeAfOverlay ->
                        hud.showEyeAfOverlay
                    ChromeGridQuickAction.ToggleVideoTally ->
                        hud.showVideoTally
                    ChromeGridQuickAction.ToggleMaxBrightnessPreview ->
                        chromePrefs.current.maxBrightnessInPreview
                    ChromeGridQuickAction.ToggleDndInPreview ->
                        chromePrefs.current.dndWhileInPreview
                    ChromeGridQuickAction.ToggleTapPreviewCapture ->
                        chromePrefs.current.tapPreviewToCapture
                    ChromeGridQuickAction.ToggleVolumeKeysCapture ->
                        chromePrefs.current.volumeKeysCapture
                    ChromeGridQuickAction.ToggleSaveLocation ->
                        chromePrefs.current.saveLocationWithMedia && fineLocationGranted
                    ChromeGridQuickAction.CyclePreviewSpin ->
                        effectivePreviewStaticRotationDeg(
                            chromePrefs.current.staticPreviewRotationDeg,
                            layoutPortrait,
                        ) != 0
                }
            IconCubeVectorButton(
                onClick = {
                    when (spec.kind) {
                        ChromeGridQuickAction.CycleStillsLut ->
                            cycleStillsLutQuick(hudState)
                        ChromeGridQuickAction.ToggleHistogram -> {
                            val cur = hudState.current
                            hudState.update(
                                cur.copy(showHistogram = !cur.showHistogram),
                            )
                        }
                        ChromeGridQuickAction.FlashStub ->
                            Toast.makeText(
                                context,
                                "Flash — not wired to AE/flash units yet.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        ChromeGridQuickAction.TimerStub -> {
                            val next =
                                PreviewChromePreferences.cycleSelfTimerDelaySec(
                                    chromePrefs.current.selfTimerDelaySec,
                                )
                            chromePrefs.update(
                                chromePrefs.current.copy(selfTimerDelaySec = next),
                            )
                            val msg =
                                if (next == 0) {
                                    "Self-timer off"
                                } else {
                                    "Self-timer ${next}s"
                                }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            Log.i("PNS.ChromeUx", "selfTimerSec=$next")
                        }
                        ChromeGridQuickAction.ToggleHorizonLevel -> {
                            val cur = hudState.current
                            hudState.update(
                                cur.copy(showHorizonLevel = !cur.showHorizonLevel),
                            )
                        }
                        ChromeGridQuickAction.ToggleEyeAfOverlay -> {
                            val cur = hudState.current
                            hudState.update(
                                cur.copy(showEyeAfOverlay = !cur.showEyeAfOverlay),
                            )
                        }
                        ChromeGridQuickAction.ToggleVideoTally -> {
                            val cur = hudState.current
                            hudState.update(
                                cur.copy(showVideoTally = !cur.showVideoTally),
                            )
                        }
                        ChromeGridQuickAction.ToggleMaxBrightnessPreview -> {
                            val c = chromePrefs.current
                            chromePrefs.update(
                                c.copy(maxBrightnessInPreview = !c.maxBrightnessInPreview),
                            )
                        }
                        ChromeGridQuickAction.ToggleDndInPreview -> {
                            val c = chromePrefs.current
                            chromePrefs.update(
                                c.copy(dndWhileInPreview = !c.dndWhileInPreview),
                            )
                        }
                        ChromeGridQuickAction.ToggleTapPreviewCapture -> {
                            val c = chromePrefs.current
                            chromePrefs.update(
                                c.copy(tapPreviewToCapture = !c.tapPreviewToCapture),
                            )
                        }
                        ChromeGridQuickAction.ToggleVolumeKeysCapture -> {
                            val c = chromePrefs.current
                            chromePrefs.update(
                                c.copy(volumeKeysCapture = !c.volumeKeysCapture),
                            )
                        }
                        ChromeGridQuickAction.ToggleSaveLocation -> {
                            val c = chromePrefs.current
                            if (c.saveLocationWithMedia && fineLocationGranted) {
                                chromePrefs.update(c.copy(saveLocationWithMedia = false))
                                CaptureLocationBridge.update(null)
                            } else if (fineLocationGranted) {
                                chromePrefs.update(c.copy(saveLocationWithMedia = true))
                            } else {
                                onPendingEnableGeotagChange(true)
                                onRequestLocationForGeotag()
                            }
                        }
                        ChromeGridQuickAction.CyclePreviewSpin -> {
                            val c = chromePrefs.current
                            val nextDeg =
                                PreviewChromePreferences.normalizeStaticRotation(
                                    c.staticPreviewRotationDeg + 90,
                                )
                            chromePrefs.update(c.copy(staticPreviewRotationDeg = nextDeg))
                        }
                    }
                },
                contentDescription = spec.contentDescription,
                imageVector = spec.icon,
                selected = selectedQuick,
                size = PreviewChromeGridIconSize,
                modifier = rot,
            )
        }
    }
}

@Composable
private fun PreviewChromeGrid7x7(
    modifier: Modifier = Modifier,
    cameraIds: List<String>,
    selectedCameraId: String?,
    focalCrop: FocalMode?,
    onApplyFocalMmSlot: (FocalMmSlot) -> Unit,
    expandedKey: String?,
    onToggleShortcutTitle: (String) -> Unit,
    hudState: HudSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    uiRotationDeg: Float,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    layoutPortrait: Boolean,
) {
    val context = LocalContext.current
    val focalSlots = FocalMmSlot.entries

    LaunchedEffect(Unit) {
        Log.i(
            "PNS.ChromeUx",
            "quickGrid=focalRow7_packedScrollSlots targetFpsOnReadout=true",
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (c in 0 until 7) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c < focalSlots.size) {
                        val slot = focalSlots[c]
                        val enabled =
                            resolveFocalMmSlot(context.applicationContext, slot, cameraIds) != null
                        val selected =
                            focalMmSlotIsActive(
                                context.applicationContext,
                                slot,
                                cameraIds,
                                selectedCameraId,
                                focalCrop,
                            )
                        FpsQuickChip(
                            label = slot.labelMm,
                            selected = selected,
                            requiresRoot = false,
                            enabled = enabled,
                            onClick = { onApplyFocalMmSlot(slot) },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                    .chromeGlyphRotation(uiRotationDeg),
                        )
                    }
                }
            }
        }
        val ordered =
            previewChromeGridSlots.sortedWith(
                compareBy({ it.row }, { it.col }),
            )
        ordered.chunked(7).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                chunk.forEach { spec ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PreviewChromeScrollSlot(
                            spec = spec,
                            expandedKey = expandedKey,
                            onToggleShortcutTitle = onToggleShortcutTitle,
                            hudState = hudState,
                            chromePrefs = chromePrefs,
                            uiRotationDeg = uiRotationDeg,
                            fineLocationGranted = fineLocationGranted,
                            onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                            onRequestLocationForGeotag = onRequestLocationForGeotag,
                            layoutPortrait = layoutPortrait,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewRightRail(
    modifier: Modifier = Modifier,
    uiRotationDeg: Float,
    cameraIds: List<String>,
    onApplyFocalMmSlot: (FocalMmSlot) -> Unit,
    onOpenHudSettings: (HudSettingsFocus) -> Unit,
    onOpenDeveloperMenu: () -> Unit,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    selectedFps: Int,
    onSetFps: (Int) -> Unit,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    isSweeping: Boolean,
    onPickFirstCamera: () -> Unit,
    onStartSweep: () -> Unit,
    onStopSweep: () -> Unit,
    status: String,
    surfaceInfo: String,
    measuredFps: Double,
    selectedCameraId: String?,
    focalCrop: FocalMode?,
    imagingProfile: ImagingProfile,
    onCycleImagingProfile: () -> Unit,
    onCaptureDng: () -> Unit,
    onBracketBurst: (BracketPattern) -> Unit,
    canCaptureRawStill: Boolean,
    canCaptureBracketBurst: Boolean,
    commandDialMode: CommandDialMode,
    onCalibrateFromPreviewFrame: () -> Unit,
    previewJpegCompanion: Boolean,
    rawStillNotReadyReason: String?,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    layoutPortrait: Boolean,
) {
    val context = LocalContext.current
    val chrome = chromePrefs.current
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val dialogScroll = rememberScrollState()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        PreviewChromeGrid7x7(
            modifier = Modifier.fillMaxWidth(),
            cameraIds = cameraIds,
            selectedCameraId = selectedCameraId,
            focalCrop = focalCrop,
            onApplyFocalMmSlot = onApplyFocalMmSlot,
            expandedKey = expandedKey,
            onToggleShortcutTitle = { title ->
                expandedKey = if (expandedKey == title) null else title
            },
            hudState = hudState,
            chromePrefs = chromePrefs,
            uiRotationDeg = uiRotationDeg,
            fineLocationGranted = fineLocationGranted,
            onPendingEnableGeotagChange = onPendingEnableGeotagChange,
            onRequestLocationForGeotag = onRequestLocationForGeotag,
            layoutPortrait = layoutPortrait,
        )
        expandedKey?.let { key ->
            Dialog(onDismissRequest = { expandedKey = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A1A),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        Modifier
                            .padding(12.dp)
                            .widthIn(max = 420.dp)
                            .verticalScroll(dialogScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(key, style = MaterialTheme.typography.titleSmall, color = Color.White)
                            TextButton(onClick = { expandedKey = null }) {
                                Text("Close", color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        when (key) {
                            "Settings" -> {
                                OutlinedButton(
                                    onClick = { onOpenHudSettings(HudSettingsFocus.None) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("HUD settings", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onOpenDeveloperMenu,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Developer menu", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            "Target FPS" -> {
                                for (opt in fpsOptions) {
                                    FpsQuickChip(
                                        label = "${opt.targetFps}",
                                        selected = opt.targetFps == selectedFps,
                                        requiresRoot = opt.requiresRoot,
                                        onClick = {
                                            if (opt.requiresRoot && opt.targetFps != selectedFps) {
                                                Toast.makeText(
                                                    context,
                                                    "Root-only on this camera: ${opt.targetFps} fps is not advertised without root or vendor unlock. You can still try; the app falls back if the HAL rejects it.",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            onSetFps(opt.targetFps)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (selectedFps >= 120 && focalCrop != null) {
                                    Text(
                                        "≥120 fps: crop off",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PnsColors.WarnAmber,
                                        maxLines = 2,
                                    )
                                }
                            }
                            "Guides" -> {
                                OutlinedButton(
                                    onClick = {
                                        val latest = compositionGuide.current
                                        compositionGuide.update(latest.copy(cropGuide = latest.cropGuide.next()))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Crop\n${compositionGuide.current.cropGuide.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val latest = compositionGuide.current
                                        compositionGuide.update(latest.copy(gridMode = latest.gridMode.next()))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = compositionGuide.current.gridMode.label.replace(' ', '\n'),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            "Preview & keys" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Shuttr\nbtn",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Switch(
                                        checked = chrome.showOnScreenShutter,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(showOnScreenShutter = it))
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Tap\npreview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Switch(
                                        checked = chrome.tapPreviewToCapture,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(tapPreviewToCapture = it))
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Zoom-fill\npreview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Switch(
                                        checked = chrome.previewTextureCoverCrop,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(previewTextureCoverCrop = it))
                                        },
                                    )
                                }
                                Text(
                                    text =
                                        if (chrome.previewTextureCoverCrop) {
                                            "On: finder fills the tile (may crop tighter than JPEG/DNG)."
                                        } else {
                                            "Off: letterboxed preview matches still framing (whole sensor crop visible)."
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.48f),
                                    maxLines = 3,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Chart\ngrid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Switch(
                                        checked = chrome.liveChartCornerOverlay,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(liveChartCornerOverlay = it))
                                        },
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCalibrateFromPreviewFrame,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Calibrate\nfrom preview",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Max\nbright",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Switch(
                                        checked = chrome.maxBrightnessInPreview,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(maxBrightnessInPreview = it))
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "DND\nprev",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Switch(
                                        checked = chrome.dndWhileInPreview,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(dndWhileInPreview = it))
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "DND\nrec",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Switch(
                                        checked = chrome.dndWhileRecording,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(dndWhileRecording = it))
                                        },
                                    )
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val nm = context.getSystemService(NotificationManager::class.java)
                                    val wantsPolicy = chrome.dndWhileInPreview || chrome.dndWhileRecording
                                    if (wantsPolicy && nm != null && !nm.isNotificationPolicyAccessGranted) {
                                        TextButton(
                                            onClick = {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                "Policy\naccess",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PnsColors.RootAccentBlue,
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Vol\nkeys",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Switch(
                                        checked = chrome.volumeKeysCapture,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(volumeKeysCapture = it))
                                        },
                                    )
                                }
                            }
                            "Capture & tools" -> {
                                OutlinedButton(onClick = onCycleImagingProfile, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Profile\n${imagingProfile.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Button(
                                    onClick = onCaptureDng,
                                    enabled = canCaptureRawStill,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Save DNG", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "RAW+\n(JPEG)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Switch(
                                        checked = chrome.stillCaptureJpegCompanion,
                                        onCheckedChange = {
                                            chromePrefs.update(chrome.copy(stillCaptureJpegCompanion = it))
                                        },
                                    )
                                }
                                Text(
                                    text =
                                        if (previewJpegCompanion) {
                                            "Still pipeline: RAW + JPEG companion (readout RAW+)"
                                        } else {
                                            "Still pipeline: RAW DNG only (no JPEG sidecar in this session)"
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 3,
                                )
                                rawStillNotReadyReason?.let { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PnsColors.WarnAmber,
                                        maxLines = 4,
                                    )
                                }
                                if (commandDialMode == CommandDialMode.BKT) {
                                    Text(
                                        "BKT RAW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        for ((pat, label) in
                                            listOf(
                                                BracketPattern.Three to "×3",
                                                BracketPattern.Five to "×5",
                                                BracketPattern.Seven to "×7",
                                            )) {
                                            Button(
                                                onClick = { onBracketBurst(pat) },
                                                enabled = canCaptureBracketBurst,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                if (!canCaptureRawStill) {
                                    Text(
                                        "DNG needs ≤119 fps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PnsColors.WarnAmber,
                                        maxLines = 2,
                                    )
                                }
                                Button(onClick = onPickFirstCamera, modifier = Modifier.fillMaxWidth()) {
                                    Text("1st cam", style = MaterialTheme.typography.labelSmall)
                                }
                                if (!isSweeping) {
                                    Button(onClick = onStartSweep, modifier = Modifier.fillMaxWidth()) {
                                        Text("Sweep", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(onClick = onStopSweep, modifier = Modifier.fillMaxWidth()) {
                                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    "cam ${selectedCameraId ?: "?"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                                Text(
                                    status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.45f),
                                    maxLines = 3,
                                )
                                Text(
                                    surfaceInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f),
                                    maxLines = 2,
                                )
                                Text(
                                    "${"%.1f".format(measuredFps)} fps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

/**
 * @param showHorizonLevel when **false**, suppresses the horizon bar so the caller can render
 * it outside this overlay's rotation context (the bar must self-rotate from gravity, not be
 * inherited from a parent `graphicsLayer`).
 * @param showVideoTallyPip when **false**, suppresses the recording tally so the caller can
 * render it outside the rotated content box (the pip is chrome, it should rotate with the
 * chrome rather than with the static preview rotation).
 */
@Composable
private fun PreviewCenterOverlay(
    modifier: Modifier = Modifier,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    isRecording: Boolean,
    eyeMarks: List<EyeMark>,
    uiRotationDeg: Float,
    tapToShootEnabled: Boolean,
    tapShootCallbacks: TapToShootCallbacks,
    onRequestVolumeKeyFocus: () -> Unit,
    showHorizonLevel: Boolean = true,
    showVideoTallyPip: Boolean = true,
) {
    val settings = hudState.current
    val guides = compositionGuide.current
    val focusTap = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier.then(
                if (tapToShootEnabled) {
                    Modifier.tapToShoot(tapShootCallbacks)
                } else {
                    Modifier.clickable(
                        interactionSource = focusTap,
                        indication = null,
                    ) {
                        onRequestVolumeKeyFocus()
                    }
                },
            ),
    ) {
        // Composition guide stays aligned to the camera buffer (preview never rotates), so
        // it does NOT inherit chrome rotation — the rule-of-thirds lines belong on the
        // sensor frame, not on the user's view.
        CompositionGuideOverlay(
            crop = guides.cropGuide,
            grid = guides.gridMode,
            modifier = Modifier.fillMaxSize(),
        )
        if (showHorizonLevel && settings.showHorizonLevel) {
            HorizonLevelOverlay(modifier = Modifier.fillMaxSize())
        }
        // Eye-AF marks live in **buffer** coords (mapped via [TexturePreviewFit.mapBufferToView]
        // upstream), so they also stay locked to the preview, not to the rotating chrome.
        if (settings.showEyeAfOverlay) {
            EyeAfOverlay(eyes = eyeMarks, modifier = Modifier.fillMaxSize())
        }
        if (showVideoTallyPip && settings.showVideoTally && isRecording) {
            VideoTallyOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = uiRotationDeg
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
            )
        }
        if (settings.showFocusPeaking) {
            // Phase 1+ shader — placeholder frame only
        }
        if (settings.showHistogram) {
            // Experimental — off by default
        }
    }
}

@Composable
private fun FpsQuickChip(
    label: String,
    selected: Boolean,
    requiresRoot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val borderColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.12f)
            selected -> PnsColors.PhotoOrange
            requiresRoot -> PnsColors.RootAccentBlue
            else -> Color.White.copy(alpha = 0.35f)
        }
    val bg =
        when {
            !enabled -> Color.Black.copy(alpha = 0.25f)
            selected -> PnsColors.PhotoOrange
            else -> Color.Black.copy(alpha = 0.45f)
        }
    val fg =
        when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            selected -> Color.Black
            requiresRoot && !selected -> PnsColors.RootAccentBlue
            else -> Color.White.copy(alpha = 0.92f)
        }
    Box(
        modifier =
            modifier
                .height(44.dp)
                .widthIn(min = PnsDimens.quickSettingsChipMinWidth)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .background(bg)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

private class PreviewController(
    private val appContext: Context,
) {
    private val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val tag = "PNS.Cam"

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var previewSurfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var selectedCameraId: String? = null
    private var desiredFps: Int = 60

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    private var lastStatus: String = "Idle"

    @Volatile private var desiredHighSpeedSize: Size? = null
    @Volatile private var desiredSurfaceSize: Size? = null
    @Volatile private var currentSurfaceSize: Size? = null
    @Volatile private var generation: Long = 0L
    private var lastTransformLogKey: String? = null

    // Frame-rate measurement (based on capture result timestamps).
    @Volatile private var lastTimestampNs: Long = 0L
    @Volatile private var smoothedFps: Double = 0.0
    @Volatile private var framesWithTimestamp: Long = 0L
    @Volatile private var framesMissingTimestamp: Long = 0L
    @Volatile private var lastWallNs: Long = 0L
    @Volatile private var smoothedWallFps: Double = 0.0
    @Volatile private var lastFrameNs: Long = 0L
    @Volatile private var smoothedFrameFps: Double = 0.0
    @Volatile private var framesFromTexture: Long = 0L
    /**
     * Mirrors [PreviewChromePreferences.previewTextureCoverCrop] for tap/metering math in buffer space.
     */
    @Volatile private var previewTextureCoverCrop: Boolean = true
    /** When true and still pipeline uses [CaptureLocationBridge.snapshot], embed GPS in DNG/JPEG EXIF. */
    @Volatile private var stillEmbedLocationInFiles: Boolean = false
    @Volatile private var textureWindowStartNs: Long = 0L
    @Volatile private var textureWindowFrames: Long = 0L

    /** Latest repeating-request metadata for the preview readout strip (Milestone 9). */
    @Volatile private var lastPreviewIso: Int? = null
    @Volatile private var lastPreviewExposureNs: Long? = null
    @Volatile private var lastPreviewAwbMode: Int? = null

    /** Null = automatic AE for sensitivity / exposure time; non-null forces manual sensor row (AE off). */
    @Volatile private var manualIsoOverride: Int? = null

    @Volatile private var manualExposureNsOverride: Long? = null

    /** Null = do not override AWB; non-null forces [CaptureRequest.CONTROL_AWB_MODE]. */
    @Volatile private var manualAwbModeOverride: Int? = null
    private var loggedChromeUxReadout: Boolean = false
    private var readoutFallbackRunnable: Runnable? = null

    private var rawImageReader: ImageReader? = null
    /** Hardware JPEG still target for RAW+JPEG dual capture (tonal companion). */
    private var jpegImageReader: ImageReader? = null
    /** Encode / DNG save lane — CAPTURE_ARCHITECTURE.md (`PNS.Reader`). */
    private val ioExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "PNS.Reader").apply { isDaemon = true }
        }

    /** YUV histogram + highlight metering — CAPTURE_ARCHITECTURE.md (`PNS.Meter`). */
    private val meterExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "PNS.Meter").apply { isDaemon = true }
        }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureBusy = AtomicBoolean(false)

    private val faceTracker = TrackerState()

    /** User tap on preview → AF/AE metering patch (sensor space); cleared when camera id changes. */
    private var tapMeteringRect: MeteringRectangle? = null

    /** When true, skip high-frequency FPS `Log.d` lines so scripted ADB runs retain early `PNS.AdbValidation` lines in logcat. */
    @Volatile
    var suppressPeriodicFpsLogs: Boolean = false

    /**
     * When true (sequential RAW / bracket automation only), face detect + tracker are silenced to cut log noise.
     * Dial-only automation keeps this **false** so Eye-AF / 3D tracking validation can run.
     */
    @Volatile
    var automationSuppressFacePipeline: Boolean = false

    /** Scripted Super Macro vendor close-up request (`EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE`). */
    @Volatile
    var superMacroAdbProbe: Boolean = false

    private var loggedSuperMacroProbeWrongCam: Boolean = false
    private var loggedSuperMacroProbeUw: Boolean = false

    /** True when OPLUS macro close-up was applied via [SessionConfiguration.setSessionParameters] (API 33+). */
    private var superMacroSessionConfigured: Boolean = false

    /** Log once per camera session for ADB validation evidence. */
    private var loggedFaceDetectCaps: Boolean = false
    private var loggedFaceStatisticsSample: Boolean = false

    /** Last locked-id set we logged for `PNS.AdbValidation` tracker lines (avoid per-frame spam). */
    private var lastTrackerLockedLogged: Set<Int>? = null

    private var loggedAdbTrackerPipelineReady: Boolean = false

    private var lastHighlightMeterAdbLogMs: Long = 0L

    private var commandDialMode: CommandDialMode = CommandDialMode.M

    private var yuvImageReader: ImageReader? = null

    @Volatile
    private var lastAppliedHighlightComp: Int? = null

    private var lastHighlightProcessWallMs: Long = 0L

    private val highlightMeterMinIntervalMs: Long = 220L

    private var hudFaceOverlayEnabled: Boolean = false

    private var eyeMarksListener: ((List<EyeMark>) -> Unit)? = null

    fun cameraIds(): List<String> =
        runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())

    /** Live roster-aware caps for [CapabilityGate] (BUILD_PLAN Sprint 5.3). */
    fun hardwareCaps(): HardwareCaps {
        val ids = cameraIds()
        val active =
            selectedCameraId
                ?: BackCameraRoleResolver.resolve(cm, ids).wide
                ?: ids.firstOrNull()
        return HardwareCapsSnapshot.build(cm, active, ids)
    }

    fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        Log.d(tag, "textureAvailable ${width}x${height}")
        previewSurfaceTexture = st
        reconcilePreviewBufferSizeFromCallbacks(width, height)
        // Never call setDefaultBufferSize(width,height) from TextureView hints alone — those are often
        // the **layout** size, not the negotiated camera stream; wrong aspect → non-uniform scale
        // (horizontal stretch) vs RAW. [maybeRestartBody] sets the authoritative size from Camera2.
        if (desiredSurfaceSize == null && width > 0 && height > 0) {
            runCatching { st.setDefaultBufferSize(width, height) }
        }
        rebuildSurfaceIfPossible()
        maybeRestart()
    }

    fun onSurfaceTextureSizeChanged(width: Int, height: Int) {
        Log.d(tag, "textureSizeChanged ${width}x${height}")
        reconcilePreviewBufferSizeFromCallbacks(width, height)
        maybeRestart()
    }

    /**
     * Keep [currentSurfaceSize] aligned with [desiredSurfaceSize] once the camera stream is
     * negotiated. TextureView listener width/height are **view/buffer surface hints** and on some
     * OEMs diverge from [setDefaultBufferSize] — treating them as the camera buffer WxH breaks
     * [TexturePreviewFit] aspect math and distorts the chart vs RAW.
     */
    private fun reconcilePreviewBufferSizeFromCallbacks(textureWidth: Int, textureHeight: Int) {
        val want = desiredSurfaceSize
        currentSurfaceSize =
            when {
                want != null -> want
                textureWidth > 0 && textureHeight > 0 -> Size(textureWidth, textureHeight)
                else -> null
            }
    }

    /**
     * Called by [TextureView.SurfaceTextureListener.onSurfaceTextureDestroyed]. We accept the
     * destroyed [SurfaceTexture] so we can identity-compare against [previewSurfaceTexture]
     * — on rotation, Compose may detach the old [TextureView] and attach a new one whose
     * `onSurfaceTextureAvailable` lands on the main thread *before* this teardown finishes.
     * If we cleared fields unconditionally we'd nuke the *new* state, leaving the preview
     * permanently black until the user navigated away.
     */
    fun onSurfaceTextureDestroyed(destroyedSt: SurfaceTexture) {
        Log.d(tag, "textureDestroyed")
        ensureThread()
        val isCurrent = previewSurfaceTexture === destroyedSt
        val surfaceToRelease: Surface? =
            if (isCurrent) {
                val s = previewSurface
                previewSurfaceTexture = null
                previewSurface = null
                currentSurfaceSize = null
                s
            } else {
                // A newer onSurfaceTextureAvailable has already replaced our refs; leave them
                // alone. The dead Surface that wrapped destroyedSt is unreachable from here —
                // it'll be GC'd. TextureView itself releases the SurfaceTexture (we return
                // true from onSurfaceTextureDestroyed in the listener).
                null
            }
        val h = handler
        if (h == null) {
            runCatching { surfaceToRelease?.release() }
            closeCamera()
            return
        }
        // Close camera before releasing the Surface so teardown cannot race createCaptureSession.
        h.post {
            closeCamera()
            mainHandler.post {
                runCatching { surfaceToRelease?.release() }
            }
        }
    }

    fun onTextureUpdated() {
        // Called when TextureView has rendered a new frame.
        framesFromTexture++
        textureWindowFrames++
        val now = SystemClock.elapsedRealtimeNanos()
        if (textureWindowStartNs <= 0L) {
            textureWindowStartNs = now
        }
        val prev = lastFrameNs
        lastFrameNs = now
        if (prev <= 0L) return
        val dt = now - prev
        if (dt <= 0L) return
        val inst = 1e9 / dt.toDouble()
        smoothedFrameFps = if (smoothedFrameFps <= 0.0) inst else (smoothedFrameFps * 0.90 + inst * 0.10)
    }

    /**
     * Copy the current preview into a software [Bitmap] for in-app calibration (Sprint 6.2).
     * Must be called on the main thread ([TextureView.getBitmap]).
     */
    fun grabPreviewFrameBitmap(textureView: TextureView): Bitmap? {
        if (textureView.surfaceTexture == null) {
            Log.w(tag, "grabPreviewFrameBitmap: SurfaceTexture not ready")
            return null
        }
        val vw = textureView.width
        val vh = textureView.height
        if (vw <= 0 || vh <= 0) {
            Log.w(tag, "grabPreviewFrameBitmap: bad view size ${vw}x${vh}")
            return null
        }
        return try {
            val bmp = textureView.getBitmap(vw, vh)
            if (bmp == null) {
                Log.w(tag, "grabPreviewFrameBitmap: getBitmap returned null")
                null
            } else {
                Log.i(
                    "PNS.AdbValidation",
                    "calibrate preview frame grab ok ${bmp.width}x${bmp.height}",
                )
                bmp
            }
        } catch (ex: Exception) {
            Log.w(tag, "grabPreviewFrameBitmap failed: ${ex.message}")
            null
        }
    }

    fun setDesired(selectedCameraId: String?, desiredFps: Int) {
        val camChanged = this.selectedCameraId != selectedCameraId
        val changed = camChanged || this.desiredFps != desiredFps
        if (camChanged) {
            tapMeteringRect = null
            manualIsoOverride = null
            manualExposureNsOverride = null
            manualAwbModeOverride = null
        }
        this.selectedCameraId = selectedCameraId
        this.desiredFps = desiredFps
        if (changed) Log.d(tag, "setDesired cameraId=${selectedCameraId ?: "null"} fps=$desiredFps")
        if (changed) maybeRestart()
    }

    private var focalCropMode: FocalMode? = null

    /** BUILD_PLAN §3 digital crop; restarts preview when changed. */
    fun setFocalCrop(mode: FocalMode?) {
        if (focalCropMode == mode) return
        focalCropMode = mode
        Log.d(tag, "setFocalCrop mode=${mode?.name ?: "null"}")
        maybeRestart()
    }

    /** BUILD_PLAN §4: highlight metering uses a YUV analysis surface when dial is [CommandDialMode.H]. */
    fun setCommandDialMode(mode: CommandDialMode) {
        if (commandDialMode == mode) return
        commandDialMode = mode
        Log.d(tag, "setCommandDialMode mode=$mode")
        maybeRestart()
    }

    fun setPreviewTextureCoverCrop(coverCrop: Boolean) {
        if (previewTextureCoverCrop == coverCrop) return
        previewTextureCoverCrop = coverCrop
        Log.d(tag, "setPreviewTextureCoverCrop coverCrop=$coverCrop")
    }

    /** BUILD_PLAN §4 Eye-AF: enable face detect + publish [EyeMark]s when HUD toggle is on. */
    fun setHudFaceOverlayEnabled(enabled: Boolean) {
        if (hudFaceOverlayEnabled == enabled) return
        hudFaceOverlayEnabled = enabled
        Log.d(tag, "setHudFaceOverlayEnabled enabled=$enabled")
        if (!enabled) {
            faceTracker.reset()
            lastTrackerLockedLogged = null
            publishEyeMarks(emptyList())
        }
        refreshRepeatingPreviewOnly()
    }

    fun setEyeMarksListener(listener: ((List<EyeMark>) -> Unit)?) {
        eyeMarksListener = listener
    }

    /**
     * Negotiated Camera2 preview stream size ([desiredSurfaceSize]), falling back to the current
     * [TextureView] buffer. UI center-crop mapping must match [applyPreviewTextureTransform], which uses
     * [desiredSurfaceSize] — using only [currentSurfaceSize] caused horizontal stretch when they
     * diverged briefly or after layout.
     */
    fun previewBufferSize(): Size? = desiredSurfaceSize ?: currentSurfaceSize

    /**
     * Reports the active camera's `SENSOR_ORIENTATION` (degrees, multiple of 90), or `null` if
     * the characteristic isn't available. Used by the diagnostic probe overlay so the user can
     * verify "where the sensor's natural up direction is" without diving into logcat.
     */
    fun sensorOrientationDegrees(): Int? {
        val camId = selectedCameraId ?: return null
        return runCatching {
            cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SENSOR_ORIENTATION)
        }.getOrNull()
    }

    fun readoutMenuSnapshot(): ReadoutMenuSnapshot {
        val camId = selectedCameraId ?: return ReadoutMenuSnapshot.EMPTY
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            ?: return ReadoutMenuSnapshot.EMPTY
        return ReadoutMenuSnapshot(
            isoChoices = ReadoutExposureCatalog.isoChoices(chars),
            exposureChoices = ReadoutExposureCatalog.exposureChoices(chars),
            awbChoices = ReadoutExposureCatalog.awbChoices(chars),
        )
    }

    fun setReadoutManualIsoExposure(
        iso: Int?,
        exposureNs: Long?,
    ) {
        manualIsoOverride = iso
        manualExposureNsOverride = exposureNs
        refreshRepeatingPreviewOnly()
    }

    /** Adjust ISO only; keeps the current manual shutter selection (or auto). */
    fun setReadoutManualIso(iso: Int?) {
        manualIsoOverride = iso
        refreshRepeatingPreviewOnly()
    }

    /** Adjust shutter only; keeps the current manual ISO selection (or auto). */
    fun setReadoutManualShutter(exposureNs: Long?) {
        manualExposureNsOverride = exposureNs
        refreshRepeatingPreviewOnly()
    }

    fun setReadoutManualAwbMode(mode: Int?) {
        manualAwbModeOverride = mode
        refreshRepeatingPreviewOnly()
    }

    fun setStillEmbedLocationInFiles(enabled: Boolean) {
        stillEmbedLocationInFiles = enabled
    }

    private fun locationForStillMetadata(): Location? =
        if (stillEmbedLocationInFiles) CaptureLocationBridge.snapshot() else null

    /** RAW DNG path requires non-HFR session with [ImageReader] attached (BUILD_PLAN §4). */
    fun canCaptureRawStill(): Boolean =
        rawImageReader != null &&
            session != null &&
            device != null &&
            desiredFps < 120 &&
            !selectedCameraId.isNullOrBlank()

    /**
     * When [canCaptureRawStill] is false, explains why (ADB automation / logcat).
     * Returns `null` when ready.
     */
    fun rawStillNotReadyReason(): String? {
        if (canCaptureRawStill()) return null
        val parts = mutableListOf<String>()
        if (rawImageReader == null) parts += "no RAW ImageReader"
        if (session == null) parts += "no capture session"
        if (device == null) parts += "no CameraDevice"
        if (desiredFps >= 120) parts += "HFR path active (desiredFps=$desiredFps)"
        if (selectedCameraId.isNullOrBlank()) parts += "no cameraId"
        return parts.joinToString("; ").ifBlank { "unknown blocker" }
    }

    /** BKT dial + same RAW session constraints as [canCaptureRawStill]. */
    fun canCaptureBracketBurst(): Boolean =
        canCaptureRawStill() && commandDialMode == CommandDialMode.BKT

    private fun jpegImageToByteArray(image: Image): ByteArray {
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }

    private fun rotateBitmapClockwise90(src: Bitmap): Bitmap {
        val m =
            Matrix().apply {
                postRotate(90f)
            }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Decode hardware JPEG, rotate **90° clockwise** so the sRGB companion matches the DNG /
     * preview upright framing, optionally apply [StillRgbLut], re-compress, MediaStore,
     * then [LutCaptureSidecars] when [stillsLut] ≠ None. Writes standard camera EXIF via
     * [StillCaptureMetadata].
     */
    private fun saveHardwareJpegCompanion(
        appContext: Context,
        profile: ImagingProfile,
        jpegImage: Image,
        stillsLut: LutCatalog,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
    ) {
        val jpegBytes = jpegImageToByteArray(jpegImage)
        val decoded =
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: run {
                    Log.w(tag, "companion JPEG decode failed")
                    return
                }
        val oriented = rotateBitmapClockwise90(decoded)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        val w = oriented.width
        val h = oriented.height
        if (w <= 0 || h <= 0) {
            oriented.recycle()
            return
        }
        val px = IntArray(w * h)
        oriented.getPixels(px, 0, w, 0, 0, w, h)
        oriented.recycle()
        val rgb = ByteArray(w * h * 3)
        var o = 0
        for (i in px.indices) {
            val c = px[i]
            rgb[o++] = ((c shr 16) and 0xFF).toByte()
            rgb[o++] = ((c shr 8) and 0xFF).toByte()
            rgb[o++] = (c and 0xFF).toByte()
        }
        if (stillsLut != LutCatalog.None) {
            StillRgbLut.applyToRgb888InPlace(rgb, w, h, stillsLut.load(BuiltInLuts.DEFAULT_SIZE))
        }
        o = 0
        for (i in px.indices) {
            val r = rgb[o++].toInt() and 0xFF
            val g = rgb[o++].toInt() and 0xFF
            val b = rgb[o++].toInt() and 0xFF
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val outBmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val loc = locationForStillMetadata()
        var handle: CaptureStorage.Handle? = null
        try {
            handle =
                CaptureStorage.openOutput(
                    appContext.applicationContext,
                    profile,
                    CaptureStorage.CaptureKind.JpegSdr,
                    useLocationBridge = false,
                )
            if (!outBmp.compress(Bitmap.CompressFormat.JPEG, 92, handle.output)) {
                throw IllegalStateException("JPEG compress failed")
            }
            val displayName = handle.displayName
            val jpegUri = handle.uri
            handle.close()
            handle = null
            StillCaptureMetadata.applyToJpegUri(
                appContext.applicationContext,
                jpegUri,
                characteristics,
                captureResult,
                location = loc,
            )
            LutCaptureSidecars.writeBundledLutSidecarIfNeeded(
                appContext.applicationContext,
                profile,
                displayName,
                stillsLut,
                LutSidecar.CaptureKind.Still,
            )
            Log.d(tag, "companion JPEG ok displayName=$displayName lut=$stillsLut")
        } catch (t: Throwable) {
            Log.w(tag, "companion JPEG save failed", t)
            runCatching { handle?.discard() }
        } finally {
            outBmp.recycle()
        }
    }

    /**
     * Single RAW → DNG via [Dng12Saver] + [CaptureStorage]. Caller supplies display [surfaceRotation]
     * ([android.view.Surface].`ROTATION_*`) for orientation metadata.
     *
     * When [jpegImageReader] is configured, performs RAW+JPEG dual still capture: saves an sRGB JPEG
     * companion (LUT-applied per [stillsLut]) and emits a **bundled** LUT sidecar when applicable.
     */
    fun captureRawStill(
        appContext: Context,
        profile: ImagingProfile,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        /** When set (e.g. `3/10`), logs `PNS.AdbValidation` lines for scripted runs. */
        adbValidationShotLabel: String? = null,
        onResult: (Result<String>) -> Unit,
    ) {
        val shotTag = adbValidationShotLabel
        if (!captureBusy.compareAndSet(false, true)) {
            mainHandler.post {
                if (shotTag != null) {
                    Log.i("PNS.AdbValidation", "captureRawStill $shotTag ok=false err=capture_busy")
                }
                onResult(Result.failure(IllegalStateException("Capture already in progress")))
            }
            return
        }
        val cam = device
        val sess = session
        val reader = rawImageReader
        val jReader = jpegImageReader
        val previewSurf = previewSurface
        val camId = selectedCameraId
        if (cam == null || sess == null || reader == null || previewSurf == null || camId.isNullOrBlank()) {
            captureBusy.set(false)
            mainHandler.post {
                if (shotTag != null) {
                    Log.i(
                        "PNS.AdbValidation",
                        "captureRawStill $shotTag ok=false err=camera_or_raw_not_ready",
                    )
                }
                onResult(
                    Result.failure(
                        IllegalStateException("Camera not ready or RAW unavailable (use preview ≤119 fps)"),
                    ),
                )
            }
            return
        }
        val bgHandler = handler
        if (bgHandler == null) {
            captureBusy.set(false)
            mainHandler.post {
                if (shotTag != null) {
                    Log.i("PNS.AdbValidation", "captureRawStill $shotTag ok=false err=no_camera_handler")
                }
                onResult(Result.failure(IllegalStateException("No camera handler")))
            }
            return
        }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        if (chars == null) {
            captureBusy.set(false)
            mainHandler.post {
                if (shotTag != null) {
                    Log.i("PNS.AdbValidation", "captureRawStill $shotTag ok=false err=no_characteristics")
                }
                onResult(Result.failure(IllegalStateException("No characteristics")))
            }
            return
        }

        val needJpeg = jReader != null
        val still =
            cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(previewSurf)
                addTarget(reader.surface)
                jReader?.let { addTarget(it.surface) }
                applyScalerCropAndMetering(this, chars, camId, aeHighlightCompensationValue())
            }.build()

        val pendingRaw = java.util.concurrent.atomic.AtomicReference<Image?>(null)
        val pendingJpeg = java.util.concurrent.atomic.AtomicReference<Image?>(null)
        val pendingResult = java.util.concurrent.atomic.AtomicReference<TotalCaptureResult?>(null)
        val processed = AtomicBoolean(false)

        fun fail(t: Throwable) {
            if (!processed.compareAndSet(false, true)) return
            reader.setOnImageAvailableListener(null, null)
            jReader?.setOnImageAvailableListener(null, null)
            runCatching { pendingRaw.getAndSet(null)?.close() }
            runCatching { pendingJpeg.getAndSet(null)?.close() }
            pendingResult.set(null)
            captureBusy.set(false)
            if (shotTag != null) {
                Log.i(
                    "PNS.AdbValidation",
                    "captureRawStill $shotTag ok=false err=${t.message}",
                )
            }
            mainHandler.post { onResult(Result.failure(t)) }
        }

        fun maybeProcess() {
            if (pendingRaw.get() == null) return
            if (needJpeg && pendingJpeg.get() == null) return
            if (pendingResult.get() == null) return
            if (!processed.compareAndSet(false, true)) return
            reader.setOnImageAvailableListener(null, null)
            jReader?.setOnImageAvailableListener(null, null)
            val rawImg = pendingRaw.getAndSet(null)!!
            val jpegImg = if (needJpeg) pendingJpeg.getAndSet(null)!! else null
            val result = pendingResult.getAndSet(null)!!
            ioExecutor.execute {
                var handle: CaptureStorage.Handle? = null
                try {
                    val orient =
                        RawCaptureSupport.orientationClockwiseDegForDng(chars, surfaceRotation)
                    val loc = locationForStillMetadata()
                    handle =
                        CaptureStorage.openOutput(
                            appContext.applicationContext,
                            profile,
                            profile.toDngCaptureKind(),
                            useLocationBridge = false,
                        )
                    Dng12Saver(chars, profile).save(
                        rawImg,
                        result,
                        handle.output,
                        orientationDegrees = orient,
                        location = loc,
                        softwareDescription = dngSoftwareDescription,
                        uniqueCameraModel = formatDngUniqueCameraModelLine(camId, stillsLut),
                    )
                    rawImg.close()
                    val uri = handle.uri.toString()
                    val dngDisplayName = handle.displayName
                    val dngUri = handle.uri
                    handle.close()
                    handle = null
                    StillCaptureMetadata.applyToDngUri(
                        appContext.applicationContext,
                        dngUri,
                        chars,
                        result,
                        location = loc,
                    )
                    writeCalibrationSidecarIfNeeded(appContext, profile, dngDisplayName)
                    if (jpegImg != null) {
                        try {
                            runCatching {
                                saveHardwareJpegCompanion(
                                    appContext,
                                    profile,
                                    jpegImg,
                                    stillsLut,
                                    chars,
                                    result,
                                )
                            }.onFailure { Log.w(tag, "companion JPEG pipeline failed", it) }
                        } finally {
                            jpegImg.close()
                        }
                    }
                    if (shotTag != null) {
                        Log.i(
                            "PNS.AdbValidation",
                            "captureRawStill $shotTag ok=true saved=$dngDisplayName",
                        )
                    }
                    mainHandler.post { onResult(Result.success(uri)) }
                } catch (t: Throwable) {
                    if (shotTag != null) {
                        Log.i(
                            "PNS.AdbValidation",
                            "captureRawStill $shotTag ok=false err=${t.message}",
                        )
                    }
                    runCatching { rawImg.close() }
                    runCatching { jpegImg?.close() }
                    runCatching { handle?.discard() }
                    mainHandler.post { onResult(Result.failure(t)) }
                } finally {
                    captureBusy.set(false)
                }
            }
        }

        reader.setOnImageAvailableListener({ r ->
            if (processed.get()) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireNextImage() }.getOrNull()
            if (img == null) return@setOnImageAvailableListener
            val prev = pendingRaw.getAndSet(img)
            runCatching { prev?.close() }
            maybeProcess()
        }, bgHandler)

        jReader?.setOnImageAvailableListener({ r ->
            if (processed.get()) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireNextImage() }.getOrNull()
            if (img == null) return@setOnImageAvailableListener
            val prev = pendingJpeg.getAndSet(img)
            runCatching { prev?.close() }
            maybeProcess()
        }, bgHandler)

        try {
            sess.capture(
                still,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        mainHandler.post { haptics.scheduleStillTick() }
                        pendingResult.set(result)
                        maybeProcess()
                        bgHandler.postDelayed({
                            if (!processed.get()) {
                                when {
                                    pendingRaw.get() == null ->
                                        fail(IllegalStateException("No RAW buffer"))
                                    needJpeg && pendingJpeg.get() == null ->
                                        fail(IllegalStateException("No JPEG buffer"))
                                }
                            }
                        }, 750L)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        fail(RuntimeException("capture failed reason=${failure.reason}"))
                    }
                },
                bgHandler,
            )
        } catch (t: Throwable) {
            fail(t)
        }
    }

    /**
     * Sequential RAW bracket → DNG files; filenames carry stop index + [BracketPlan.groupingId].
     * Uses [captureBusy] for the whole sequence (mutually exclusive with [captureRawStill]).
     */
    fun captureBracketBurst(
        appContext: Context,
        profile: ImagingProfile,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        pattern: BracketPattern,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        onResult: (Result<String>) -> Unit,
    ) {
        val finished = AtomicBoolean(false)
        fun finishFailure(t: Throwable) {
            if (!finished.compareAndSet(false, true)) return
            captureBusy.set(false)
            mainHandler.post { onResult(Result.failure(t)) }
        }
        fun finishSuccess(message: String) {
            if (!finished.compareAndSet(false, true)) return
            captureBusy.set(false)
            mainHandler.post { onResult(Result.success(message)) }
        }

        if (!captureBusy.compareAndSet(false, true)) {
            mainHandler.post {
                onResult(Result.failure(IllegalStateException("Capture already in progress")))
            }
            return
        }
        if (commandDialMode != CommandDialMode.BKT) {
            finishFailure(IllegalStateException("Set command dial to BKT"))
            return
        }

        val cam = device
        val sess = session
        val reader = rawImageReader
        val jReader = jpegImageReader
        val previewSurf = previewSurface
        val camId = selectedCameraId
        if (cam == null || sess == null || reader == null || previewSurf == null || camId.isNullOrBlank()) {
            finishFailure(IllegalStateException("Camera not ready or RAW unavailable (use preview ≤119 fps)"))
            return
        }
        val bgHandler = handler
        if (bgHandler == null) {
            finishFailure(IllegalStateException("No camera handler"))
            return
        }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        if (chars == null) {
            finishFailure(IllegalStateException("No characteristics"))
            return
        }
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        if (step == null || range == null) {
            finishFailure(IllegalStateException("AE compensation step/range unavailable"))
            return
        }

        val needJpeg = jReader != null
        val plan = BracketPlan.build(pattern)
        val aeInts = BracketScheduler.aeStepsFor(plan, step, range)
        val savedUris = mutableListOf<String>()

        fun scheduleShot(idx: Int) {
            if (idx >= aeInts.size) {
                reader.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                finishSuccess(savedUris.joinToString("\n"))
                return
            }
            val still =
                cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(previewSurf)
                    addTarget(reader.surface)
                    jReader?.let { addTarget(it.surface) }
                    applyScalerCropAndMetering(this, chars, camId, null)
                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeInts[idx])
                }.build()
            val pendingRaw = java.util.concurrent.atomic.AtomicReference<Image?>(null)
            val pendingJpeg = java.util.concurrent.atomic.AtomicReference<Image?>(null)
            val pendingResult = java.util.concurrent.atomic.AtomicReference<TotalCaptureResult?>(null)
            val processed = AtomicBoolean(false)

            fun shotFail(t: Throwable) {
                if (!processed.compareAndSet(false, true)) return
                reader.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                runCatching { pendingRaw.getAndSet(null)?.close() }
                runCatching { pendingJpeg.getAndSet(null)?.close() }
                pendingResult.set(null)
                finishFailure(t)
            }

            fun shotMaybeProcess() {
                if (pendingRaw.get() == null) return
                if (needJpeg && pendingJpeg.get() == null) return
                if (pendingResult.get() == null) return
                if (!processed.compareAndSet(false, true)) return
                reader.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                val rawImg = pendingRaw.getAndSet(null)!!
                val jpegImg = if (needJpeg) pendingJpeg.getAndSet(null)!! else null
                val result = pendingResult.getAndSet(null)!!
                ioExecutor.execute {
                    var handle: CaptureStorage.Handle? = null
                    try {
                        val orient =
                            RawCaptureSupport.orientationClockwiseDegForDng(chars, surfaceRotation)
                        val suffix = "bkt${idx + 1}of${aeInts.size}-${plan.groupingId}"
                        val loc = locationForStillMetadata()
                        handle =
                            CaptureStorage.openOutput(
                                appContext.applicationContext,
                                profile,
                                profile.toDngCaptureKind(),
                                useLocationBridge = false,
                                filenameSuffix = suffix,
                            )
                        Dng12Saver(chars, profile).save(
                            rawImg,
                            result,
                            handle.output,
                            orientationDegrees = orient,
                            location = loc,
                            softwareDescription = dngSoftwareDescription,
                            uniqueCameraModel = formatDngUniqueCameraModelLine(camId, stillsLut),
                        )
                        rawImg.close()
                        val uri = handle.uri.toString()
                        val dngDisplayName = handle.displayName
                        val dngUri = handle.uri
                        handle.close()
                        handle = null
                        StillCaptureMetadata.applyToDngUri(
                            appContext.applicationContext,
                            dngUri,
                            chars,
                            result,
                            location = loc,
                        )
                        writeCalibrationSidecarIfNeeded(appContext, profile, dngDisplayName)
                        if (jpegImg != null) {
                            try {
                                runCatching {
                                    saveHardwareJpegCompanion(
                                        appContext,
                                        profile,
                                        jpegImg,
                                        stillsLut,
                                        chars,
                                        result,
                                    )
                                }.onFailure { Log.w(tag, "bracket companion JPEG failed", it) }
                            } finally {
                                jpegImg.close()
                            }
                        }
                        synchronized(savedUris) { savedUris.add(uri) }
                    } catch (t: Throwable) {
                        runCatching { rawImg.close() }
                        runCatching { jpegImg?.close() }
                        runCatching { handle?.discard() }
                        finishFailure(t)
                        return@execute
                    }
                    bgHandler.post { scheduleShot(idx + 1) }
                }
            }

            reader.setOnImageAvailableListener({ r ->
                if (processed.get()) {
                    runCatching { r.acquireLatestImage()?.close() }
                    return@setOnImageAvailableListener
                }
                val img = runCatching { r.acquireNextImage() }.getOrNull()
                    ?: return@setOnImageAvailableListener
                val prev = pendingRaw.getAndSet(img)
                runCatching { prev?.close() }
                shotMaybeProcess()
            }, bgHandler)

            jReader?.setOnImageAvailableListener({ r ->
                if (processed.get()) {
                    runCatching { r.acquireLatestImage()?.close() }
                    return@setOnImageAvailableListener
                }
                val img = runCatching { r.acquireNextImage() }.getOrNull()
                    ?: return@setOnImageAvailableListener
                val prev = pendingJpeg.getAndSet(img)
                runCatching { prev?.close() }
                shotMaybeProcess()
            }, bgHandler)

            try {
                sess.capture(
                    still,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult,
                        ) {
                            mainHandler.post { haptics.scheduleStillTick() }
                            pendingResult.set(result)
                            shotMaybeProcess()
                            bgHandler.postDelayed({
                                if (!processed.get()) {
                                    when {
                                        pendingRaw.get() == null ->
                                            shotFail(
                                                IllegalStateException(
                                                    "No RAW buffer at bracket stop ${idx + 1}",
                                                ),
                                            )
                                        needJpeg && pendingJpeg.get() == null ->
                                            shotFail(
                                                IllegalStateException(
                                                    "No JPEG buffer at bracket stop ${idx + 1}",
                                                ),
                                            )
                                    }
                                }
                            }, 750L)
                        }

                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure,
                        ) {
                            shotFail(RuntimeException("capture failed reason=${failure.reason}"))
                        }
                    },
                    bgHandler,
                )
            } catch (t: Throwable) {
                shotFail(t)
            }
        }

        scheduleShot(0)
    }

    fun status(): String = lastStatus

    fun measuredFps(): Double {
        // Prefer sensor timestamps when present. Otherwise prefer rendered-frame rate from TextureView.
        // If only a few frames arrive (or 1 frame), smoothedFrameFps may be 0; use a windowed estimate too.
        if (framesWithTimestamp > 0L) return smoothedFps

        val now = SystemClock.elapsedRealtimeNanos()
        val winStart = textureWindowStartNs
        val winFrames = textureWindowFrames
        val windowFps =
            if (winStart > 0L && winFrames > 1L) {
                val dt = now - winStart
                if (dt > 0L) (winFrames.toDouble() * 1e9) / dt.toDouble() else 0.0
            } else {
                0.0
            }

        return when {
            smoothedFrameFps > 0.0 -> smoothedFrameFps
            windowFps > 0.0 -> windowFps
            else -> smoothedWallFps
        }
    }

    fun previewMeterIso(): Int? = lastPreviewIso

    fun previewMeterExposureNs(): Long? = lastPreviewExposureNs

    fun previewMeterAwbMode(): Int? = lastPreviewAwbMode

    /** True when preview session includes a JPEG companion surface (RAW+ pipeline). */
    fun previewUsesJpegCompanion(): Boolean = jpegImageReader != null

    private var preferredJpegCompanion: Boolean = true

    /** Mirrors [PreviewChromePreferences.stillCaptureJpegCompanion]; triggers session rebuild. */
    fun setPreferredJpegCompanion(want: Boolean) {
        if (preferredJpegCompanion == want) return
        preferredJpegCompanion = want
        Log.d(tag, "setPreferredJpegCompanion want=$want")
        maybeRestart()
    }

    /** After returning from another activity (e.g. gallery viewer), rebuild the capture session. */
    fun kickPreviewPipelineRestart() {
        maybeRestart()
    }

    /**
     * When a calibration JSON exists on disk (newest profile wins), writes a gallery-visible
     * `.pns-calibration.json` next to the DNG via MediaStore (API 29+). Desktop tools can merge
     * [DngColorTags] into TIFF/DNG without relying on hidden `DngCreator` tag APIs.
     */
    private fun writeCalibrationSidecarIfNeeded(
        appContext: Context,
        profile: ImagingProfile,
        dngDisplayName: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val latest = CalibrationProfileStorage.list(appContext).firstOrNull() ?: return
        val calProfile =
            runCatching { CalibrationProfileStorage.load(latest) }.getOrNull() ?: return
        val dngColor = DngColorTags.forProfile(calProfile)
        val json =
            DngCalibrationSidecar.encode(calProfile, dngColor, latest.absolutePath)
        var sidecar: CaptureStorage.JsonSidecarHandle? = null
        try {
            sidecar =
                CaptureStorage.openCalibrationSidecarOutput(
                    appContext.applicationContext,
                    profile,
                    dngDisplayName,
                )
            sidecar.output.write(json.toByteArray(Charsets.UTF_8))
            sidecar.close()
            sidecar = null
            Log.d(tag, "calibration sidecar ok for $dngDisplayName")
        } catch (t: Throwable) {
            Log.w(tag, "calibration sidecar write failed", t)
            runCatching { sidecar?.discard() }
        }
    }

    fun surfaceDebug(): String {
        val cur = currentSurfaceSize
        val want = desiredSurfaceSize
        val hs = desiredHighSpeedSize
        val mode =
            when {
                framesWithTimestamp > 0L -> "sensorTs"
                framesFromTexture > 0L -> "texture"
                else -> "wall"
            }
        return "surface cur=${cur?.width}x${cur?.height} want=${want?.width}x${want?.height} hs=${hs?.width}x${hs?.height} ts=${framesWithTimestamp}/${framesWithTimestamp + framesMissingTimestamp} tex=${framesFromTexture} texWin=${textureWindowFrames} fpsMode=$mode"
    }

    fun applyPreviewTextureTransform(
        textureView: TextureView,
        viewWidthPx: Int,
        viewHeightPx: Int,
        uiTwistDegrees: Float = 0f,
        coverCrop: Boolean,
    ) {
        // Must match [previewBufferSize]: always prefer negotiated stream dimensions. Never infer
        // buffer WxH from the TextureView widget — layout size can differ from camera pixels.
        val buf = desiredSurfaceSize ?: currentSurfaceSize
        if (buf == null || buf.width <= 0 || buf.height <= 0) {
            textureView.setTransform(android.graphics.Matrix())
            return
        }
        val key = "${viewWidthPx}x${viewHeightPx}/${buf.width}x${buf.height}/${uiTwistDegrees}/crop=$coverCrop"
        if (key != lastTransformLogKey) {
            lastTransformLogKey = key
            Log.d(
                tag,
                "applyPreviewTextureTransform view=${viewWidthPx}x${viewHeightPx} buffer=${buf.width}x${buf.height} twist=${uiTwistDegrees} coverCrop=$coverCrop",
            )
        }
        TexturePreviewFit.applyCenterFitWithUiTwist(
            textureView,
            viewWidthPx,
            viewHeightPx,
            buf.width,
            buf.height,
            uiTwistDegrees,
            coverCrop,
        )
    }

    private fun closeCamera() {
        generation++
        faceTracker.reset()
        lastTrackerLockedLogged = null
        tapMeteringRect = null
        loggedFaceDetectCaps = false
        loggedFaceStatisticsSample = false
        loggedAdbTrackerPipelineReady = false
        publishEyeMarks(emptyList())
        loggedSuperMacroProbeWrongCam = false
        loggedSuperMacroProbeUw = false
        superMacroSessionConfigured = false
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { rawImageReader?.close() }
        rawImageReader = null
        runCatching { jpegImageReader?.close() }
        jpegImageReader = null
        runCatching { yuvImageReader?.close() }
        yuvImageReader = null
        lastAppliedHighlightComp = null
        lastHighlightProcessWallMs = 0L
        lastHighlightMeterAdbLogMs = 0L
        session = null
        device = null

        lastTimestampNs = 0L
        smoothedFps = 0.0
        framesWithTimestamp = 0L
        framesMissingTimestamp = 0L
        lastWallNs = 0L
        smoothedWallFps = 0.0
        lastFrameNs = 0L
        smoothedFrameFps = 0.0
        framesFromTexture = 0L
        textureWindowStartNs = 0L
        textureWindowFrames = 0L
        lastPreviewIso = null
        lastPreviewExposureNs = null
        lastPreviewAwbMode = null
        loggedChromeUxReadout = false
        readoutFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        readoutFallbackRunnable = null
    }

    fun stop() {
        val h = handler
        val t = thread
        if (h != null && t != null && t.isAlive) {
            val latch = CountDownLatch(1)
            h.post {
                closeCamera()
                latch.countDown()
            }
            latch.await(5L, TimeUnit.SECONDS)
        } else {
            closeCamera()
        }

        handler = null
        thread?.quitSafely()
        thread = null

        if (!meterExecutor.isShutdown) {
            meterExecutor.shutdown()
            runCatching { meterExecutor.awaitTermination(5L, TimeUnit.SECONDS) }
        }

        lastStatus = "Stopped"
    }

    private fun ensureThread() {
        if (thread != null && handler != null) return
        val t = HandlerThread("PNS.Cam")
        t.start()
        thread = t
        handler = Handler(t.looper)
    }

    /**
     * UI / TextureView callbacks run on the main thread; camera work runs on [handler].
     * Restart must be serialized with [createSession] so we never [closeCamera] while a session
     * is being built (RAW/YUV ImageReader surfaces abandoned mid-flight).
     */
    private fun maybeRestart() {
        ensureThread()
        val h = handler ?: return
        if (Looper.myLooper() == h.looper) {
            maybeRestartBody()
        } else {
            h.post { maybeRestartBody() }
        }
    }

    private fun maybeRestartBody() {
        val camId = selectedCameraId
        val surf = previewSurface
        if (camId.isNullOrBlank() || surf == null) {
            lastStatus = "Waiting (cameraId=${camId ?: "null"}, surface=${if (surf == null) "null" else "ok"})"
            return
        }

        ModeTransitionLog.previewPipelineRestart(
            cameraId = camId,
            fps = desiredFps,
            focalCrop = focalCropMode?.name,
            commandDial = commandDialMode.name,
        )

        Log.d(tag, "maybeRestart cameraId=$camId fps=$desiredFps cur=${currentSurfaceSize?.width}x${currentSurfaceSize?.height}")

        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val activeArray = chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        desiredHighSpeedSize = pickHighSpeedTarget(
            map = map,
            desiredFps = desiredFps,
        )?.first

        val emergencyPreview =
            map?.getOutputSizes(SurfaceTexture::class.java)
                ?.maxByOrNull { it.width.toLong() * it.height.toLong() }

        // Pick the surface size we want to drive for this mode.
        val wantedSurfaceSize =
            if (desiredFps >= 120) {
                // Constrained high-speed requires one of these exact sizes.
                desiredHighSpeedSize
            } else {
                pickNormalPreviewSize(map, activeArray, chars) ?: emergencyPreview
            }
        desiredSurfaceSize = wantedSurfaceSize

        // Tear down the camera session before replacing the TextureView Surface. Releasing the old
        // Surface while createCaptureSession is still configuring causes IllegalArgumentException:
        // "Surface was abandoned" (OutputConfiguration).
        closeCamera()
        // Ensure the SurfaceTexture buffer size matches what Camera2 expects.
        // Some devices never report this back via callbacks, so we treat this as authoritative.
        if (wantedSurfaceSize != null) {
            runCatching { previewSurfaceTexture?.setDefaultBufferSize(wantedSurfaceSize.width, wantedSurfaceSize.height) }
            currentSurfaceSize = wantedSurfaceSize
            rebuildSurfaceIfPossible()
            Log.d(tag, "setDefaultBufferSize ${wantedSurfaceSize.width}x${wantedSurfaceSize.height}")
        }

        val latestSurface = previewSurface
        if (latestSurface == null) {
            lastStatus = "Waiting (surface=null)"
            return
        }
        Log.d(tag, "openAndStart (after close) cameraId=$camId fps=$desiredFps")
        openAndStart(camId)
    }

    private fun rebuildSurfaceIfPossible() {
        val st = previewSurfaceTexture ?: return
        runCatching { previewSurface?.release() }
        previewSurface = Surface(st)
    }

    // (TextureView drives frame counting via onSurfaceTextureUpdated.)

    private fun openAndStart(camId: String) {
        val h = handler ?: return
        lastStatus = "Opening cameraId=$camId"
        Log.d(tag, "openCamera cameraId=$camId fps=$desiredFps")
        val gen = generation
        try {
            cm.openCamera(
                camId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (gen != generation) {
                            Log.w(tag, "onOpened ignored (stale gen=$gen current=$generation)")
                            runCatching { camera.close() }
                            return
                        }
                        device = camera
                        lastStatus = "Opened cameraId=$camId; creating session (fps=$desiredFps)"
                        Log.d(tag, "onOpened cameraId=$camId; creating session")
                        createSession(camera, camId)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        lastStatus = "Disconnected cameraId=$camId"
                        Log.w(tag, "onDisconnected cameraId=$camId")
                        runCatching { camera.close() }
                        device = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        lastStatus = "Camera error cameraId=$camId error=$error"
                        Log.e(tag, "onError cameraId=$camId error=$error")
                        runCatching { camera.close() }
                        device = null
                    }
                },
                h,
            )
        } catch (e: SecurityException) {
            lastStatus = "Missing CAMERA permission"
        } catch (e: CameraAccessException) {
            lastStatus = "CameraAccessException: ${e.reason}"
        } catch (t: Throwable) {
            lastStatus = "Open failed: ${t::class.java.simpleName}"
        }
    }

    /**
     * Uses API 33 [SessionConfiguration.setSessionParameters] for `com.oplus.macro.closeup.enable`.
     *
     * Do **not** gate on [CameraCharacteristics.getAvailableSessionKeys] here: on some OEM stacks
     * (including OnePlus 13 / Android 16 in Sprint 5.3 validation) `availableSessionKeys` is still
     * empty at the first `createCaptureSession` after `CameraDevice.StateCallback.onOpened`, but the
     * same characteristics query later — when building the repeating request — already lists the
     * macro key under session + request enums. We attempt the session-parameters path whenever the
     * probe targets ultra-wide; [VendorKeyGuard] decides whether a setter sticks.
     */
    private fun shouldUseMacroSessionParameters(camId: String): Boolean {
        if (!superMacroAdbProbe) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val uw =
            runCatching {
                BackCameraRoleResolver.resolve(cm, cameraIds()).ultraWide
            }.getOrNull() ?: return false
        return uw == camId
    }

    private fun createSession(camera: CameraDevice, camId: String) {
        val h = handler ?: return
        val surf =
            previewSurface ?: run {
                Log.w(tag, "createSession aborted: previewSurface=null")
                runCatching { camera.close() }
                device = null
                return
            }
        // The TextureView's underlying SurfaceTexture can be abandoned between the moment
        // the camera open completes and the moment we hand its Surface to OutputConfiguration
        // (rotation / activity teardown is the common trigger). Validate up-front so we don't
        // hand an abandoned Surface to the framework — that throws IllegalArgumentException
        // from a background thread and crashes the process.
        if (!surf.isValid) {
            Log.w(tag, "createSession aborted: previewSurface no longer valid (abandoned)")
            lastStatus = "Surface abandoned during session create — waiting for new texture"
            runCatching { camera.close() }
            device = null
            return
        }
        val gen = generation
        val map = runCatching { cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) }.getOrNull()
        val target = pickHighSpeedTarget(map, desiredFps)

        val useHighSpeed = target != null && desiredFps >= 120
        Log.d(tag, "createSession camId=$camId desiredFps=$desiredFps useHighSpeed=$useHighSpeed target=${target?.first?.width}x${target?.first?.height} ${target?.second}")

        runCatching { rawImageReader?.close() }
        rawImageReader = null
        runCatching { jpegImageReader?.close() }
        jpegImageReader = null
        runCatching { yuvImageReader?.close() }
        yuvImageReader = null

        val surfaces = mutableListOf(surf)
        if (!useHighSpeed) {
            val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            val mapForStreams =
                chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawPick = chars?.let { RawCaptureSupport.pickRawOutput(it) }
            if (rawPick != null) {
                val (fmt, size) = rawPick
                // No OnImageAvailableListener here on purpose. The RAW reader is targeted
                // ONLY by the explicit still-capture request (the preview repeating request
                // does not target it), so the queue can never grow unbounded. The previous
                // auto-drain listener was racing the still-capture path: when a RAW image
                // arrived for a real capture the listener would acquire+close it before
                // [captureRawStill]'s onCaptureCompleted got a chance, and the user saw
                // "No RAW buffer". Leaving the queue undrained lets onCaptureCompleted
                // acquire the image deterministically.
                rawImageReader = ImageReader.newInstance(size.width, size.height, fmt, 2)
                surfaces.add(rawImageReader!!.surface)
                Log.d(tag, "RAW ImageReader ${size.width}x${size.height} format=$fmt")

                val jpegSize = mapForStreams?.let { RawCaptureSupport.pickLargestJpegSize(it) }
                if (preferredJpegCompanion && jpegSize != null) {
                    jpegImageReader =
                        ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
                    surfaces.add(jpegImageReader!!.surface)
                    Log.d(tag, "JPEG ImageReader ${jpegSize.width}x${jpegSize.height}")
                } else {
                    if (!preferredJpegCompanion) {
                        Log.d(tag, "JPEG companion disabled by preference — RAW-only still capture")
                    } else {
                        Log.w(tag, "No JPEG output sizes — RAW-only still capture")
                    }
                }
            }

            val wantHighlight =
                commandDialMode == CommandDialMode.H &&
                    desiredFps < 120
            if (wantHighlight) {
                val yuvSize = HighlightMeterSupport.pickYuv420AnalysisSize(map)
                if (yuvSize != null) {
                    yuvImageReader =
                        ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 2).also { ir ->
                            ir.setOnImageAvailableListener({ reader -> processYuvForHighlight(reader) }, h)
                        }
                    surfaces.add(yuvImageReader!!.surface)
                    Log.d(tag, "YUV highlight ImageReader ${yuvSize.width}x${yuvSize.height}")
                } else {
                    Log.w(tag, "Highlight metering: no YUV_420_888 size")
                }
            }
        }

        if (!useHighSpeed) {
            if (shouldUseMacroSessionParameters(camId)) {
                val sessionChars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                if (sessionChars != null) {
                    val sessionReqBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val macroName = HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST
                    val sessionApplied =
                        VendorKeyGuard.trySetVendorSessionEnable(
                            sessionReqBuilder,
                            sessionChars,
                            macroName,
                        )
                            ?: VendorKeyGuard.trySetVendorRequestEnable(
                                sessionReqBuilder,
                                sessionChars,
                                macroName,
                            )
                    if (sessionApplied == null) {
                        Log.i(
                            "PNS.AdbValidation",
                            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=false type=none path=sessionParameters",
                        )
                    }
                    if (sessionApplied != null) {
                        superMacroSessionConfigured = true
                        val macroKind = sessionApplied
                        val sessionParams = sessionReqBuilder.build()
                        val outputConfigs = surfaces.map { OutputConfiguration(it) }
                        val executor: Executor = Executor { cmd -> h.post(cmd) }
                        val sessionConfig =
                            SessionConfiguration(
                                SessionConfiguration.SESSION_REGULAR,
                                outputConfigs,
                                executor,
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(sess: CameraCaptureSession) {
                                        if (gen != generation || device == null) {
                                            Log.w(tag, "onConfigured ignored (stale gen=$gen current=$generation)")
                                            runCatching { sess.close() }
                                            return
                                        }
                                        Log.i(
                                            "PNS.AdbValidation",
                                            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=true type=$macroKind path=sessionParameters",
                                        )
                                        session = sess
                                        val fpsRange = pickNormalFpsRange(camId, desiredFps)
                                        startRepeating(sess, camera, surf, fpsRange = fpsRange, camId = camId)
                                    }

                                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                                        Log.i(
                                            "PNS.AdbValidation",
                                            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=false type=$macroKind path=sessionParametersConfigureFailed",
                                        )
                                        lastStatus = "Session configure failed (macro sessionParameters)"
                                        superMacroSessionConfigured = false
                                    }
                                },
                            )
                        sessionConfig.setSessionParameters(sessionParams)
                        val macroCreate = runCatching { camera.createCaptureSession(sessionConfig) }
                        macroCreate.exceptionOrNull()?.let { e ->
                            Log.w(
                                tag,
                                "createCaptureSession(SessionConfiguration macro) threw ${e::class.java.simpleName}: ${e.message}",
                            )
                            superMacroSessionConfigured = false
                            Log.i(
                                "PNS.AdbValidation",
                                "superMacroCloseup probe cameraId=$camId vendorKeyApplied=false type=$macroKind path=sessionParametersCreateThrows",
                            )
                        } ?: run {
                            return
                        }
                    }
                }
            }
            // Even with the up-front isValid() check above, the SurfaceTexture can still be
            // torn down between the check and OutputConfiguration's internal getSurfaceSize()
            // call (TOCTOU on rotation). Wrapping in runCatching keeps that race recoverable
            // — maybeRestart() will be re-driven once the new TextureView surface is ready.
            val createResult = runCatching {
                camera.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(sess: CameraCaptureSession) {
                            if (gen != generation || device == null) {
                                Log.w(tag, "onConfigured ignored (stale gen=$gen current=$generation)")
                                runCatching { sess.close() }
                                return
                            }
                            session = sess
                            val fpsRange = pickNormalFpsRange(camId, desiredFps)
                            startRepeating(sess, camera, surf, fpsRange = fpsRange, camId = camId)
                        }

                        override fun onConfigureFailed(sess: CameraCaptureSession) {
                            lastStatus = "Session configure failed (normal)"
                        }
                    },
                    h,
                )
            }
            createResult.exceptionOrNull()?.let { e ->
                Log.w(tag, "createCaptureSession threw ${e::class.java.simpleName}: ${e.message}")
                lastStatus = "Session create aborted: ${e::class.java.simpleName}"
                runCatching { camera.close() }
                device = null
                runCatching { rawImageReader?.close() }
                rawImageReader = null
                runCatching { jpegImageReader?.close() }
                jpegImageReader = null
                runCatching { yuvImageReader?.close() }
                yuvImageReader = null
            }
            return
        }

        Log.d(tag, "Creating HFR session fps=$desiredFps size=${target.first.width}x${target.first.height} range=${target.second}")
        // Constrained high-speed session — same TOCTOU protection as the normal path.
        val hfrResult = runCatching {
            camera.createConstrainedHighSpeedCaptureSession(
                listOf(surf),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        if (gen != generation || device == null) {
                            Log.w(tag, "HFR onConfigured ignored (stale gen=$gen current=$generation)")
                            runCatching { sess.close() }
                            return
                        }
                        session = sess
                        val fpsRange = target!!.second
                        startRepeating(sess, camera, surf, fpsRange = fpsRange, camId = camId)
                    }

                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                        lastStatus = "High-speed session configure failed"
                    }
                },
                h,
            )
        }
        hfrResult.exceptionOrNull()?.let { e ->
            Log.w(tag, "createConstrainedHighSpeedCaptureSession threw ${e::class.java.simpleName}: ${e.message}")
            lastStatus = "HFR session create aborted: ${e::class.java.simpleName}"
            runCatching { camera.close() }
            device = null
        }
    }

    private fun wantsHighlightMetering(): Boolean =
        commandDialMode == CommandDialMode.H &&
            desiredFps < 120

    /**
     * AE compensation index for [CONTROL_AE_EXPOSURE_COMPENSATION] when YUV analysis is active;
     * null = leave AE compensation unset (normal AE).
     */
    private fun aeHighlightCompensationValue(): Int? {
        if (!wantsHighlightMetering() || yuvImageReader == null) return null
        return lastAppliedHighlightComp ?: 0
    }

    private fun buildPreviewCaptureRequest(
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
        camId: String,
    ): CaptureRequest {
        val template =
            if (fpsRange != null && fpsRange.lower >= 120) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        return camera.createCaptureRequest(template).apply {
            addTarget(surf)
            yuvImageReader?.let { addTarget(it.surface) }
            if (fpsRange != null) {
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }
            if (chars != null) {
                val manualSensor = manualIsoOverride != null || manualExposureNsOverride != null
                applyScalerCropAndMetering(
                    this,
                    chars,
                    camId,
                    if (manualSensor) null else aeHighlightCompensationValue(),
                )
                applyFaceDetectMode(this, chars)
                applySuperMacroVendorProbe(this, chars, camId)
                applyReadoutManualExposureAndWb(this, chars)
            }
        }.build()
    }

    private fun applyReadoutManualExposureAndWb(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val wantsManualSensor = manualIsoOverride != null || manualExposureNsOverride != null
        if (wantsManualSensor) {
            if (!aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)) {
                Log.w(tag, "Readout manual ISO/shutter unavailable: no CONTROL_AE_MODE_OFF")
                return
            }
            req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            val isoPick = manualIsoOverride ?: lastPreviewIso ?: isoRange?.lower ?: 100
            val isoClamped = ReadoutExposureCatalog.clampIso(isoRange, isoPick)
            req.set(CaptureRequest.SENSOR_SENSITIVITY, isoClamped)
            val expPick =
                manualExposureNsOverride
                    ?: lastPreviewExposureNs
                    ?: expRange?.lower
                    ?: 33_333_333L
            val expClamped = ReadoutExposureCatalog.clampExposure(expRange, expPick)
            req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expClamped)
        }
        manualAwbModeOverride?.let { mode ->
            val awbAvail = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
            if (awbAvail.contains(mode)) {
                req.set(CaptureRequest.CONTROL_AWB_MODE, mode)
            }
        }
    }

    /**
     * Sprint 5.3: when ADB passes [superMacroAdbProbe] and preview targets ultra-wide, set OPLUS
     * close-up enable on the repeating request if the key is advertised — proof for §5 / Super Macro gate.
     */
    private fun applySuperMacroVendorProbe(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
    ) {
        if (!superMacroAdbProbe) return
        val uw =
            runCatching {
                BackCameraRoleResolver.resolve(cm, cameraIds()).ultraWide
            }.getOrNull()
        if (uw != camId) {
            if (!loggedSuperMacroProbeWrongCam) {
                loggedSuperMacroProbeWrongCam = true
                Log.i(
                    "PNS.AdbValidation",
                    "superMacroCloseup skipped cameraId=$camId (ultraWide=$uw); use pns_preview_camera_id on UW",
                )
            }
            return
        }
        if (superMacroSessionConfigured) return
        if (loggedSuperMacroProbeUw) return
        loggedSuperMacroProbeUw = true
        val macroName = HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST
        val lookup = VendorKeyGuard.captureRequestKey(chars, macroName)
        val reqAvail = VendorKeyGuard.isRequestKeyAvailable(chars, macroName)
        val sessAvail = VendorKeyGuard.isSessionKeyAvailable(chars, macroName)
        Log.i(
            "PNS.AdbValidation",
            "superMacroCloseup keyLookup requestKeyObject=${lookup != null} requestEnum=$reqAvail sessionEnum=$sessAvail",
        )
        val appliedKind =
            VendorKeyGuard.trySetVendorRequestEnable(
                req,
                chars,
                macroName,
            )
        Log.i(
            "PNS.AdbValidation",
            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=${appliedKind != null} type=${appliedKind ?: "none"}",
        )
    }

    private fun publishEyeMarks(marks: List<EyeMark>) {
        mainHandler.post { eyeMarksListener?.invoke(marks) }
    }

    private fun pickFaceDetectMode(chars: CameraCharacteristics): Int {
        val modes =
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                ?: intArrayOf(CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
        return when {
            modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
            modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
            else -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        }
    }

    private fun applyFaceDetectMode(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val avail =
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)?.joinToString()
        val mode =
            if (automationSuppressFacePipeline || !hudFaceOverlayEnabled) {
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
            } else {
                pickFaceDetectMode(chars)
            }
        if (hudFaceOverlayEnabled && !automationSuppressFacePipeline && !loggedFaceDetectCaps) {
            loggedFaceDetectCaps = true
            Log.i(
                "PNS.AdbValidation",
                "eyeAf faceDetectMode=$mode availableModes=[$avail]",
            )
        }
        req.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
    }

    private fun processFaceStatistics(result: TotalCaptureResult) {
        if (automationSuppressFacePipeline || !hudFaceOverlayEnabled) return
        if (!loggedAdbTrackerPipelineReady) {
            loggedAdbTrackerPipelineReady = true
            Log.i(
                "PNS.AdbValidation",
                "tracker statisticsPipeline active (metadata wired to TrackerState)",
            )
        }
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        if (useDigitalCrop) {
            faceTracker.reset()
            lastTrackerLockedLogged = null
            publishEyeMarks(emptyList())
            return
        }
        val faces = result.get(CaptureResult.STATISTICS_FACES)
        if (faces.isNullOrEmpty()) {
            faceTracker.update(emptySet())
            publishEyeMarks(emptyList())
            return
        }
        val observed = FaceTrackingSupport.observedIds(faces)
        val snap = faceTracker.update(observed)
        if (!loggedFaceStatisticsSample) {
            loggedFaceStatisticsSample = true
            Log.i(
                "PNS.AdbValidation",
                "eyeAf statisticsSample faces=${faces.size} lockedTrackIds=${snap.locked.joinToString()}",
            )
        }
        if (snap.locked != lastTrackerLockedLogged) {
            lastTrackerLockedLogged = snap.locked.toSet()
            Log.i(
                "PNS.AdbValidation",
                "tracker lockedIds=${snap.locked.joinToString()} transientCount=${snap.transient.size}",
            )
        }
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
        val mirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        val bufW = currentSurfaceSize?.width ?: return
        val bufH = currentSurfaceSize?.height ?: return
        val aw = active.width()
        val ah = active.height()
        val marks = ArrayList<EyeMark>(faces.size * 2)
        for (face in faces) {
            val faceId = FaceTrackingSupport.stableFaceId(face)
            val locked = faceId in snap.locked
            val sc = face.score.coerceIn(1, 100) / 100f
            val left: Point? = face.leftEyePosition
            val right: Point? = face.rightEyePosition
            if (left != null) {
                marks.add(
                    FaceDetectAdapter.mapEyeToPreview(
                        eyeSensor = SensorPoint(left.x, left.y),
                        activeArrayWidth = aw,
                        activeArrayHeight = ah,
                        previewWidth = bufW,
                        previewHeight = bufH,
                        sensorOrientationDeg = sensorOrientation,
                        mirrorHorizontally = mirror,
                        confidence = sc,
                    ).copy(trackingLocked = locked),
                )
            }
            if (right != null) {
                marks.add(
                    FaceDetectAdapter.mapEyeToPreview(
                        eyeSensor = SensorPoint(right.x, right.y),
                        activeArrayWidth = aw,
                        activeArrayHeight = ah,
                        previewWidth = bufW,
                        previewHeight = bufH,
                        sensorOrientationDeg = sensorOrientation,
                        mirrorHorizontally = mirror,
                        confidence = sc,
                    ).copy(trackingLocked = locked),
                )
            }
            if (left == null && right == null) {
                val b = face.bounds
                marks.add(
                    FaceDetectAdapter.mapFaceCenterToPreview(
                        faceLeft = b.left,
                        faceTop = b.top,
                        faceRight = b.right,
                        faceBottom = b.bottom,
                        activeArrayWidth = aw,
                        activeArrayHeight = ah,
                        previewWidth = bufW,
                        previewHeight = bufH,
                        sensorOrientationDeg = sensorOrientation,
                        mirrorHorizontally = mirror,
                        confidence = sc * 0.5f,
                    ).copy(trackingLocked = locked),
                )
            }
        }
        publishEyeMarks(marks)
    }

    /**
     * Maps a tap in [TextureView] pixel space to AE/AF metering regions and reapplies the
     * repeating request. Preview transform uses `uiTwistDegrees = 0` in [applyPreviewTextureTransform];
     * [uiTwistDegrees] is accepted for API symmetry with overlay chrome rotation only.
     */
    fun applyTapFocusFromView(
        viewX: Float,
        viewY: Float,
        viewW: Int,
        viewH: Int,
        @Suppress("UNUSED_PARAMETER") uiTwistDegrees: Float,
    ) {
        val buf = desiredSurfaceSize ?: return
        if (viewW <= 0 || viewH <= 0 || buf.width <= 0 || buf.height <= 0) return
        val (bx, by) =
            TexturePreviewFit.mapViewToBuffer(
                viewX,
                viewY,
                viewW,
                viewH,
                buf.width,
                buf.height,
                coverCrop = previewTextureCoverCrop,
            )
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val cropRect = SensorCropGeometry.scalerCropRect(chars, camId, modeForCrop)
        if (cropRect.width() <= 0 || cropRect.height() <= 0) return

        val nx = (bx / buf.width.toFloat()).coerceIn(0f, 1f)
        val ny = (by / buf.height.toFloat()).coerceIn(0f, 1f)
        val mx = cropRect.left + nx * cropRect.width()
        val my = cropRect.top + ny * cropRect.height()

        val span =
            (kotlin.math.min(cropRect.width(), cropRect.height()) * 0.12f)
                .toInt()
                .coerceIn(32, 512)
        val half = span / 2
        val cx = mx.toInt()
        val cy = my.toInt()
        var left = cx - half
        var top = cy - half
        val w = span
        val h = span
        if (left < active.left) left = active.left
        if (top < active.top) top = active.top
        if (left + w > active.right) left = (active.right - w).coerceAtLeast(active.left)
        if (top + h > active.bottom) top = (active.bottom - h).coerceAtLeast(active.top)
        if (w < 1 || h < 1 || left + w > active.right || top + h > active.bottom) return

        tapMeteringRect =
            MeteringRectangle(left, top, w, h, MeteringRectangle.METERING_WEIGHT_MAX)
        Log.d(tag, "tap focus buffer=(${bx.toInt()},${by.toInt()}) metering=$left,$top ${w}x$h")
        refreshRepeatingPreviewOnly()
    }

    /** Rebuild the repeating request without tearing down the session (face overlay / AE comp). */
    private fun refreshRepeatingPreviewOnly() {
        val sess = session ?: return
        val cam = device ?: return
        val surf = previewSurface ?: return
        val camId = selectedCameraId ?: return
        val fpsRange = pickNormalFpsRange(camId, desiredFps)
        try {
            val constrained =
                runCatching {
                    sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true)
                }.getOrDefault(false)
            if (constrained) return
            val req = buildPreviewCaptureRequest(cam, surf, fpsRange, camId)
            sess.setRepeatingRequest(req, fpsMeasuringCallback(), handler)
        } catch (e: CameraAccessException) {
            Log.w(tag, "refreshRepeatingPreviewOnly failed: ${e.reason}")
        } catch (t: Throwable) {
            Log.w(tag, "refreshRepeatingPreviewOnly failed: ${t.message}")
        }
    }

    private fun processYuvForHighlight(reader: ImageReader) {
        if (!wantsHighlightMetering() || yuvImageReader == null) {
            reader.acquireLatestImage()?.close()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastHighlightProcessWallMs < highlightMeterMinIntervalMs) {
            reader.acquireLatestImage()?.close()
            return
        }
        lastHighlightProcessWallMs = now
        val image = reader.acquireLatestImage() ?: return
        val bytes: ByteArray
        val w: Int
        val h: Int
        val rowStride: Int
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            w = image.width
            h = image.height
            rowStride = plane.rowStride
        } finally {
            image.close()
        }
        meterExecutor.execute {
            val hist = PreviewLumaHistogram.reduceYuv420Y(bytes, w, h, rowStride)
            val ev = HighlightMeter.suggestEvCorrection(hist)
            val camId = selectedCameraId ?: return@execute
            val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                ?: return@execute
            val comp = HighlightMeterSupport.evToCompensationIndex(ev, chars) ?: return@execute
            val camHandler = handler ?: return@execute
            camHandler.post {
                if (!wantsHighlightMetering() || yuvImageReader == null) return@post
                if (comp == lastAppliedHighlightComp) return@post
                lastAppliedHighlightComp = comp
                Log.d(tag, "HighlightMeter ev=${"%.2f".format(ev)} aeComp=$comp")
                val adbWall = SystemClock.elapsedRealtime()
                if (adbWall - lastHighlightMeterAdbLogMs >= 3500L) {
                    lastHighlightMeterAdbLogMs = adbWall
                    Log.i(
                        "PNS.AdbValidation",
                        "highlightMeter ev=${"%.2f".format(ev)} aeComp=$comp dial=H",
                    )
                }
                refreshRepeatingPreviewOnly()
            }
        }
    }

    private fun applyScalerCropAndMetering(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
        aeHighlightComp: Int? = null,
    ) {
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val cropRect = SensorCropGeometry.scalerCropRect(chars, camId, modeForCrop)
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            req.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            Log.d(
                tag,
                "SCALER_CROP_REGION=${cropRect.left},${cropRect.top}-${cropRect.right},${cropRect.bottom} mode=${modeForCrop?.name ?: "full"}",
            )
        }
        val tap = tapMeteringRect
        when {
            tap != null -> {
                req.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(tap))
                req.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(tap))
                val afModes =
                    chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val afMode =
                    when {
                        afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ->
                            CaptureRequest.CONTROL_AF_MODE_AUTO
                        else -> CaptureRequest.CONTROL_AF_MODE_OFF
                    }
                req.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            }
            modeForCrop == FocalMode.Standard50 -> {
                val active =
                    chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                if (active != null) {
                    val aeRect = centerWeightedMeteringRect(active, cropRect)
                    req.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(aeRect))
                }
            }
        }
        if (tap == null && commandDialMode == CommandDialMode.S) {
            applyStreetSnapAf(req, chars)
        }
        if (tap == null && commandDialMode == CommandDialMode.Auto) {
            applyAutoProgramAfAe(req, chars)
        }
        if (aeHighlightComp != null) {
            req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeHighlightComp)
        }
    }

    /**
     * Ricoh GR-style street snap: prefer manual infinity focus; otherwise EDOF, then CAF video/picture.
     * Tap metering overrides via the branch above.
     */
    private fun applyStreetSnapAf(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF) -> {
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                req.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
            }
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) -> {
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF)
            }
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> {
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> {
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
        }
    }

    /** Full-auto program: automatic exposure + continuous picture AF when the user has not tap-metered. */
    private fun applyAutoProgramAfAe(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        when {
            aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) ->
                req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON) ->
                req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ->
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        }
    }

    private fun centerWeightedMeteringRect(active: Rect, crop: Rect): MeteringRectangle {
        val frac = 0.45
        var cw = (crop.width() * frac).toInt().coerceAtLeast(1)
        var ch = (crop.height() * frac).toInt().coerceAtLeast(1)
        var cx = crop.left + (crop.width() - cw) / 2
        var cy = crop.top + (crop.height() - ch) / 2
        if (cx < active.left) cx = active.left
        if (cy < active.top) cy = active.top
        if (cx + cw > active.right) cw = (active.right - cx).coerceAtLeast(1)
        if (cy + ch > active.bottom) ch = (active.bottom - cy).coerceAtLeast(1)
        return MeteringRectangle(cx, cy, cw, ch, MeteringRectangle.METERING_WEIGHT_MAX)
    }

    private fun startRepeating(
        sess: CameraCaptureSession,
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
        camId: String,
    ) {
        val req = buildPreviewCaptureRequest(camera, surf, fpsRange, camId)

        try {
            if (sess is CameraConstrainedHighSpeedSessionShim && fpsRange != null) {
                // Not used; see below.
            }
        } catch (_: Throwable) {
            // no-op
        }

        try {
            // If this is a constrained high-speed session, we must use a high-speed request list.
            val constrained = runCatching { sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true) }.getOrDefault(false)
            if (constrained) {
                val list = (sess as? android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession)
                    ?.createHighSpeedRequestList(req)
                if (list != null) {
                    sess.setRepeatingBurst(list, fpsMeasuringCallback(), handler)
                    lastStatus = "Preview running (HFR ${fpsRange?.upper ?: "?"}fps)"
                    Log.d(tag, "HFR repeatingBurst started (n=${list.size})")
                    scheduleReadoutChromeUxFallback()
                    return
                }
            }

            sess.setRepeatingRequest(req, fpsMeasuringCallback(), handler)
            lastStatus = "Preview running (normal)"
            Log.d(tag, "Normal repeatingRequest started")
            scheduleReadoutChromeUxFallback()
        } catch (e: CameraAccessException) {
            lastStatus = "Repeating failed: ${e.reason}"
        } catch (t: Throwable) {
            lastStatus = "Repeating failed: ${t::class.java.simpleName}"
        }
    }

    private fun updatePreviewMetadata(result: CaptureResult) {
        result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { lastPreviewIso = it }
        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { lastPreviewExposureNs = it }
        result.get(CaptureResult.CONTROL_AWB_MODE)?.let { lastPreviewAwbMode = it }
        maybeLogChromeUxReadout()
    }

    private fun maybeLogChromeUxReadout() {
        if (loggedChromeUxReadout) return
        if (lastPreviewIso == null && lastPreviewExposureNs == null && lastPreviewAwbMode == null) return
        loggedChromeUxReadout = true
        readoutFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        readoutFallbackRunnable = null
        val iso = lastPreviewIso
        val ss = PreviewReadoutFormat.formatShutter(lastPreviewExposureNs)
        val awb = PreviewReadoutFormat.awbModeLabel(lastPreviewAwbMode)
        val fps = "%.1f".format(smoothedFps)
        mainHandler.post {
            Log.i(
                "PNS.ChromeUx",
                "readout=live iso=${iso ?: "—"} ss=$ss awb=$awb fps=$fps",
            )
        }
    }

    /** When preview metadata keys never arrive, still emit a gate-friendly ChromeUx line. */
    private fun scheduleReadoutChromeUxFallback() {
        readoutFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        val r =
            Runnable {
                if (loggedChromeUxReadout) return@Runnable
                loggedChromeUxReadout = true
                Log.i(
                    "PNS.ChromeUx",
                    "readout=fallback fps=${"%.1f".format(smoothedFps)} metadata=pending",
                )
            }
        readoutFallbackRunnable = r
        mainHandler.postDelayed(r, 10_000L)
    }

    private fun fpsMeasuringCallback(): CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                onWallTick()
                onCaptureResult(result)
                processFaceStatistics(result)
            }

            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult,
            ) {
                // Some devices deliver timestamps in partials; accept if present.
                onWallTick()
                updatePreviewMetadata(partialResult)
                val ts = partialResult.get(CaptureResult.SENSOR_TIMESTAMP)
                if (ts == null) {
                    framesMissingTimestamp++
                    return
                }
                framesWithTimestamp++
                onTimestamp(ts)
            }

            private fun onCaptureResult(result: TotalCaptureResult) {
                updatePreviewMetadata(result)
                val ts = result.get(CaptureResult.SENSOR_TIMESTAMP)
                if (ts == null) {
                    framesMissingTimestamp++
                    return
                }
                framesWithTimestamp++
                onTimestamp(ts)
            }

            private fun onWallTick() {
                // Fallback FPS meter when SENSOR_TIMESTAMP is missing (some HFR paths).
                val now = SystemClock.elapsedRealtimeNanos()
                val prev = lastWallNs
                lastWallNs = now
                if (prev <= 0L) return
                val dt = now - prev
                if (dt <= 0L) return
                val inst = 1e9 / dt.toDouble()
                smoothedWallFps = if (smoothedWallFps <= 0.0) inst else (smoothedWallFps * 0.90 + inst * 0.10)
            }

            private fun onTimestamp(tsNs: Long) {
                val prev = lastTimestampNs
                lastTimestampNs = tsNs
                if (prev <= 0L) return
                val dt = tsNs - prev
                if (dt <= 0L) return
                val inst = 1e9 / dt.toDouble()
                smoothedFps = if (smoothedFps <= 0.0) inst else (smoothedFps * 0.90 + inst * 0.10)

                // At ~60fps, dt~16.7ms is always <25ms so this would log every frame — use suppress during ADB automation.
                if (!suppressPeriodicFpsLogs && (dt < 25_000_000L || dt > 100_000_000L)) {
                    Log.d(tag, "fps=${"%.1f".format(smoothedFps)} dtMs=${"%.1f".format(dt / 1e6)}")
                }
            }
        }

    private fun pickHighSpeedTarget(map: StreamConfigurationMap?, desiredFps: Int): Pair<Size, Range<Int>>? {
        if (map == null) return null
        val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null

        // Prefer 1920x1080 if available; else 1280x720; else any.
        val preferredOrder = listOf(Size(1920, 1080), Size(1280, 720))
        val candidateSizes = (preferredOrder.filter { p -> sizes.any { it == p } } + sizes).distinct()

        for (s in candidateSizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
            val exact = ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }
            if (exact != null) return s to exact
            val capped = ranges.firstOrNull { it.upper == desiredFps }
            if (capped != null) return s to capped
        }
        return null
    }

    private fun pickNormalFpsRange(camId: String, desiredFps: Int): Range<Int>? {
        if (desiredFps <= 0) return null
        val ranges = runCatching {
            cm.getCameraCharacteristics(camId).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        }.getOrNull().orEmpty()
        if (ranges.isEmpty()) return null

        // Prefer exact fixed range (e.g., 60-60), else closest range that can include desired.
        ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.lower <= desiredFps && it.upper >= desiredFps }?.let { return it }
        // Some physical tele cameras omit an exact fixed range; pick the closest band so preview still runs.
        ranges.minByOrNull { kotlin.math.abs(it.upper - desiredFps) }?.let { return it }
        return ranges.firstOrNull()
    }

    /**
     * BUILD_PLAN UI milestone: pick preview sizes whose aspect matches the active sensor array when
     * possible; [TexturePreviewFit] applies uniform **center-crop** in the [TextureView] so the
     * finder fills width without side pillarboxing (cropping top/bottom or left/right as needed).
     */
    private fun pickNormalPreviewSize(
        map: StreamConfigurationMap?,
        activeArray: Rect? = null,
        chars: CameraCharacteristics? = null,
    ): Size? {
        if (map == null) return null
        val sizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null

        // Cap at ~1080p area to keep preview lightweight on lower-end devices, but still pick
        // the size whose aspect best matches the full sensor (no crop, no distortion).
        val maxArea = 1920L * 1440L

        // Prefer the same aspect as the largest RAW still output when advertised — aligns finder
        // geometry with DNG; fall back to active array (preview crop) aspect.
        val sensorAspect =
            chars
                ?.let { ch ->
                    RawCaptureSupport.pickRawOutput(ch)?.second?.let { sz ->
                        sz.width.toFloat() / sz.height.toFloat()
                    }
                }
                ?: if (activeArray != null && activeArray.width() > 0 && activeArray.height() > 0) {
                    activeArray.width().toFloat() / activeArray.height().toFloat()
                } else {
                    4f / 3f
                }

        val matching =
            sizes.filter { size ->
                val a = size.width.toFloat() / size.height.toFloat()
                kotlin.math.abs(a - sensorAspect) < 0.015f
            }
        val pool = if (matching.isNotEmpty()) matching else sizes

        val capped = pool.filter { it.width.toLong() * it.height.toLong() <= maxArea }
        val pick =
            (if (capped.isNotEmpty()) capped else pool)
                .maxByOrNull { it.width.toLong() * it.height.toLong() }
        return pick ?: sizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

}

// Marker shim (kept to avoid accidental imports/edits later).
private interface CameraConstrainedHighSpeedSessionShim

