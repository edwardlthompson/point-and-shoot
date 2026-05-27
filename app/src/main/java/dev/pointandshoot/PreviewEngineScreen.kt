package dev.pointandshoot

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
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
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.util.Log
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.location.Location
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.opengl.GLSurfaceView
import android.view.Display
import android.view.PixelCopy
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.CameraEnhance
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import dev.pointandshoot.fleet.OnePlus13FleetPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.text.Charsets

/**
 * Right rail holds collapsible quick-setting blocks. We give it
 * extra width so the shutter (64.dp) sits comfortably with padding and the rail can hold the
 * "more settings" the BUILD_PLAN UI milestone calls for, instead of putting the shutter over
 * the preview.
 */
private fun Modifier.chromeGlyphRotation(degrees: Float): Modifier =
    graphicsLayer {
        rotationZ = degrees
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }

/**
 * Portrait preview column: finder vs [PreviewRightRail] weighted height share. Larger finder
 * weight yields a taller finder slot so a **3:4** (width:height) tile can use full width without
 * side letterboxing on typical tall phones; paired with in-slot sizing below.
 */
private const val PreviewChromeFinderFlexWeight = 2.9f
private const val PreviewChromeRailFlexWeight = 1f

/** Live preview always shrink-to-fit in the finder ([TexturePreviewFit] center-contain). */
private const val PREVIEW_FINDER_CONTAIN = false

private enum class ChromeGridQuickAction {
    /** Popup: self-timer + burst shutter behavior (single / timer / burst). */
    TimerStub,
    ToggleHistogram,
    ToggleHorizonLevel,
    ToggleEyeAfOverlay,
    ToggleVideoTally,
    ToggleMaxBrightnessPreview,
    ToggleDndInPreview,
    /** Popup: tap-to-capture + volume keys (merged quick setting). */
    ExtraShutterMenu,
    /** Short-press cycles flash mode; long-press opens picker. */
    CycleFlash,
    /** Icon-only: embed GPS in DNG/JPEG when permission allows. */
    ToggleSaveLocation,
    /** Sprint **15.8/15.9** — lens OIS ([HudSettings.enableLensOpticalStabilization]). */
    ToggleLensOis,
    /** Sprint **15.8/15.9** — preview EIS ([HudSettings.enableVideoStabilizationPreview]). */
    ToggleVideoEis,
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

/**
 * Scroll-area shortcuts: **7 columns × 3 logical shortcut rows** (plus a separate focal-length row of 7).
 * Target FPS lives on the readout strip. Empty grid coordinates render as inert cells.
 */
private val previewChromeGridSlots: List<ChromeGridSlotSpec> =
    listOf(
        ChromeGridSlotSpec.ExpandShortcut(1, 0, "Guides", Icons.Outlined.GridOn, "Guides"),
        ChromeGridSlotSpec.ExpandShortcut(1, 1, "Preview & keys", Icons.Outlined.TouchApp, "Preview & keys"),
        ChromeGridSlotSpec.ExpandShortcut(1, 2, "Capture & tools", Icons.Outlined.PhotoCamera, "Capture & tools"),
        ChromeGridSlotSpec.QuickAction(1, 3, Icons.Outlined.Timer, "Shutter: single, timer, or burst", ChromeGridQuickAction.TimerStub),
        ChromeGridSlotSpec.QuickAction(1, 4, Icons.Outlined.BarChart, "Histogram", ChromeGridQuickAction.ToggleHistogram),
        ChromeGridSlotSpec.QuickAction(1, 5, Icons.Outlined.Landscape, "Horizon level", ChromeGridQuickAction.ToggleHorizonLevel),
        ChromeGridSlotSpec.QuickAction(1, 6, Icons.Outlined.Face, "Eye AF overlay", ChromeGridQuickAction.ToggleEyeAfOverlay),
        ChromeGridSlotSpec.QuickAction(2, 0, Icons.Outlined.Videocam, "Video tally", ChromeGridQuickAction.ToggleVideoTally),
        ChromeGridSlotSpec.QuickAction(2, 1, Icons.Outlined.BrightnessHigh, "Max brightness in preview", ChromeGridQuickAction.ToggleMaxBrightnessPreview),
        ChromeGridSlotSpec.QuickAction(2, 2, Icons.Outlined.DoNotDisturb, "DND while in preview", ChromeGridQuickAction.ToggleDndInPreview),
        ChromeGridSlotSpec.QuickAction(2, 3, Icons.Outlined.PhotoCamera, "Optical stabilization OIS", ChromeGridQuickAction.ToggleLensOis),
        ChromeGridSlotSpec.QuickAction(2, 4, Icons.Outlined.Videocam, "Electronic stabilization EIS", ChromeGridQuickAction.ToggleVideoEis),
        ChromeGridSlotSpec.QuickAction(2, 5, Icons.Outlined.FlashOn, "Flash mode, tap to cycle, long press for menu", ChromeGridQuickAction.CycleFlash),
        ChromeGridSlotSpec.ExpandShortcut(2, 6, "Settings", Icons.Outlined.Settings, "Settings"),
    )

/** Immutable placeholder until the first YUV histogram sample arrives (overlay stays visible when enabled). */
private val PreviewHistogramPendingBins = IntArray(PreviewLumaHistogram.BIN_COUNT)

private val PreviewBottomTrayHeight = 92.dp
private val PreviewGalleryThumbSize = 56.dp

/** Seam between major preview chrome vertical bands; see docs/preview-chrome-layout-style-guide.md */
private val PreviewChromeSectionDividerAlpha = 0.22f

@Composable
private fun PreviewChromeSectionDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = Color.White.copy(alpha = PreviewChromeSectionDividerAlpha),
    )
}

private fun formatDngSoftwareLine(context: Context, lut: LutCatalog): String =
    DngLutMetadata.formatSoftwareTag(
        appVersion = PnsAppInfo.versionName(context),
        activeLut = lut.identityForDngMetadata(),
    )

private fun dngUniqueCameraModelForSave(cameraId: String, lut: LutCatalog): String? =
    if (
        DngSaveBisectState.skipUniqueCameraModel ||
        dev.pointandshoot.fleet.OnePlus13FleetPolicy.skipUniqueCameraModelOnLeafDng(cameraId)
    ) {
        null
    } else {
        formatDngUniqueCameraModelLine(cameraId, lut)
    }

private fun wideLeafCalibrationCharacteristicsForDngSave(
    cm: android.hardware.camera2.CameraManager,
    sessionCameraId: String,
): android.hardware.camera2.CameraCharacteristics? {
    if (!dev.pointandshoot.fleet.OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng()) {
        return null
    }
    if (
        sessionCameraId != dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_UW &&
        sessionCameraId != dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_TELE
    ) {
        return null
    }
    return cm.getCameraCharacteristics(dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_WIDE)
}

private fun shouldApplyStillMetadataToDng(sessionCameraId: String): Boolean {
    if (DngSaveBisectState.skipStillMetadataApply) return false
    if (dev.pointandshoot.fleet.OnePlus13FleetPolicy.skipStillMetadataApplyOnLeafDng(sessionCameraId)) {
        return false
    }
    return true
}

private fun formatDngUniqueCameraModelLine(cameraId: String, lut: LutCatalog): String =
    DngLutMetadata.formatUniqueCameraModel(
        deviceModel = Build.MODEL,
        cameraId = cameraId,
        activeLut = lut.identityForDngMetadata(),
        includeLutMarkerInUniqueCameraModel = false,
    )

/**
 * Successful [PreviewController.captureRawStill].
 *
 * [companionJpegUri] is always **null** from the first success callback (companion JPEG is encoded
 * asynchronously). Use [PreviewController.captureRawStill]'s `onCompanionJpegReady` when you need the
 * JPEG URI (e.g. `ACTION_IMAGE_CAPTURE`). When the capture session has no hardware JPEG surface,
 * there is no companion — `onCompanionJpegReady(null)` is invoked once encoding would have completed.
 */
data class RawStillSaveSuccess(
    /** Primary DNG when the RAW tier is on; empty when tonal-only. */
    val dngUriString: String,
    /** Independent tonal file (JXL / AVIF / fallback JPEG), not a RAW companion. */
    val tonalUriString: String? = null,
) {
    val companionJpegUri: Uri?
        get() = tonalUriString?.let { Uri.parse(it) }
}

/** Main-thread settle before first ADB sequential RAW when Ultra-Max (RAW12) restarts the pipeline (M6 gate). */
private const val ULTRA_MAX_ADB_SEQUENTIAL_RAW_SETTLE_MS = 6000L

/** After session open, require this many TextureView updates before ADB sequential RAW12 (M6 cold start). */
private const val ADB_SEQUENTIAL_RAW_MIN_TEXTURE_FRAMES = 30L

private const val ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_WAIT_MAX_ITERATIONS = 200

private const val ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_POLL_MS = 50L

/** After [canCaptureRawStill] is true, wait before first ADB still (HAL/preview pump after session races). */
private const val ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS = 4500L

/** [adbRawStillFastAutomation] — shorter post-ready settle for quick device smoke (still long enough for OEM RAW pump). */
private const val ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS_FAST = 3200L
/** Extra AE settle after focal-slot switch on OP13 ProShot leaf path (USB bayer vs ProShot). */
private const val ADB_SEQUENTIAL_RAW_PROSHOT_FOCAL_EXTRA_SETTLE_MS = 5000L

/** [adbRawStillFastAutomation] — GL frames before first scripted still (aligned with non-fast cold stability). */
private const val ADB_SEQUENTIAL_RAW_MIN_TEXTURE_FRAMES_FAST = 28L

/** [adbRawStillFastAutomation] — gap between sequential stills. */
private const val ADB_SEQUENTIAL_RAW_GAP_MS_FAST = 900L

private const val ADB_SEQUENTIAL_RAW_GAP_MS_DEFAULT = 2500L

private const val ADB_WAIT_CAN_CAPTURE_RAW_POLL_MS = 400L

private const val ADB_WAIT_CAN_CAPTURE_RAW_POLL_MS_FAST = 200L

private const val ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_POLL_MS_FAST = 25L

private fun scaleBitmapToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0) return src
    val longest = maxOf(w, h)
    if (longest <= maxSide) return src
    val scale = maxSide.toFloat() / longest.toFloat()
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    val out = Bitmap.createScaledBitmap(src, nw, nh, true)
    if (out !== src) src.recycle()
    return out
}

private suspend fun deliverImageCaptureToCaller(
    contract: ImageCaptureReturnContract,
    tonalUri: Uri?,
) {
    val host = contract.host
    val app = host.applicationContext
    if (tonalUri == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                app,
                "Still capture needs a tonal JPEG/JXL output for this intent.",
                Toast.LENGTH_LONG,
            ).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
        }
        return
    }
    val target = contract.callerOutputUri
    try {
        if (target != null) {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(target)?.use { out ->
                    app.contentResolver.openInputStream(tonalUri)?.use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Cannot open caller output URI for write")
            }
            withContext(Dispatchers.Main) {
                host.setResult(Activity.RESULT_OK, null)
                host.finish()
            }
        } else {
            val scaled =
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(tonalUri)?.use { stream ->
                        val decoded =
                            BitmapFactory.decodeStream(stream)
                                ?: error("JPEG decode failed")
                        scaleBitmapToMaxSide(decoded, maxSide = 1024)
                    } ?: error("Cannot read companion JPEG")
                }
            withContext(Dispatchers.Main) {
                host.setResult(
                    Activity.RESULT_OK,
                    Intent().apply { putExtra("data", scaled) },
                )
                host.finish()
            }
        }
    } catch (e: Throwable) {
        withContext(Dispatchers.Main) {
            Toast.makeText(app, PnsUserFacingErrors.stillCaptureFailure(e), Toast.LENGTH_LONG).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
        }
    }
}

private const val VIDEO_INTENT_ENCODE_WIDTH_HIGH = 1280
private const val VIDEO_INTENT_ENCODE_WIDTH_LOW = 640
private const val VIDEO_INTENT_ENCODE_HEIGHT_HIGH = 720
private const val VIDEO_INTENT_ENCODE_HEIGHT_LOW = 360

private suspend fun deliverVideoCaptureToCaller(
    contract: VideoCaptureReturnContract,
    imagingProfile: ImagingProfile,
    /** When set, copy this in-app recording to the caller instead of synthesizing a clip. */
    recordedVideoUri: Uri? = null,
) {
    val host = contract.host
    val app = host.applicationContext
    val resolver = app.contentResolver
    try {
        val resultUri =
            withContext(Dispatchers.IO) {
                if (recordedVideoUri != null) {
                    if (contract.callerOutputUri != null) {
                        resolver.openInputStream(recordedVideoUri)?.use { input ->
                            resolver.openOutputStream(contract.callerOutputUri)?.use { out ->
                                input.copyTo(out)
                            }
                        } ?: error("Cannot read in-app recording for caller handoff")
                        contract.callerOutputUri
                    } else {
                        recordedVideoUri
                    }
                } else if (contract.callerOutputUri != null) {
                    resolver.openFileDescriptor(contract.callerOutputUri, "rw")?.use { pfd ->
                        val w = if (contract.preferHighQuality) VIDEO_INTENT_ENCODE_WIDTH_HIGH else VIDEO_INTENT_ENCODE_WIDTH_LOW
                        val h = if (contract.preferHighQuality) VIDEO_INTENT_ENCODE_HEIGHT_HIGH else VIDEO_INTENT_ENCODE_HEIGHT_LOW
                        SystemVideoClipEncoder.encodeSolidColorClip(
                            pfd,
                            w,
                            h,
                            contract.preferHighQuality,
                        ).getOrThrow()
                    } ?: error("Cannot open caller video URI for write")
                    contract.callerOutputUri
                } else {
                    val (uri, pfd) = CaptureStorage.openVideoOutputReadWritePfd(app, imagingProfile)
                    try {
                        pfd.use { fd ->
                            val w = if (contract.preferHighQuality) VIDEO_INTENT_ENCODE_WIDTH_HIGH else VIDEO_INTENT_ENCODE_WIDTH_LOW
                            val h = if (contract.preferHighQuality) VIDEO_INTENT_ENCODE_HEIGHT_HIGH else VIDEO_INTENT_ENCODE_HEIGHT_LOW
                            SystemVideoClipEncoder.encodeSolidColorClip(
                                fd,
                                w,
                                h,
                                contract.preferHighQuality,
                            ).getOrThrow()
                        }
                        CaptureStorage.finalizePendingVideoInsert(app, uri)
                        uri
                    } catch (t: Throwable) {
                        CaptureStorage.discardPendingVideo(app, uri)
                        throw t
                    }
                }
            }
        withContext(Dispatchers.Main) {
            PnsAdbLog.i(app, "videoIntentReturn ok uri=$resultUri")
            host.setResult(Activity.RESULT_OK, Intent().apply { data = resultUri })
            host.finish()
        }
    } catch (e: Throwable) {
        withContext(Dispatchers.Main) {
            Toast.makeText(app, "Video save failed: ${e.message?.take(80) ?: e::class.java.simpleName}", Toast.LENGTH_LONG).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
        }
    }
}

private const val FOCAL_SLOT_PROBE_EMPTY_IDS_WAIT_LOOPS = 80
private const val FOCAL_SLOT_PROBE_NULL_CAMERA_WAIT_LOOPS = 80
private const val FOCAL_SLOT_PROBE_POST_SEED_MS = 600L
/** After [selectedCameraId] changes for ADB focal probe, wait for matching committed session. */
private const val FOCAL_SLOT_PROBE_POST_SWITCH_SETTLE_LOOPS = 400
private const val FOCAL_SLOT_PROBE_SEED_SESSION_LOOPS = 120

private fun stillCaptureSurfaceRotationFromPhysicalCardinal(physicalCardinalSnapDegrees: Float): Int =
    RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(physicalCardinalSnapDegrees.roundToInt())

/**
 * Logical multi-camera: when a tele slot resolves to the **logical** parent id, the preview
 * [OutputConfiguration] must pin [physicalTeleId] so the routed sensor matches the preset.
 * Applied synchronously on the main thread so [setFocalCrop] restarts see the same pin as
 * [setDesired] (which may clear or keep the pin when [selectedCameraId] changes).
 */
private fun schedulePreviewPhysicalForFocalSlot(
    context: Context,
    controller: PreviewController,
    slot: FocalMmSlot,
    pair: Pair<String, FocalMode?>,
    ids: List<String>,
) {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val roles = BackCameraRoleResolver.resolve(cm, ids)
    val telePhysical =
        when (slot) {
            FocalMmSlot.M73, FocalMmSlot.M85, FocalMmSlot.M150 ->
                telePhysicalForPreviewPin(slot, roles)
            else -> null
        }
    val uwPhysical =
        if (slot == FocalMmSlot.M14) ultraWidePhysicalForPreviewPin(slot, roles) else null
    val physical =
        when {
            slot == FocalMmSlot.M73 || slot == FocalMmSlot.M85 || slot == FocalMmSlot.M150 -> {
                val parent = telePhysical?.let { logicalParentForPhysicalCamera(cm, it, ids) }
                if (telePhysical != null && parent != null && pair.first == parent) telePhysical else null
            }
            slot == FocalMmSlot.M14 -> {
                val parent = uwPhysical?.let { logicalParentForPhysicalCamera(cm, it, ids) }
                if (uwPhysical != null && parent != null && pair.first == parent) uwPhysical else null
            }
            else -> null
        }
    controller.setPreviewSurfacePhysicalCameraId(physical)
}

private data class PreviewEnginePollState(
    val status: String,
    val measuredFps: Double,
    val previewReadoutIso: Int?,
    val previewReadoutExposureNs: Long?,
    val previewReadoutAwbMode: Int?,
    val previewLogicalPhysicalId: String?,
    val previewJpegCompanion: Boolean,
    val surfaceInfo: String,
    val previewBufferSize: Size?,
    val sensorOrientationDeg: Int?,
) {
    companion object {
        fun idle(): PreviewEnginePollState =
            PreviewEnginePollState(
                status = "Idle",
                measuredFps = 0.0,
                previewReadoutIso = null,
                previewReadoutExposureNs = null,
                previewReadoutAwbMode = null,
                previewLogicalPhysicalId = null,
                previewJpegCompanion = false,
                surfaceInfo = "surface=?",
                previewBufferSize = null,
                sensorOrientationDeg = null,
            )
    }
}

private fun previewEnginePollStateFromController(c: PreviewController): PreviewEnginePollState =
    PreviewEnginePollState(
        status = c.status(),
        measuredFps = c.measuredFps(),
        previewReadoutIso = c.previewMeterIso(),
        previewReadoutExposureNs = c.previewMeterExposureNs(),
        previewReadoutAwbMode = c.previewReadoutAwbMode(),
        previewLogicalPhysicalId = c.previewMeterLogicalPhysicalId(),
        previewJpegCompanion = c.previewUsesJpegCompanion(),
        surfaceInfo = c.surfaceDebug(),
        previewBufferSize = c.previewBufferSize(),
        sensorOrientationDeg = c.sensorOrientationDegrees(),
    )

@Composable
fun PreviewEngineScreen(
    onBack: () -> Unit,
    onOpenDeveloperMenu: () -> Unit = {},
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
    startAutoSweep: Boolean = false,
    /** From `am start` extras — see `EXTRA_PNS_PREVIEW_*` in [CameraCapabilitiesProbe]. */
    adbInitialDial: CommandDialMode? = null,
    adbSequentialRawStills: Int = 0,
    /**
     * When true (`--ez pns_preview_raw_still_fast true` with raw count), ADB sequential RAW uses
     * shorter settle/poll delays for quicker device smoke (does not change in-app H behavior).
     */
    adbRawStillFastAutomation: Boolean = false,
    adbBracketPattern: BracketPattern? = null,
    adbInitialImagingProfile: ImagingProfile? = null,
    /** Optional [RawStreamPreference] name via `pns_preview_raw_stream` (session RAW pick matrix). */
    adbRawStreamPreference: RawStreamPreference? = null,
    /** When non-null, session-only seed for JPEG companion (`pns_preview_jpeg_companion`). */
    adbJpegCompanionSeed: Boolean? = null,
    /** Sprint **AS.1** — `pns_preview_audio_hifi` (session-only). */
    adbAudioHiFiSeed: Boolean? = null,
    /** Sprint **AS.1** — `pns_preview_audio_wind` (session-only). */
    adbAudioWindSeed: Boolean? = null,
    /** Sprint **AS.2** — `pns_preview_shutter_sound_pack` (session-only). */
    adbShutterSoundPackSeed: String? = null,
    /** `--ez pns_preview_composed_still true` — one IMG-matrix still via [PreviewController.captureComposedStill]. */
    adbComposedStillSmoke: Boolean = false,
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
    /** **`--es pns_preview_focal_mm_slot N`** — applies [FocalMmSlot] once after seed; logs **`focalSlotTap=`**. */
    adbFocalMmSlotProbe: FocalMmSlot? = null,
    /** Sprint **15.10** — `--el pns_preview_readout_shutter_ns` (lock SS so ISO chase logs can be proven). */
    adbReadoutShutterNsProbe: Long? = null,
    /** Sprint **15.1** — ADB seed: enable face/eye overlay markers. */
    adbEyeAfOverlaySeed: Boolean? = null,
    /** When non-null, activity was started with [MediaStore.ACTION_IMAGE_CAPTURE]; still capture returns JPEG to caller. */
    imageCaptureReturn: ImageCaptureReturnContract? = null,
    /** When non-null, [MediaStore.ACTION_VIDEO_CAPTURE] — stop mock record encodes a short clip and [Activity.finish]es with result. */
    videoCaptureReturn: VideoCaptureReturnContract? = null,
    /**
     * When false, bottom tray opens with **video** as the primary shutter (matches [MediaStore.INTENT_ACTION_VIDEO_CAMERA]).
     * Photo-primary keeps preview FPS target fixed at 120; video-primary enables FPS pickers.
     */
    initialPrimaryPhoto: Boolean = true,
    /**
     * Debug + cold preview intent: record **N** seconds via in-app [android.media.MediaRecorder] once settled.
     * See **`scripts/pns_in_app_video_verify.ps1`**.
     */
    adbAutomationInAppVideoSec: Int = 0,
  /** Target FPS for ADB video automation (`--ei pns_preview_video_fps`). */
    adbAutomationVideoFps: Int? = null,
    /** Chrome encode width for ADB video automation (`--ei pns_preview_video_encode_w`, **13V.16**). */
    adbAutomationVideoEncodeW: Int? = null,
    /** Chrome encode height for ADB video automation (`--ei pns_preview_video_encode_h`, **13V.16**). */
    adbAutomationVideoEncodeH: Int? = null,
    /** HEVC Main10 for ADB video automation (`--ez pns_preview_video_10bit`). */
    adbAutomationVideoTenBit: Boolean = false,
    /** DCG HDR10 for ADB video automation (`--ez pns_preview_video_dcg`). */
    adbAutomationVideoDcg: Boolean = false,
    /** AV1 encode for ADB (`--ez pns_preview_video_av1`, Sprint **VF.1**). */
    adbAutomationVideoAv1: Boolean = false,
    /** Codec ordinal override (`--ei pns_preview_video_codec_ordinal`, Sprint **VF.1**). */
    adbAutomationVideoCodecOrdinal: Int? = null,
    /** OIS+EIS for ADB (`--ez pns_preview_video_stabilization`, Sprint **VF.2**). */
    adbAutomationVideoStabilization: Boolean = false,
    /** RAW video automation (`--ei pns_preview_video_raw_sec N`, Sprint **13.6**). */
    adbAutomationVideoRawSec: Int = 0,
    /** DNG matrix bisect — see [DngSaveBisectState] and `EXTRA_PNS_PREVIEW_DNG_*`. */
    adbDngBisectActive: Boolean = false,
    /** `pns_preview_still_mode` — [StillCaptureMode] for 13.8 automation (non-Standard falls back until shipped). */
    adbStillCaptureMode: StillCaptureMode? = null,
    /** Sprint **CC.1** — `pns_preview_burst_count` + `pns_preview_burst_interval_ms` composed still burst. */
    adbBurstStillCount: Int = 0,
    adbBurstIntervalMs: Int = 0,
    /** `pns_preview_focus_peaking` — seeds HUD peaking color (e.g. `Red`) for Sprint **13V.10** gates. */
    adbSeedFocusPeakingColor: FocusPeakingColor? = null,
    /** `pns_preview_focus_mode` — Sprint **14.8** (`auto`, `manual`, `macro`, …). */
    adbPreviewFocusMode: PreviewFocusSelection? = null,
    /** `pns_preview_video_lut` — seeds [HudSettings.selectedLutForVideo] for Sprint **13V.11** gates. */
    adbSeedVideoLutName: String? = null,
    /** `pns_preview_force_power_thermal` — Sprint **13V.12** gate: show power HUD without HFR FPS. */
    adbForcePowerThermalOverlay: Boolean = false,
    /** `pns_preview_adaptive_battery_pct` — Sprint **PO.2** gate: battery % override for adaptive FPS. */
    adbAdaptiveBatteryPctOverride: Int? = null,
    /** `pns_preview_adaptive_thermal_status` — Sprint **PO.2** gate: thermal status override for adaptive FPS. */
    adbAdaptiveThermalStatusOverride: Int? = null,
    /** `pns_preview_storage_available_bytes` — Sprint **13V.13** gate: StatFs override for estimate math. */
    adbStorageAvailableBytes: Long? = null,
    /** `pns_preview_smile_still` — Sprint **13V.17**: enable smile-triggered still. */
    adbEnableSmileStill: Boolean = false,
    /** `pns_preview_smile_still_synthetic` — Sprint **13V.17** gate: one tray still without ML face. */
    adbSmileStillSynthetic: Boolean = false,
    /** `pns_preview_video_bitrate_scale` — Sprint **13V.17**: HUD bitrate scale percent. */
    adbVideoBitrateScalePercent: Int? = null,
    /** `pns_preview_scene_vendor_hints` — Sprint **13V.17**: scene vendor hint log toggle. */
    adbSceneVendorHints: Boolean = false,
    /** `--ez pns_preview_show_about true` — Sprint **14.11** gate: in-preview About overlay. */
    adbShowAboutOverlay: Boolean = false,
    /** Sprint **CC.3** — `pns_preview_tether` enables loopback HTTP tether. */
    adbTetherEnabled: Boolean = false,
    /** Sprint **CC.3** — `pns_preview_picture_profile` (e.g. `cinematic`, `ultra_raw`). */
    adbPictureProfileId: String? = null,
    /** Sprint **CC.3** — `pns_preview_flash_strength` percent (25–100). */
    adbFlashStrengthPercent: Int? = null,
    /** Sprint **CC.3** — `pns_preview_cal_export` exports newest calibration JSON once. */
    adbCalExportSmoke: Boolean = false,
    /** Sprint **UX.1** — `pns_preview_theme_mode` (`system` | `light` | `dark`). */
    adbPreviewThemeMode: PnsThemeMode? = null,
    /** Sprint **UX.3** — `pns_preview_workflow_preset` (e.g. `street`). */
    adbWorkflowPresetId: String? = null,
    /** Sprint **UX.2/UX.3** — `pns_preview_open_gallery` opens bespoke gallery overlay. */
    adbOpenGallery: Boolean = false,
    /** Sprint **UX.3** — `pns_preview_gallery_batch_share` (≥2) auto batch-shares after index load. */
    adbGalleryBatchShareCount: Int? = null,
    adbCloudBackupEnabled: Boolean = false,
    adbCloudBackupSyncNow: Boolean = false,
    adbCloudBackupProbe: Boolean = false,
    adbPlatformShareProbe: Boolean = false,
    adbPlatformFileProviderProbe: Boolean = false,
    adbPlatformWidgetProbe: Boolean = false,
    adbLanTransfer: Boolean = false,
    adbLanTransferProbe: Boolean = false,
    adbWebDavProbe: Boolean = false,
    adbSocialStreamProbe: Boolean = false,
    adbCollaborativeProbe: Boolean = false,
    /** Engineering hub: live D-pad alignment for face/eye HUD overlays (Sprint **15.1**). */
    eyeOverlayCalibratorActive: Boolean = false,
    onEyeOverlayCalibratorDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val hostActivity = context.findHostActivity()
    val intentOverridesTrayRestore =
        hostActivity?.intent?.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO) == true ||
            hostActivity?.intent?.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA ||
            hostActivity?.intent?.action == MediaStore.ACTION_VIDEO_CAPTURE ||
            imageCaptureReturn != null ||
            videoCaptureReturn != null
    val restoredTraySurface =
        remember(intentOverridesTrayRestore) {
            if (intentOverridesTrayRestore) null else PreviewLastSurfacePrefs.load(appContext)
        }
    val resolvedInitialPrimaryPhoto =
        when {
            restoredTraySurface == PreviewLastSurface.Video -> false
            intentOverridesTrayRestore -> initialPrimaryPhoto
            else -> true
        }
    val resolvedInitialGallery =
        adbOpenGallery ||
            (restoredTraySurface == PreviewLastSurface.Gallery && !intentOverridesTrayRestore)
    val adbSelfTimerSecSanitized =
        adbInitialSelfTimerSec?.coerceIn(0, 60)?.also { v ->
            if (v != adbInitialSelfTimerSec) {
                Log.w("PNS.Preview", "pns_preview_self_timer_sec clamped from $adbInitialSelfTimerSec to $v")
            }
        }
    val captureScope = rememberCoroutineScope()
    val snackbarHostState = LocalPnsSnackbarHostState.current
    val controller = remember { PreviewController(context.applicationContext) }
    if (adbAutomationVideoCodecOrdinal != null) {
        controller.adbAutomationVideoCodecOrdinal = adbAutomationVideoCodecOrdinal
    }
    /** Owned for features that need the active [GLSurfaceView] (e.g. calibrate grab). */
    val previewHostSlot = remember { PreviewHostSlot() }
    val hudState = rememberHudSettings()
    var faceOverlayCalibration by remember {
        mutableStateOf(FaceOverlayCalibrationStore.load(appContext))
    }
    LaunchedEffect(eyeOverlayCalibratorActive) {
        if (eyeOverlayCalibratorActive) {
            faceOverlayCalibration = FaceOverlayCalibrationStore.load(appContext)
            hudState.updateMutate {
                it.copy(showEyeAfOverlay = true, showFaceAlignmentDebugCrosshair = true)
            }
            Log.i(FaceOverlayCalibration.TAG, "calibrator open ${faceOverlayCalibration.toDiagString()}")
        }
    }
    // Stable list instance when the id roster is unchanged — avoids extra child work from a fresh
    // [List] allocation on every 350 ms controller poll recomposition.
    val cameraIdsRaw = controller.cameraIds()
    val cameraIdsStableKey = cameraIdsRaw.joinToString(",")
    val cameraIdsList = remember(cameraIdsStableKey) { cameraIdsRaw.toList() }
    val cameraRoles =
        remember(cameraIdsList.toSortedSet().joinToString()) {
            val cm = context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager
            BackCameraRoleResolver.resolve(cm, cameraIdsList)
        }

    val adbSeedCameraIdEffective =
        remember(adbSeedCameraId, cameraIdsStableKey) {
            val raw = adbSeedCameraId?.trim()?.takeIf { it.isNotBlank() } ?: return@remember null
            if (raw in cameraIdsList) return@remember raw
            Log.w("PNS.Preview", "invalid pns_preview_camera_id=$raw; ignored (fallback id=2 when listed)")
            if ("2" in cameraIdsList) "2" else null
        }

    // Must run during composition (not SideEffect): LaunchedEffect can schedule `openCamera` in the
    // same pass; SideEffect runs after and would leave `suppressPeriodicFpsLogs` false for the
    // first `applyFaceDetectMode` / FPS callback.
    // Suppress FPS spam for any `am start` preview automation (dial / RAW / bracket).
    controller.suppressPeriodicFpsLogs =
        adbSequentialRawStills > 0 ||
            adbBracketPattern != null ||
            adbInitialDial != null ||
            !adbSeedCameraId.isNullOrBlank() ||
            adbSuperMacroProbe ||
            adbM6FpsLutProbe ||
            adbCalibrateGrabSmoke ||
            adbInitialSelfTimerSec != null ||
            adbFocalMmSlotProbe != null ||
            adbRawStreamPreference != null ||
            adbJpegCompanionSeed != null ||
            adbAudioHiFiSeed != null ||
            adbAudioWindSeed != null ||
            adbShutterSoundPackSeed != null ||
            adbAutomationInAppVideoSec > 0 ||
            adbAutomationVideoRawSec > 0 ||
            adbSeedFocusPeakingColor != null ||
            adbPreviewFocusMode != null ||
            !adbSeedVideoLutName.isNullOrBlank() ||
            adbEnableSmileStill ||
            adbSmileStillSynthetic ||
            adbVideoBitrateScalePercent != null ||
            adbSceneVendorHints ||
            adbTetherEnabled ||
            !adbPictureProfileId.isNullOrBlank() ||
            adbFlashStrengthPercent != null ||
            adbCalExportSmoke ||
            adbPreviewThemeMode != null ||
            !adbWorkflowPresetId.isNullOrBlank()
    controller.superMacroAdbProbe = adbSuperMacroProbe
    controller.adbForceRawVideoLane = adbAutomationVideoRawSec > 0
    // Bracket automation disables face stats to cut CameraMetadataJV noise. Sequential RAW-only (`pns_preview_raw_count`)
    // keeps the normal H-dial YUV/analysis path so OEM stacks (e.g. CPH2655) match in-app H session wiring.
    LaunchedEffect(adbDngBisectActive) {
        if (!adbDngBisectActive) {
            DngSaveBisectState.reset()
        } else {
            DngSaveBisectState.logActive()
        }
    }
    controller.automationSuppressFacePipeline =
        adbBracketPattern != null

    var lastStillModeSeed by remember { mutableStateOf<StillCaptureMode?>(null) }

    LaunchedEffect(adbStillCaptureMode, adbAutomationVideoDcg, adbAutomationVideoTenBit, hudState.current.stillCaptureMode) {
        val hudMode = hudState.current.stillCaptureMode
        val mode = adbStillCaptureMode ?: hudMode
        val prev = lastStillModeSeed
        controller.requestedStillCaptureMode = mode
        controller.adbAutomationVideoDcg = adbAutomationVideoDcg
        controller.adbAutomationVideoTenBit = adbAutomationVideoTenBit
        controller.applyStillCaptureModeForPipeline(mode)
        if (prev != null && prev != mode) {
            PnsAdbLog.i(context, "stillMode changed ${prev.name} -> ${mode.name}; restart preview session")
            controller.kickPreviewPipelineRestart()
        }
        lastStillModeSeed = mode
        adbStillCaptureMode?.let { seeded ->
            PnsAdbLog.i(context, "preview seeded stillMode=${seeded.name} (adb)")
        } ?: run {
            if (hudMode != StillCaptureMode.Standard) {
                PnsAdbLog.i(context, "preview seeded stillMode=${hudMode.name} (hud)")
            }
        }
    }

    LaunchedEffect(adbRawStreamPreference) {
        controller.setRawStreamPreference(adbRawStreamPreference ?: RawStreamPreference.Default)
        if (adbRawStreamPreference != null) {
            PnsAdbLog.i(context, "preview seeded rawStream=${adbRawStreamPreference.name}")
        }
    }

    LaunchedEffect(adbSeedFocusPeakingColor) {
        val color = adbSeedFocusPeakingColor ?: return@LaunchedEffect
        val cur = hudState.current
        if (cur.focusPeakingColor != color) {
            hudState.update(cur.copy(focusPeakingColor = color))
        }
        PnsAdbLog.i(context, "preview seeded focusPeakingColor=${color.name}")
    }

    LaunchedEffect(adbPreviewFocusMode) {
        val mode = adbPreviewFocusMode ?: return@LaunchedEffect
        controller.setPreviewFocusSelection(mode)
        PnsAdbLog.i(context, "preview seeded focusMode=${PreviewFocusMode.chromeUxLogValue(mode, null)}")
    }

    LaunchedEffect(adbPreviewFocusMode, adbInitialDial) {
        if (adbPreviewFocusMode != null || adbInitialDial != CommandDialMode.M) return@LaunchedEffect
        controller.ensureManualFocusForDialM()
    }

    LaunchedEffect(adbSeedVideoLutName) {
        val name = adbSeedVideoLutName?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val entry = LutCatalog.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (entry == null) {
            PnsAdbLog.e(context, "preview video_lut seed unknown name=$name")
            return@LaunchedEffect
        }
        val cur = hudState.current
        if (cur.selectedLutForVideo != entry.name) {
            hudState.update(cur.copy(selectedLutForVideo = entry.name))
        }
        PnsAdbLog.i(context, "preview seeded videoLut=${entry.name}")
    }

    LaunchedEffect(adbForcePowerThermalOverlay) {
        if (adbForcePowerThermalOverlay) {
            PnsAdbLog.i(context, "preview forcePowerThermalOverlay=true")
        }
    }

    LaunchedEffect(adbStorageAvailableBytes) {
        val bytes = adbStorageAvailableBytes ?: return@LaunchedEffect
        PnsAdbLog.i(context, "preview storageAvailableBytesOverride=$bytes")
    }

    LaunchedEffect(adbEnableSmileStill) {
        if (!adbEnableSmileStill) return@LaunchedEffect
        val cur = hudState.current
        if (!cur.enableSmileTriggeredStill) {
            hudState.update(cur.copy(enableSmileTriggeredStill = true))
        }
        PnsAdbLog.i(context, "preview seeded enableSmileTriggeredStill=true")
        Log.i("PNS.SmileStill", "smileStillEnabled=true (adb)")
    }

    LaunchedEffect(adbSceneVendorHints) {
        if (!adbSceneVendorHints) return@LaunchedEffect
        val cur = hudState.current
        if (!cur.showSceneVendorHints) {
            hudState.update(cur.copy(showSceneVendorHints = true))
        }
        PnsAdbLog.i(context, "preview seeded showSceneVendorHints=true")
    }

    LaunchedEffect(adbVideoBitrateScalePercent) {
        val pct = adbVideoBitrateScalePercent ?: return@LaunchedEffect
        val cur = hudState.current
        if (cur.videoBitrateScalePercent != pct) {
            hudState.update(cur.copy(videoBitrateScalePercent = pct))
        }
        PnsAdbLog.i(context, "preview seeded videoBitrateScalePercent=$pct")
    }

    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var primaryPhoto by rememberSaveable(resolvedInitialPrimaryPhoto) {
        mutableStateOf(resolvedInitialPrimaryPhoto)
    }
    val rawOrBracketAutomation = adbSequentialRawStills > 0 || adbBracketPattern != null
    // Photo-primary: stay below 120 so [createSession] can attach RAW ([canCaptureRawStill]). Use **60** for
    // ADB sequential RAW / bracket runs: **90** preview targets still saw HAL timeouts on some devices
    // (CPH2655) while controller [DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC] starts at 60 until UI sync.
    var selectedFps by remember(resolvedInitialPrimaryPhoto, rawOrBracketAutomation) {
        mutableStateOf(
            when {
                rawOrBracketAutomation -> 60
                resolvedInitialPrimaryPhoto -> 90
                // Video-primary default; HFR (≥120) uses MediaCodec + constrained high-speed session (13V.16).
                else -> 60
            },
        )
    }
    val powerThermalMonitor = remember { PreviewPowerThermalMonitor(context) }
    var userSelectedFps by remember(resolvedInitialPrimaryPhoto, rawOrBracketAutomation) {
        mutableStateOf(
            when {
                rawOrBracketAutomation -> 60
                resolvedInitialPrimaryPhoto -> 90
                else -> 60
            },
        )
    }

    LaunchedEffect(adbAdaptiveBatteryPctOverride, adbAdaptiveThermalStatusOverride) {
        adbAdaptiveBatteryPctOverride?.let {
            PnsAdbLog.i(context, "preview adaptiveBatteryPctOverride=$it")
        }
        adbAdaptiveThermalStatusOverride?.let {
            PnsAdbLog.i(context, "preview adaptiveThermalStatusOverride=$it")
        }
    }

    LaunchedEffect(adbAutomationVideoFps) {
        val fps = adbAutomationVideoFps
        if (fps != null && fps > 0) {
            selectedFps = fps.coerceIn(15, 480)
            userSelectedFps = selectedFps
            PnsAdbLog.i(context, "preview seeded videoFps=$fps (adb)")
        }
    }

    val previewState by produceState(
        initialValue = PreviewEnginePollState.idle(),
        key1 = controller,
    ) {
        while (true) {
            val next = previewEnginePollStateFromController(controller)
            if (next != value) {
                value = next
            }
            PreviewLogicalPhysicalDebugBridge.updateFromCaptureResult(next.previewLogicalPhysicalId)
            delay(350)
        }
    }
    var sweepJob by remember { mutableStateOf<Job?>(null) }
    var sweepRunId by remember { mutableStateOf<String?>(null) }
    val autoSweepConsumed = remember { AtomicBoolean(false) }

    LaunchedEffect(
        userSelectedFps,
        sweepJob,
        adbAdaptiveBatteryPctOverride,
        adbAdaptiveThermalStatusOverride,
        adbAutomationInAppVideoSec,
        adbAutomationVideoFps,
    ) {
        while (true) {
            if (sweepJob != null) {
                delay(3_000L)
                continue
            }
            // USB HFR gates: do not cap 120–480 fps mid-run when the device heats up after prior cases.
            if (adbAutomationInAppVideoSec > 0 && (adbAutomationVideoFps ?: 0) >= 120) {
                if (selectedFps != userSelectedFps) {
                    selectedFps = userSelectedFps
                }
                delay(3_000L)
                continue
            }
            val snap = powerThermalMonitor.sample()
            val batteryPct = adbAdaptiveBatteryPctOverride ?: snap.batteryPct
            val thermal = adbAdaptiveThermalStatusOverride ?: snap.thermalStatus
            val decision = PreviewAdaptiveFpsPolicy.decide(userSelectedFps, batteryPct, thermal)
            if (decision.capFps != null && selectedFps != decision.effectiveFps) {
                Log.i(
                    "PNS.PowerThermal",
                    "adaptiveFpsCap userFps=$userSelectedFps effective=${decision.effectiveFps} " +
                        "battery=$batteryPct thermal=$thermal reason=${decision.reason}",
                )
                selectedFps = decision.effectiveFps
            } else if (decision.capFps == null && selectedFps != userSelectedFps) {
                selectedFps = userSelectedFps
            }
            delay(3_000L)
        }
    }

    LaunchedEffect(
        adbSequentialRawStills,
        adbRawStillFastAutomation,
        adbBracketPattern,
        adbInitialDial,
        adbInitialImagingProfile,
        adbRawStreamPreference,
        adbJpegCompanionSeed,
        adbSeedCameraId,
        adbSuperMacroProbe,
        adbPreviewStillsLutName,
        adbM6FpsLutProbe,
        adbCalibrateGrabSmoke,
        adbInitialSelfTimerSec,
        adbFocalMmSlotProbe,
    ) {
        val captureAutomation = adbSequentialRawStills > 0 || adbBracketPattern != null
        val dialOrProfileOrSeed =
            adbInitialDial != null ||
                adbInitialImagingProfile != null ||
                adbRawStreamPreference != null ||
                !adbSeedCameraId.isNullOrBlank()
        val probeOrTimerOrLut =
            adbSuperMacroProbe ||
                adbM6FpsLutProbe ||
                adbCalibrateGrabSmoke ||
                !adbPreviewStillsLutName.isNullOrBlank() ||
                adbInitialSelfTimerSec != null ||
                adbFocalMmSlotProbe != null
        if (captureAutomation || dialOrProfileOrSeed || probeOrTimerOrLut) {
            PnsAdbLog.i(
                context,
                "automation extras raw=$adbSequentialRawStills bracket=$adbBracketPattern dial=$adbInitialDial " +
                    "profile=${adbInitialImagingProfile?.id} rawStream=${adbRawStreamPreference?.name} jpegSeed=$adbJpegCompanionSeed " +
                    "seedCam=$adbSeedCameraId superMacroProbe=$adbSuperMacroProbe " +
                    "stillsLutSeed=$adbPreviewStillsLutName m6FpsLutProbe=$adbM6FpsLutProbe calibrateGrabSmoke=$adbCalibrateGrabSmoke " +
                    "selfTimerSecSeed=$adbInitialSelfTimerSec focalMmSlot=${adbFocalMmSlotProbe?.labelMm} " +
                    "suppressFps=${controller.suppressPeriodicFpsLogs} suppressFacePipeline=${controller.automationSuppressFacePipeline} " +
                    "rawStillFast=$adbRawStillFastAutomation",
            )
        }
    }

    // Preview readout polling merged into [previewState] (see [PreviewEnginePollState]).
    LaunchedEffect(controller, adbSeedCameraIdEffective) {
        var waited = 0
        while (waited < 60 && controller.cameraIds().isEmpty()) {
            delay(50)
            waited++
        }
        val ids = controller.cameraIds()
        if (ids.isEmpty()) return@LaunchedEffect
        val seed = adbSeedCameraIdEffective?.trim()?.takeIf { it.isNotBlank() }
        when {
            seed != null && seed in ids -> {
                // Always honor a valid ADB seed — a first-pass null seed can let M23 pick first, then this
                // effect re-runs once extras arrive; we must override a stale selection.
                if (selectedCameraId != seed) {
                    selectedCameraId = seed
                }
                PnsAdbLog.i(context, "preview seed cameraId=$seed ok")
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
                while (!PreviewLongRunningPause.shouldContinueSweep()) delay(500)
                if (!isActive) return@launch
                selectedCameraId = cam
                Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                delay(700)

                for (fps in sequence) {
                    while (!PreviewLongRunningPause.shouldContinueSweep()) delay(500)
                    if (!isActive) return@launch
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

    LaunchedEffect(selectedCameraId, selectedFps, primaryPhoto, sweepJob) {
        // Photo mode must honor [selectedFps] (FPS sheet / readout). Forcing 120 here ignored user picks like 60,
        // left [PreviewController.desiredFps] ≥ 120, and blocked RAW/DNG ([canCaptureRawStill] requires < 120).
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
    LaunchedEffect(selectedCameraId, sweepJob, adbAutomationVideoFps) {
        if (sweepJob != null) return@LaunchedEffect
        if (adbAutomationVideoFps != null && adbAutomationVideoFps >= 120) return@LaunchedEffect
        val cam = selectedCameraId ?: return@LaunchedEffect
        val clamped =
            PreviewFpsSupport.clampFpsToAchievableWithoutRoot(context.applicationContext, cam, selectedFps)
        if (clamped != selectedFps) {
            Log.i("PNS.ChromeUx", "fpsClampForLens cameraId=$cam $selectedFps -> $clamped")
            selectedFps = clamped
        }
    }
    LaunchedEffect(selectedCameraId) {
        val id = selectedCameraId ?: return@LaunchedEffect
        PreviewChromePreferences.saveLastRearCameraIdIfRear(context.applicationContext, id)
    }
    val deviceUiRotationState = rememberDeviceUiRotationState()
    val latestPhysicalCardinalSnap = rememberUpdatedState(deviceUiRotationState.physicalCardinalSnapDegrees)
    val stillsLutLatest = rememberUpdatedState(hudState.current.stillsLut())
    val compositionGuide = rememberCompositionGuideSettings()

    LaunchedEffect(adbPreviewStillsLutName) {
        val name = adbPreviewStillsLutName?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val lut =
            LutCatalog.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: run {
                    hudState.update(hudState.current.copy(selectedLutForStills = LutCatalog.None.name))
                    return@LaunchedEffect
                }
        hudState.update(hudState.current.copy(selectedLutForStills = lut.name))
        PnsAdbLog.i(context, "preview seeded stillsLut=${lut.name}")
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
            PnsAdbLog.i(context, "m6 lutFpsBaseline fps=${"%.2f".format(baseline)} lut=None")
            hudState.update(hudState.current.copy(selectedLutForStills = LutCatalog.PnsCinematic.name))
            delay(3500)
            val withLut = controller.measuredFps()
            PnsAdbLog.i(
                context,
                "m6 lutFpsWithLut fps=${"%.2f".format(withLut)} lut=PnsCinematic",
            )
            val drop =
                if (baseline > 1.0) ((baseline - withLut) / baseline) * 100.0 else 0.0
            val ok = drop <= 5.0
            PnsAdbLog.i(
                context,
                "m6 lutFpsBudget ok=$ok baseline=${"%.2f".format(baseline)} withLut=${"%.2f".format(withLut)} dropPercent=${"%.1f".format(drop)}",
            )
        } finally {
            hudState.update(hudState.current.copy(selectedLutForStills = prev))
        }
    }
    val chromePrefs = rememberPreviewChromePreferences()
    LaunchedEffect(chromePrefs.current.btRemoteShutter, chromePrefs.current.volumeKeysCapture) {
        PnsMediaSessionManager.btRemoteShutterEnabled =
            chromePrefs.current.btRemoteShutter && chromePrefs.current.volumeKeysCapture
    }
    LaunchedEffect(context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FleetCameraStartupScan.runIfNeeded(context.applicationContext)
        }
    }
    LaunchedEffect(adbAutomationVideoAv1, adbAutomationVideoCodecOrdinal) {
        if (adbAutomationVideoAv1 || adbAutomationVideoCodecOrdinal == VideoCodec.AV1.ordinal) {
            val c = chromePrefs.current
            chromePrefs.update(
                c.copy(
                    inAppVideoCodecOrdinal = VideoCodec.AV1.ordinal,
                    inAppVideoEncodeWidth = if (c.inAppVideoEncodeWidth > 0) c.inAppVideoEncodeWidth else 1920,
                    inAppVideoEncodeHeight = if (c.inAppVideoEncodeHeight > 0) c.inAppVideoEncodeHeight else 1080,
                    inAppVideoFps = if (c.inAppVideoFps > 0) c.inAppVideoFps else 60,
                ),
            )
            PnsAdbLog.i(context, "preview seeded videoCodec=AV1 (adb)")
        }
        adbAutomationVideoCodecOrdinal?.let { ord ->
            if (VideoCodec.entries.getOrNull(ord) != null) {
                chromePrefs.update(chromePrefs.current.copy(inAppVideoCodecOrdinal = ord))
            }
        }
    }
    LaunchedEffect(adbAutomationVideoStabilization) {
        controller.adbAutomationVideoStabilization = adbAutomationVideoStabilization
        if (adbAutomationVideoStabilization) {
            PnsAdbLog.i(context, "preview seeded videoStabilization=true (adb)")
        }
    }
    LaunchedEffect(adbAutomationVideoAv1) {
        controller.adbAutomationVideoAv1 = adbAutomationVideoAv1
    }
    LaunchedEffect(adbAutomationVideoCodecOrdinal) {
        controller.adbAutomationVideoCodecOrdinal = adbAutomationVideoCodecOrdinal
    }
    LaunchedEffect(adbEyeAfOverlaySeed) {
        val want = adbEyeAfOverlaySeed ?: return@LaunchedEffect
        controller.setHudFaceOverlayEnabled(want)
        PnsAdbLog.i(context, "preview seeded eyeAfOverlay=$want (adb)")
    }
    LaunchedEffect(
        selectedFps,
        primaryPhoto,
        adbAutomationVideoTenBit,
        adbAutomationVideoDcg,
        adbAutomationVideoFps,
        chromePrefs.current.inAppVideoCodecOrdinal,
        adbAutomationVideoAv1,
    ) {
        val wantsAv1 =
            adbAutomationVideoAv1 ||
                chromePrefs.current.inAppVideoCodecOrdinal == VideoCodec.AV1.ordinal
        val wantsHevcSdrMc =
            chromePrefs.current.inAppVideoCodecOrdinal == VideoCodec.H265.ordinal ||
                adbAutomationVideoCodecOrdinal == VideoCodec.H265.ordinal
        val automationHfrFps = adbAutomationVideoFps?.takeIf { it >= 120 } ?: 0
        val mcHintFps = maxOf(selectedFps, automationHfrFps)
        val wantsMc =
            !primaryPhoto &&
                (
                    mcHintFps >= 120 ||
                        adbAutomationVideoTenBit ||
                        adbAutomationVideoDcg ||
                        wantsAv1 ||
                        wantsHevcSdrMc
                    )
        controller.hintInAppVideoMediaCodecPath(wantsMc)
    }
    LaunchedEffect(adbAutomationVideoEncodeW, adbAutomationVideoEncodeH) {
        val w = adbAutomationVideoEncodeW ?: return@LaunchedEffect
        val h = adbAutomationVideoEncodeH ?: return@LaunchedEffect
        if (w <= 0 || h <= 0) return@LaunchedEffect
        val c = chromePrefs.current
        chromePrefs.update(c.copy(inAppVideoEncodeWidth = w, inAppVideoEncodeHeight = h))
        controller.setInAppVideoEncodeSize(Size(w, h))
        PnsAdbLog.i(context, "preview seeded videoEncode=${w}x$h (adb)")
    }
    var composedStillIntent by remember(adbInitialImagingProfile) {
        mutableStateOf(
            runCatching {
                listOf(ImagingProfile.StandardPro, ImagingProfile.UltraMax, ImagingProfile.JpegOnly)
                val companionSeed = PreviewChromePreferences.load(context.applicationContext).stillCaptureJpegCompanion
                if (adbInitialImagingProfile != null) {
                    adbInitialImagingProfile!!.id
                    ComposedStillIntent.fromLegacyImagingProfile(adbInitialImagingProfile!!, jpegCompanionOn = true)
                } else {
                    HudSettings.loadImagingProfile(context).id
                    HudSettings.loadComposedStillIntent(context, companionSeed)
                }
            }.getOrElse { ComposedStillIntent.default() },
        )
    }
    val imagingProfile: ImagingProfile = composedStillIntent.storageProfile()
    val imagingProfileState = rememberUpdatedState(imagingProfile)
    LaunchedEffect(adbJpegCompanionSeed) {
        val want = adbJpegCompanionSeed ?: return@LaunchedEffect
        val cur = chromePrefs.current
        if (cur.stillCaptureJpegCompanion != want) {
            chromePrefs.applySessionOnly(cur.copy(stillCaptureJpegCompanion = want))
        }
        PnsAdbLog.i(context, "preview seeded stillCaptureJpegCompanion=$want (session-only)")
        if (composedStillIntent.raw != ImgMenuTier.Off) {
            composedStillIntent =
                composedStillIntent
                    .copy(
                        jpeg = if (want) composedStillIntent.raw else ImgMenuTier.Off,
                        hdrWhenJpegOff = composedStillIntent.raw,
                    ).coerceNoOffOff()
        }
    }
    LaunchedEffect(adbAudioHiFiSeed, adbAudioWindSeed) {
        if (adbAudioHiFiSeed == null && adbAudioWindSeed == null) return@LaunchedEffect
        val cur = chromePrefs.current
        var next = cur
        adbAudioHiFiSeed?.let { want ->
            if (cur.audioHiFiCapture != want) next = next.copy(audioHiFiCapture = want)
        }
        adbAudioWindSeed?.let { want ->
            if (cur.audioWindNoiseReduction != want) next = next.copy(audioWindNoiseReduction = want)
        }
        if (next != cur) {
            chromePrefs.applySessionOnly(next)
        }
        PnsAdbLog.i(
            context,
            "preview seeded audio hiFi=${next.audioHiFiCapture} windNs=${next.audioWindNoiseReduction} (session-only)",
        )
    }
    LaunchedEffect(adbShutterSoundPackSeed) {
        val packKey = adbShutterSoundPackSeed ?: return@LaunchedEffect
        val resolved = ShutterSoundPack.fromStorageKey(packKey).storageKey
        val cur = chromePrefs.current
        if (cur.shutterSoundPackKey != resolved) {
            chromePrefs.applySessionOnly(cur.copy(shutterSoundPackKey = resolved))
        }
        PnsAdbLog.i(context, "preview seeded shutterSoundPack=$resolved raw=$packKey (session-only)")
    }
    LaunchedEffect(imageCaptureReturn) {
        if (imageCaptureReturn != null) {
            val c = chromePrefs.current
            if (!c.stillCaptureJpegCompanion) {
                chromePrefs.update(c.copy(stillCaptureJpegCompanion = true))
            }
            if (composedStillIntent.raw != ImgMenuTier.Off && composedStillIntent.jpeg == ImgMenuTier.Off) {
                composedStillIntent =
                    composedStillIntent.copy(
                        jpeg = composedStillIntent.raw,
                        hdrWhenJpegOff = composedStillIntent.raw,
                    )
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, captureScope) {
        val profiler = MemoryProfiler.getInstance(context.applicationContext, captureScope)
        var stopped = false
        fun stopProfilerOnce() {
            if (stopped) return
            stopped = true
            profiler.logEvent("preview_session_stop")
            val report = profiler.stopProfiling()
            runCatching {
                profiler.saveReportToFile(report, "preview_engine_last.csv")
            }
            PnsBitmapGuard.logLeakCheck("PreviewEngine")
        }
        profiler.startProfiling(intervalMs = 10_000L)
        profiler.logEvent("preview_session_start")
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    stopProfilerOnce()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopProfilerOnce()
        }
    }
    var previewNeedsResumeKick by remember { mutableStateOf(false) }
    val pendingHardRestartAfterExternalGallery = remember { AtomicBoolean(false) }
    DisposableEffect(lifecycleOwner, controller, previewHostSlot, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        previewNeedsResumeKick = true
                        PreviewLongRunningPause.setPaused(true)
                        controller.lifecycleBackgroundPaused = true
                        Log.i(
                            "PNS.PowerThermal",
                            "longRunningPaused=true backgroundYuv=false sweepContinuesOnResume=true",
                        )
                        controller.drainCompanionJpegExecutor(timeoutMs = 2000L)
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        val wasLongRunningPaused = PreviewLongRunningPause.paused
                        PreviewLongRunningPause.setPaused(false)
                        controller.lifecycleBackgroundPaused = false
                        if (wasLongRunningPaused) {
                            Log.i("PNS.PowerThermal", "longRunningPaused=false")
                            controller.kickPreviewPipelineRestart()
                        }
                        if (pendingHardRestartAfterExternalGallery.compareAndSet(true, false)) {
                            previewNeedsResumeKick = false
                            val act = context.findHostActivity()
                            if (act != null) {
                                restartMainActivityCold(act)
                            }
                            return@LifecycleEventObserver
                        }
                        if (previewNeedsResumeKick) {
                            previewNeedsResumeKick = false
                            controller.kickPreviewPipelineRestart()
                            // Defer layout to the next UI message so GLSurfaceView.onResume() / window
                            // pass first (Camera / SurfaceView pattern). Triggers OnLayoutChangeListener
                            // → setGeometry without a second writer. See AGENTS.md — GLES preview aspect.
                            previewHostSlot.view?.post {
                                val v = previewHostSlot.view
                                v?.requestLayout()
                                v?.invalidate()
                            }
                        }
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(adbSelfTimerSecSanitized) {
        if (adbSelfTimerSecSanitized != null) {
            val norm = PreviewChromePreferences.normalizeSelfTimerDelaySec(adbSelfTimerSecSanitized)
            val cur = chromePrefs.current
            if (cur.selfTimerDelaySec != norm) {
                // Do not persist: automation `pns_preview_self_timer_sec` would otherwise stick in prefs.
                chromePrefs.applySessionOnly(cur.copy(selfTimerDelaySec = norm))
            }
            PnsAdbLog.i(
                context,
                "preview adb seed selfTimerDelaySec=$norm raw=$adbInitialSelfTimerSec",
            )
            Log.i("PNS.ChromeUx", "selfTimerSec=$norm")
        } else {
            Log.i("PNS.ChromeUx", "selfTimerSec=${chromePrefs.current.selfTimerDelaySec}")
        }
    }

    val previewAutomationExtras =
        adbSequentialRawStills > 0 ||
            adbBracketPattern != null ||
            adbInitialDial != null ||
            !adbSeedCameraId.isNullOrBlank() ||
            adbSuperMacroProbe ||
            adbM6FpsLutProbe ||
            adbCalibrateGrabSmoke ||
            adbInitialSelfTimerSec != null ||
            adbFocalMmSlotProbe != null ||
            !adbPreviewStillsLutName.isNullOrBlank() ||
            adbInitialImagingProfile != null ||
            adbRawStreamPreference != null ||
            adbJpegCompanionSeed != null

    LaunchedEffect(previewAutomationExtras, snackbarHostState) {
        if (previewAutomationExtras || snackbarHostState == null) return@LaunchedEffect
        if (PnsUiHintsStore.hasSeenImmersiveGestureTip(context.applicationContext)) return@LaunchedEffect
        delay(1400)
        snackbarHostState.showSnackbar(
            message = "Swipe from the screen edge when you need Back or Home.",
            duration = SnackbarDuration.Long,
        )
        PnsUiHintsStore.markImmersiveGestureTipSeen(context.applicationContext)
    }

    var isRecording by remember { mutableStateOf(false) }
    val trayStillCaptureRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    /** Latest indexed capture for gallery thumb + open-in-viewer (typically DNG URI). */
    var lastGalleryUri by remember { mutableStateOf<Uri?>(null) }
    /** Controls bespoke gallery visibility (restored from [PreviewLastSurfacePrefs] on cold start). */
    var showBespokeGallery by rememberSaveable(resolvedInitialGallery) {
        mutableStateOf(resolvedInitialGallery)
    }

    LaunchedEffect(primaryPhoto, showBespokeGallery) {
        val surface =
            when {
                showBespokeGallery -> PreviewLastSurface.Gallery
                primaryPhoto -> PreviewLastSurface.Photo
                else -> PreviewLastSurface.Video
            }
        PreviewLastSurfacePrefs.save(appContext, surface)
    }

    LaunchedEffect(hudState.current.enableSmileTriggeredStill, primaryPhoto, isRecording, sweepJob) {
        controller.setSmileStillEnabled(hudState.current.enableSmileTriggeredStill)
    }

    /** BUILD_PLAN §3 digital crops: wide `2` → 35/50mm; tele `4` → 85/150mm; `null` = native FOV. */
    var focalCrop by remember { mutableStateOf<FocalMode?>(null) }

    LaunchedEffect(focalCrop) {
        controller.setFocalCrop(focalCrop)
    }

    LaunchedEffect(selectedCameraId, focalCrop, cameraRoles) {
        val sid = selectedCameraId ?: return@LaunchedEffect
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager
        val phys =
            runCatching {
                cm.getCameraCharacteristics(sid).physicalCameraIds?.toSet().orEmpty()
            }.getOrDefault(emptySet())
        val teleId = cameraRoles.tele
        val longTeleId = cameraRoles.longTele
        val wideId = cameraRoles.wide
        val uwId = cameraRoles.ultraWide
        val containsTele = teleId != null && phys.contains(teleId)
        val containsLongTele = longTeleId != null && phys.contains(longTeleId)
        val containsWide = wideId != null && phys.contains(wideId)
        // Tele slots can resolve to the logical parent id while preview is pinned to the tele
        // physical stream — the old `sid == tele` branch alone cleared LongTele150 and caused
        // restart churn / HAL disconnect on some OEM logical cameras.
        val clamped =
            when {
                sid == uwId -> null
                containsTele && containsWide ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 ||
                            it == FocalMode.Standard50 ||
                            it == FocalMode.Portrait85 ||
                            it == FocalMode.LongTele150
                    }
                sid == wideId ->
                    when {
                        teleId != null &&
                            phys.contains(teleId) &&
                            (focalCrop == FocalMode.Portrait85 || focalCrop == FocalMode.LongTele150) ->
                            focalCrop
                        else ->
                            focalCrop?.takeIf {
                                it == FocalMode.Street35 || it == FocalMode.Standard50
                            }
                    }
                sid == teleId ->
                    focalCrop?.takeIf {
                        it == FocalMode.Portrait85 || it == FocalMode.LongTele150
                    }
                containsLongTele && !containsTele ->
                    focalCrop?.takeIf { it == FocalMode.LongTele150 }
                containsTele ->
                    focalCrop?.takeIf {
                        it == FocalMode.Portrait85 || it == FocalMode.LongTele150
                    }
                containsWide ->
                    focalCrop?.takeIf {
                        it == FocalMode.Street35 || it == FocalMode.Standard50
                    }
                else -> null
            }
        if (clamped != focalCrop) {
            focalCrop = clamped
        }
    }

    var focalSlotProbeConsumed by remember(adbFocalMmSlotProbe) { mutableStateOf(false) }

    LaunchedEffect(
        controller,
        adbFocalMmSlotProbe,
        focalSlotProbeConsumed,
        cameraIdsStableKey,
    ) {
        val slot = adbFocalMmSlotProbe ?: return@LaunchedEffect
        if (focalSlotProbeConsumed) return@LaunchedEffect
        var w = 0
        while (w < FOCAL_SLOT_PROBE_EMPTY_IDS_WAIT_LOOPS && controller.cameraIds().isEmpty()) {
            delay(50)
            w++
        }
        if (controller.cameraIds().isEmpty()) {
            Log.w("PNS.ChromeUx", "focalSlotTap=mm=${slot.labelMm} skipped=no_camera_ids")
            focalSlotProbeConsumed = true
            return@LaunchedEffect
        }
        var w2 = 0
        while (w2 < FOCAL_SLOT_PROBE_NULL_CAMERA_WAIT_LOOPS && selectedCameraId == null) {
            delay(50)
            w2++
        }
        if (selectedCameraId == null) {
            Log.w("PNS.ChromeUx", "focalSlotTap=mm=${slot.labelMm} skipped=no_seed_camera")
            focalSlotProbeConsumed = true
            return@LaunchedEffect
        }
        var seedSettle = 0
        while (seedSettle < FOCAL_SLOT_PROBE_SEED_SESSION_LOOPS && !controller.canCaptureStill()) {
            delay(50)
            seedSettle++
        }
        delay(FOCAL_SLOT_PROBE_POST_SEED_MS)
        val ids = controller.cameraIds()
        val before = selectedCameraId
        val pair =
            resolveFocalMmSlot(context.applicationContext, slot, ids)
                ?: run {
                    Log.i("PNS.ChromeUx", "focalSlotTap=mm=${slot.labelMm} skipped=no_mapping")
                    focalSlotProbeConsumed = true
                    return@LaunchedEffect
                }
        schedulePreviewPhysicalForFocalSlot(context.applicationContext, controller, slot, pair, ids)
        selectedCameraId = pair.first
        focalCrop = pair.second
        Log.i(
            "PNS.ChromeUx",
            "focalSlotTap=mm=${slot.labelMm} cameraIdBefore=$before cameraIdAfter=${pair.first} focalCrop=${pair.second?.name ?: "native"}",
        )
        var postSwitch = 0
        while (postSwitch < FOCAL_SLOT_PROBE_POST_SWITCH_SETTLE_LOOPS) {
            if (selectedCameraId == pair.first && controller.canCaptureStill()) break
            delay(50)
            postSwitch++
        }
        if (!controller.canCaptureStill()) {
            Log.w(
                "PNS.ChromeUx",
                "focalSlotTap=mm=${slot.labelMm} session not ready after switch target=${pair.first} " +
                    "reason=${controller.rawStillNotReadyReason() ?: "unknown"}",
            )
        }
        focalSlotProbeConsumed = true
    }

    // Sprint **15.10** ADB proof: lock shutter so ISO chase emits `readoutChase iso=...` logs.
    var readoutShutterProbeConsumed by remember(adbReadoutShutterNsProbe) { mutableStateOf(false) }
    LaunchedEffect(
        controller,
        adbReadoutShutterNsProbe,
        readoutShutterProbeConsumed,
    ) {
        val shutterNs = adbReadoutShutterNsProbe ?: return@LaunchedEffect
        if (readoutShutterProbeConsumed) return@LaunchedEffect
        controller.setReadoutManualShutter(shutterNs)
        controller.adbReadoutChaseProof = true
        readoutShutterProbeConsumed = true
        PnsAdbLog.i(context, "preview seeded readoutShutterNs=$shutterNs (adb)")
    }

    // Keep [PreviewController] session outputs aligned with IMG tiers before any shutter tap (LaunchedEffect
    // alone races tray capture → missing JPEG surface → generic save failure).
    SideEffect {
        controller.setComposedCapturePlan(composedStillIntent.resolveCapturePlan())
    }

    LaunchedEffect(composedStillIntent) {
        HudSettings.saveComposedStillIntent(context.applicationContext, composedStillIntent)
        val wantTonal = composedStillIntent.wantsTonalStill()
        val c = chromePrefs.current
        if (c.stillCaptureJpegCompanion != wantTonal) {
            chromePrefs.update(c.copy(stillCaptureJpegCompanion = wantTonal))
        }
        syncHudJpegEncodeFromImgMenu(composedStillIntent, hudState)
    }

    LaunchedEffect(composedStillIntent.raw) {
        when (composedStillIntent.raw) {
            ImgMenuTier.Standard ->
                HudSettings.saveLastRawImagingProfileId(context.applicationContext, ImagingProfile.StandardPro.id)
            ImgMenuTier.Ultra ->
                HudSettings.saveLastRawImagingProfileId(context.applicationContext, ImagingProfile.UltraMax.id)
            ImgMenuTier.Off -> Unit
        }
    }

    /** One snackbar per rear “RAW probe” id when we auto-fall back from DNG profiles on no-RAW hardware. */
    val jpegOnlyCoerceNotified = remember { mutableSetOf<String>() }
    LaunchedEffect(selectedCameraId, composedStillIntent.raw, adbInitialImagingProfile, cameraIdsStableKey) {
        if (adbInitialImagingProfile != null) return@LaunchedEffect
        delay(120)
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager
        val ids = controller.cameraIds()
        if (ids.isEmpty()) return@LaunchedEffect
        val rearRawProbeId =
            BackCameraRoleResolver.resolve(cm, ids).wide
                ?: ids.firstOrNull { it != "1" }
                ?: return@LaunchedEffect
        val caps = HardwareCapsSnapshot.build(cm, rearRawProbeId, ids)
        if (caps.hasRawCapability) return@LaunchedEffect
        when (composedStillIntent.raw) {
            ImgMenuTier.Ultra,
            ImgMenuTier.Standard,
            -> {
                composedStillIntent =
                    ComposedStillIntent(
                        raw = ImgMenuTier.Off,
                        jpeg = ImgMenuTier.Standard,
                        hdrWhenJpegOff = ImgMenuTier.Standard,
                    )
                HudSettings.saveImagingProfile(context, ImagingProfile.JpegOnly)
                if (jpegOnlyCoerceNotified.add(rearRawProbeId)) {
                    captureScope.pnsShowSnackbar(
                        snackbarHostState,
                        "This device has no rear RAW output — switched to JPG.",
                    )
                }
            }
            ImgMenuTier.Off -> Unit
        }
    }
    val selectedCameraIdState = rememberUpdatedState(selectedCameraId)
    val haptics = remember { CaptureHaptics(context.applicationContext) }
    val composedStillIntentState = rememberUpdatedState(composedStillIntent)
    val stillsLutState = rememberUpdatedState(hudState.current.stillsLut())
    val imageCaptureReturnState = rememberUpdatedState(imageCaptureReturn)

    fun applyStillResultToGalleryThumb(result: Result<RawStillSaveSuccess>) {
        result.fold(
            onSuccess = { out ->
                val dngUri = runCatching { Uri.parse(out.dngUriString) }.getOrNull()
                val tonalUri =
                    out.tonalUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
                val pick = tonalUri ?: dngUri
                if (pick != null) {
                    lastGalleryUri = pick
                    Log.i("PNS.ChromeUx", "galleryThumbUpdated path=${pick.lastPathSegment}")
                }
                dngUri?.let { CloudCaptureBackup.queueUri(appContext, it) }
                if (tonalUri != null && tonalUri != dngUri) {
                    CloudCaptureBackup.queueUri(appContext, tonalUri)
                }
            },
            onFailure = { },
        )
    }

    LaunchedEffect(Unit) {
        lateinit var runTrayStillCaptureImpl: () -> Unit
        lateinit var runTrayStillBurstImpl: () -> Unit
        fun scheduleTrayStillCapture() {
            controller.runAfterAfShutterGateIfNeeded(
                onTimeoutOnMain = {
                    captureScope.pnsShowSnackbar(
                        snackbarHostState,
                        "Focus wait timed out — photo not taken.",
                        longDuration = false,
                    )
                },
                runCaptureOnMain = { runTrayStillCaptureImpl() },
            )
        }
        fun scheduleTrayStillBurstCapture() {
            controller.runAfterAfShutterGateIfNeeded(
                onTimeoutOnMain = {
                    captureScope.pnsShowSnackbar(
                        snackbarHostState,
                        "Focus wait timed out — burst not started.",
                        longDuration = false,
                    )
                },
                runCaptureOnMain = { runTrayStillBurstImpl() },
            )
        }
        runTrayStillCaptureImpl = {
            val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
            val plan = composedStillIntentState.value.resolveCapturePlan()
            controller.setComposedCapturePlan(plan)
            val blocked = controller.composedCaptureBlockedReason(plan)
            if (blocked != null) {
                captureScope.pnsShowSnackbar(
                    snackbarHostState,
                    blocked,
                    longDuration = true,
                )
            } else {
                val ic = imageCaptureReturnState.value
                controller.captureComposedStill(
                    appContext = context.applicationContext,
                    plan = plan,
                    haptics = haptics,
                    surfaceRotation = rot,
                    dngSoftwareDescription =
                        formatDngSoftwareLine(context, stillsLutState.value),
                    stillsLut = stillsLutState.value,
                    onTonalReady =
                        ic?.let { returnContract ->
                            { tonalUri ->
                                captureScope.launch {
                                    deliverImageCaptureToCaller(returnContract, tonalUri)
                                }
                            }
                        },
                    onResult = { result ->
                        applyStillResultToGalleryThumb(result)
                        result.onFailure { e ->
                            val retryable = PnsUserFacingErrors.shouldOfferRetryAfterStillFailure(e)
                            captureScope.pnsShowSnackbar(
                                snackbarHostState,
                                PnsUserFacingErrors.stillCaptureFailure(e),
                                clipboardDetail =
                                    if (retryable) {
                                        null
                                    } else {
                                        PnsUserFacingErrors.technicalDetailForCopy(e)
                                    },
                                clipboardAppContext =
                                    if (retryable) null else context.applicationContext,
                                onRetry =
                                    if (retryable) {
                                        { scheduleTrayStillCapture() }
                                    } else {
                                        null
                                    },
                            )
                            if (!retryable) {
                                ic?.let { contract ->
                                    contract.host.setResult(Activity.RESULT_CANCELED)
                                    contract.host.finish()
                                }
                            }
                        }
                    },
                )
            }
        }
        PnsMediaSessionManager.onRemoteShutter = { scheduleTrayStillCapture() }
        runTrayStillBurstImpl = {
            val hud = hudState.current
            val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
            val plan = composedStillIntentState.value.resolveCapturePlan()
            controller.setComposedCapturePlan(plan)
            val blocked = controller.composedCaptureBlockedReason(plan)
            if (blocked != null) {
                captureScope.pnsShowSnackbar(snackbarHostState, blocked, longDuration = true)
            } else {
                controller.captureComposedStillBurst(
                    appContext = context.applicationContext,
                    plan = plan,
                    haptics = haptics,
                    surfaceRotation = rot,
                    shotCount = hud.burstShotCount,
                    intervalMs = hud.burstIntervalMs.toLong(),
                    dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutState.value),
                    stillsLut = stillsLutState.value,
                    onResult = { result ->
                        result.fold(
                            onSuccess = { n ->
                                captureScope.pnsShowSnackbar(
                                    snackbarHostState,
                                    "Burst saved $n/${hud.burstShotCount}",
                                    longDuration = false,
                                )
                            },
                            onFailure = { e ->
                                captureScope.pnsShowSnackbar(
                                    snackbarHostState,
                                    PnsUserFacingErrors.stillCaptureFailure(e),
                                    longDuration = true,
                                )
                            },
                        )
                    },
                )
            }
        }
        trayStillCaptureRef.value = {
            if (hudState.current.burstModeEnabled) {
                scheduleTrayStillBurstCapture()
            } else {
                scheduleTrayStillCapture()
            }
        }
    }

    LaunchedEffect(hudState.current.preCaptureBufferEnabled) {
        controller.refreshPreCaptureRingFromHud()
    }

    LaunchedEffect(
        primaryPhoto,
        hudState.current.intervalometerRunning,
        hudState.current.intervalometerIntervalSec,
        isRecording,
    ) {
        if (
            !primaryPhoto ||
            isRecording ||
            !hudState.current.intervalometerRunning ||
            hudState.current.intervalometerIntervalSec <= 0
        ) {
            return@LaunchedEffect
        }
        val intervalSec = hudState.current.intervalometerIntervalSec
        Log.i("PNS.ChromeUx", "intervalometer active intervalSec=$intervalSec")
        while (
            primaryPhoto &&
            !isRecording &&
            hudState.current.intervalometerRunning &&
            hudState.current.intervalometerIntervalSec == intervalSec
        ) {
            delay(intervalSec * 1000L)
            if (
                !primaryPhoto ||
                isRecording ||
                !hudState.current.intervalometerRunning
            ) {
                break
            }
            trayStillCaptureRef.value?.invoke()
        }
    }

    val tetherServer = remember { TetheredCaptureServer() }
    DisposableEffect(Unit) {
        onDispose { ProCapture.stopTether(tetherServer) }
    }
    val primaryPhotoForTether = rememberUpdatedState(primaryPhoto)
    val selectedCameraIdForTether = rememberUpdatedState(selectedCameraId)
    val selectedFpsForTether = rememberUpdatedState(selectedFps)
    LaunchedEffect(hudState.current.tetheredCaptureEnabled, adbTetherEnabled) {
        val want = hudState.current.tetheredCaptureEnabled || adbTetherEnabled
        if (!want) {
            ProCapture.stopTether(tetherServer)
            return@LaunchedEffect
        }
        if (adbTetherEnabled && !hudState.current.tetheredCaptureEnabled) {
            hudState.update(hudState.current.copy(tetheredCaptureEnabled = true))
        }
        val bridge =
            object : ProCapture.PreviewControllerBridge {
                override fun canCaptureStill(): Boolean = controller.canCaptureStill()

                override fun primaryPhoto(): Boolean = primaryPhotoForTether.value

                override fun selectedCameraId(): String? = selectedCameraIdForTether.value

                override fun selectedFps(): Int = selectedFpsForTether.value

                override fun previewFlashMode(): PreviewFlashMode = chromePrefs.current.previewFlashMode
            }
        ProCapture.stopTether(tetherServer)
        ProCapture.bindTetherCallbacks(
            tetherServer,
            bridge,
            onStillCapture = { trayStillCaptureRef.value?.invoke() },
            onFlashMode = { mode ->
                chromePrefs.update(chromePrefs.current.copy(previewFlashMode = mode))
            },
        )
        ProCapture.startTether(tetherServer)
        PnsAdbLog.i(context, "tetherServer active port=${TetheredCaptureServer.DEFAULT_PORT}")
    }

    LaunchedEffect(hudState.current.previewFlashStrengthPercent) {
        controller.setPreviewFlashStrengthPercent(hudState.current.previewFlashStrengthPercent)
    }

    LaunchedEffect(adbFlashStrengthPercent) {
        val pct = adbFlashStrengthPercent ?: return@LaunchedEffect
        val clamped =
            pct.coerceIn(
                HudSettings.PREVIEW_FLASH_STRENGTH_MIN,
                HudSettings.PREVIEW_FLASH_STRENGTH_MAX,
            )
        hudState.update(hudState.current.copy(previewFlashStrengthPercent = clamped))
        controller.setPreviewFlashStrengthPercent(clamped)
        PnsAdbLog.i(context, "preview seeded flashStrengthPercent=$clamped (adb)")
    }

    LaunchedEffect(adbPictureProfileId) {
        val raw = adbPictureProfileId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val profile =
            ProPictureProfiles.byId(raw) ?: run {
                PnsAdbLog.e(context, "pictureProfile unknown id=$raw")
                return@LaunchedEffect
            }
        ProCapture.applyPictureProfile(
            context,
            profile,
            hudState,
            onImagingProfile = { imaging ->
                composedStillIntent =
                    ComposedStillIntent.fromLegacyImagingProfile(imaging, jpegCompanionOn = true)
                HudSettings.saveImagingProfile(context, imaging)
                controller.kickPreviewPipelineRestart()
            },
        )
        PnsAdbLog.i(context, "preview seeded pictureProfile=${profile.id} (adb)")
    }

    LaunchedEffect(adbCalExportSmoke) {
        if (!adbCalExportSmoke) return@LaunchedEffect
        delay(2_000)
        val exported = ColorCalibrationTools.exportLatestProfile(context)
        if (exported != null) {
            PnsAdbLog.i(
                context,
                "colorCal export ok path=${exported.file.name} target=${exported.profile.targetId}",
            )
        } else {
            PnsAdbLog.e(context, "colorCal export failed: no saved profile")
        }
    }

    LaunchedEffect(adbPreviewThemeMode) {
        val mode = adbPreviewThemeMode ?: return@LaunchedEffect
        UxSettings.saveThemeMode(context, mode)
        onThemeModeChange(mode)
        PnsAdbLog.i(context, "preview seeded themeMode=${mode.name} (adb)")
    }

    LaunchedEffect(adbWorkflowPresetId) {
        val raw = adbWorkflowPresetId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val preset =
            WorkflowPresets.byId(context, raw) ?: run {
                PnsAdbLog.e(context, "workflowPreset unknown id=$raw")
                return@LaunchedEffect
            }
        WorkflowPresets.logApplied(context, preset)
        HudSettings.saveCommandDialMode(context, preset.commandDialMode)
        val profile = ImagingProfile.byId(preset.imagingProfileId)
        composedStillIntent =
            ComposedStillIntent.fromLegacyImagingProfile(profile, jpegCompanionOn = true)
        HudSettings.saveImagingProfile(context, profile)
        primaryPhoto = preset.primaryPhoto
        preset.fps?.let { fps ->
            selectedFps = fps
            userSelectedFps = fps
        }
        controller.kickPreviewPipelineRestart()
    }

    rememberNavigationUxSnapshot(immersiveSystemBarsHidden = true)

    BackHandler(enabled = showBespokeGallery) {
        PnsAdbLog.i(context, "navBack previewGalleryClosed")
        showBespokeGallery = false
    }

    LaunchedEffect(adbOpenGallery) {
        if (!adbOpenGallery) return@LaunchedEffect
        showBespokeGallery = true
        PnsAdbLog.i(context, "preview openGallery=true (adb)")
    }

    LaunchedEffect(adbCloudBackupEnabled) {
        if (!adbCloudBackupEnabled) return@LaunchedEffect
        CloudCaptureBackup.setEnabled(context, true)
        PnsAdbLog.i(context, "preview seeded cloudBackup enabled=true (adb)")
    }

    LaunchedEffect(adbCloudBackupSyncNow, adbCloudBackupProbe) {
        if (!adbCloudBackupSyncNow) return@LaunchedEffect
        if (!CloudCaptureBackup.isEnabled(context) && !adbCloudBackupProbe) return@LaunchedEffect
        if (adbCloudBackupProbe) CloudCaptureBackup.setEnabled(context, true)
        val result =
            CloudCaptureBackup.syncRecentCaptures(
                context,
                maxItems = 12,
                allowProbeDir = adbCloudBackupProbe,
            )
        PnsAdbLog.i(
            context,
            "cloudBackup syncDone copied=${result.copied} skipped=${result.skipped} failed=${result.failed}",
        )
    }

    LaunchedEffect(adbPlatformFileProviderProbe) {
        if (!adbPlatformFileProviderProbe) return@LaunchedEffect
        SharingManager.probeFileProvider(context)
    }

    LaunchedEffect(adbPlatformWidgetProbe) {
        if (!adbPlatformWidgetProbe) return@LaunchedEffect
        PlatformIntegration.logWidgetProbe(context)
        ExternalApps.logInstalledViewers(context)
    }

    LaunchedEffect(adbPlatformShareProbe) {
        if (!adbPlatformShareProbe) return@LaunchedEffect
        val items = PnsMediaStoreGallery.loadIndex(context, maxItems = 3)
        val first = items.firstOrNull() ?: return@LaunchedEffect
        SharingManager.shareSingle(context, first.uri, "Platform share probe")
    }

    val lanMediaServer = remember { LanMediaTransferServer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { lanMediaServer.stop() }
    }
    LaunchedEffect(adbLanTransfer, adbLanTransferProbe) {
        val want =
            adbLanTransfer ||
                adbLanTransferProbe ||
                PnsConnectivity.isLanTransferEnabled(context)
        if (!want) {
            lanMediaServer.stop()
            return@LaunchedEffect
        }
        if (adbLanTransfer || adbLanTransferProbe) {
            PnsConnectivity.setLanTransferEnabled(context, true)
        }
        lanMediaServer.fileProvider = {
            PnsMediaStoreGallery.loadIndex(context, maxItems = 24).map { item ->
                LanMediaTransferServer.FileEntry(
                    id = item.uri.lastPathSegment?.toLongOrNull() ?: item.uri.hashCode().toLong(),
                    uri = item.uri,
                    name = item.displayName,
                    mime = item.mimeType,
                    size = item.size,
                )
            }
        }
        if (!lanMediaServer.isListening()) {
            lanMediaServer.start()
            kotlinx.coroutines.delay(600)
        }
        if (lanMediaServer.isListening()) {
            PnsAdbLog.i(context, "connectivity lanServer active port=${lanMediaServer.boundPort}")
        }
        if (adbLanTransferProbe) {
            PnsConnectivity.logCapabilitySummary(context)
        }
    }

    LaunchedEffect(adbWebDavProbe) {
        if (!adbWebDavProbe) return@LaunchedEffect
        NetworkStorageClient.probeWebDavConfigured(context)
    }

    LaunchedEffect(adbSocialStreamProbe) {
        if (!adbSocialStreamProbe) return@LaunchedEffect
        val items = PnsMediaStoreGallery.loadIndex(context, maxItems = 1)
        val first = items.firstOrNull()
        if (first != null) {
            SocialStreamHooks.postCaptureEvent(context, first.uri, first.mimeType, first.displayName)
        } else {
            SocialStreamHooks.postCaptureEvent(context, Uri.EMPTY, null, "probe")
        }
    }

    LaunchedEffect(adbCollaborativeProbe) {
        if (!adbCollaborativeProbe) return@LaunchedEffect
        CollaborativeCapture.logProbe(context)
    }

    fun invokeSmileTriggeredStillCapture() {
        if (!primaryPhoto) {
            Log.i("PNS.SmileStill", "smileCapture skipped: video mode (photo only)")
            return
        }
        if (isRecording || sweepJob != null) return
        val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
        val plan = composedStillIntentState.value.resolveCapturePlan()
        controller.setComposedCapturePlan(plan)
        val blocked = controller.composedCaptureBlockedReason(plan)
        if (blocked != null) {
            Log.w("PNS.SmileStill", "smileCapture blocked: $blocked")
            return
        }
        controller.captureComposedStill(
            appContext = context.applicationContext,
            plan = plan,
            haptics = haptics,
            surfaceRotation = rot,
            dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutState.value),
            stillsLut = stillsLutState.value,
            onTonalReady = null,
            onResult = { result ->
                applyStillResultToGalleryThumb(result)
                result.onFailure { e ->
                    Log.w("PNS.SmileStill", "smileCapture failed: ${e.message}")
                }
            },
        )
    }

    LaunchedEffect(primaryPhoto, isRecording, sweepJob, composedStillIntent) {
        controller.setSmileStillCaptureListener { invokeSmileTriggeredStillCapture() }
    }

    LaunchedEffect(adbSmileStillSynthetic, adbEnableSmileStill, primaryPhoto, isRecording, sweepJob) {
        if (!adbSmileStillSynthetic || !adbEnableSmileStill) return@LaunchedEffect
        if (!primaryPhoto || isRecording || sweepJob != null) return@LaunchedEffect
        delay(18_000)
        Log.i("PNS.SmileStill", "smileSyntheticTrigger")
        PnsAdbLog.i(context, "smileStill synthetic capture (adb gate)")
        invokeSmileTriggeredStillCapture()
    }

    LaunchedEffect(isRecording, primaryPhoto, selectedFps, selectedCameraId, imagingProfile, videoCaptureReturn, hudState.current.videoEncodeLane) {
        val want = isRecording && !primaryPhoto
        if (!want) {
            if (controller.peekRawVideoRecordingActive() || controller.adbForceRawVideoLane) {
                controller.applyRawVideoRecordingShell(
                    wantRecord = false,
                    profile = imagingProfile,
                ) { uri ->
                    if (uri != null) {
                        lastGalleryUri = uri
                    }
                }
            }
            controller.applyInAppVideoRecordingShell(
                wantRecord = false,
                profile = imagingProfile,
                wantHighSpeed = selectedFps >= VideoRecordingController.HFR_THRESHOLD_FPS,
                onUi = { ev ->
                    when (ev) {
                        InAppVideoRecordingUiEvent.StartFailed -> Unit
                        is InAppVideoRecordingUiEvent.Stopped -> {
                            if (ev.uri != null) {
                                lastGalleryUri = ev.uri
                            }
                            val vc = videoCaptureReturn
                            if (vc != null && ev.uri != null) {
                                captureScope.launch {
                                    deliverVideoCaptureToCaller(vc, imagingProfile, recordedVideoUri = ev.uri)
                                }
                            }
                        }
                    }
                }
            )
            return@LaunchedEffect
        }
        val camForVideo = selectedCameraId
        if (camForVideo.isNullOrBlank()) {
            isRecording = false
            captureScope.pnsShowSnackbar(
                snackbarHostState,
                "Can't start in-app video (pick a camera).",
                longDuration = false,
            )
            return@LaunchedEffect
        }
        controller.setDesired(camForVideo, selectedFps)
        if (controller.wantsRawVideoLane()) {
            Log.i(
                "PNS.ChromeUx",
                "rawVideoShellRequest fps=$selectedFps cam=$camForVideo lane=RAW",
            )
            controller.applyRawVideoRecordingShell(wantRecord = true, profile = imagingProfile)
            delay(150)
            if (!controller.peekRawVideoRecordingActive()) {
                isRecording = false
                PnsAdbLog.i(context.applicationContext, "rawVideoShellStartFailed")
                captureScope.pnsShowSnackbar(
                    snackbarHostState,
                    "Can't start RAW video (OP13 rear leaf + RAW session required).",
                    longDuration = false,
                )
            }
            return@LaunchedEffect
        }
        Log.i(
            "PNS.ChromeUx",
            "inAppVideoShellRequest fps=$selectedFps cam=$camForVideo out=DCIM/Point and Shoot (MediaStore)",
        )
        controller.applyInAppVideoRecordingShell(
            wantRecord = true,
            profile = imagingProfile,
            wantHighSpeed = selectedFps >= VideoRecordingController.HFR_THRESHOLD_FPS,
            onUi = { ev ->
                when (ev) {
                    InAppVideoRecordingUiEvent.StartFailed -> {
                        isRecording = false
                        PnsAdbLog.i(context.applicationContext, "inAppVideoShellStartFailed")
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Can't start in-app video (try fps ≤ 119, rear camera).",
                            longDuration = false,
                        )
                    }

                    is InAppVideoRecordingUiEvent.Stopped -> {
                        isRecording = false
                        if (ev.uri != null) {
                            lastGalleryUri = ev.uri
                        } else {
                            captureScope.pnsShowSnackbar(
                                snackbarHostState,
                                "Video not saved — no frames were encoded. Try 60 fps or a lower resolution.",
                                longDuration = true,
                            )
                        }
                        val vc = videoCaptureReturn
                        if (vc != null && ev.uri != null) {
                            captureScope.launch {
                                deliverVideoCaptureToCaller(vc, imagingProfile, recordedVideoUri = ev.uri)
                            }
                        }
                    }
                }
            }
        )
    }

    val debuggableVideoAutomation =
        remember {
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
    val adbRawVideoAutomationConsumed = remember { AtomicBoolean(false) }
    val adbInAppVideoAutomationConsumed = remember { AtomicBoolean(false) }
    LaunchedEffect(
        adbAutomationVideoRawSec,
        debuggableVideoAutomation,
    ) {
        val sec = adbAutomationVideoRawSec
        if (!debuggableVideoAutomation || sec <= 0) return@LaunchedEffect
        if (!adbRawVideoAutomationConsumed.compareAndSet(false, true)) return@LaunchedEffect
        try {
        primaryPhoto = false
        controller.adbForceRawVideoLane = true
        delay(50)
        PnsAdbLog.i(context, "start RAW video automation recordSec=$sec profile=${imagingProfileState.value.id}")
        var hwWait = 0
        while (!controller.previewCameraHandlerReady() && hwWait < 300) {
            delay(50)
            hwWait++
        }
        delay(2500)
        var waited = 0
        while (selectedCameraIdState.value.isNullOrBlank() && waited < 200) {
            delay(100)
            waited++
        }
        var pumpWait = 0
        while (
            controller.previewTextureFrameCount() < 8 &&
                pumpWait < ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_WAIT_MAX_ITERATIONS
        ) {
            delay(120)
            pumpWait++
        }
        isRecording = true
        var prepWait = 0
        while (
            !controller.peekRawVideoRecordingActive() &&
                prepWait < 400
        ) {
            delay(25)
            prepWait++
        }
        if (!controller.peekRawVideoRecordingActive()) {
            PnsAdbLog.i(context, "rawVideoAutomation notRecording prepWaitMs=${prepWait * 25}")
            isRecording = false
            return@LaunchedEffect
        }
        delay(sec * 1000L)
        isRecording = false
        controller.applyRawVideoRecordingShell(
            wantRecord = false,
            profile = imagingProfileState.value,
        ) { uri ->
            PnsAdbLog.i(
                context,
                "rawVideoAutomation done saved=${uri != null} recordSec=$sec",
            )
        }
        PnsAdbLog.i(context, "finished RAW video automation recordSec=$sec")
        } finally {
            hostActivity?.intent?.stripPreviewVideoAutomationExtras()
        }
    }

    LaunchedEffect(
        adbAutomationInAppVideoSec,
        debuggableVideoAutomation,
    ) {
        val sec = adbAutomationInAppVideoSec
        if (!debuggableVideoAutomation || sec <= 0 || adbAutomationVideoRawSec > 0) return@LaunchedEffect
        if (!adbInAppVideoAutomationConsumed.compareAndSet(false, true)) return@LaunchedEffect
        try {
        primaryPhoto = false
        delay(50)
        val dualAutomation =
            adbInitialDial == CommandDialMode.Dual
        if (dualAutomation) {
            selectedFps = DualVideoRecordingController.V1_TARGET_FPS
            selectedCameraIdState.value?.let { controller.setDesired(it, selectedFps) }
        }
        PnsAdbLog.i(
            context,
            "start in-app video automation recordSec=$sec profile=${imagingProfileState.value.id} dual=$dualAutomation",
        )
        var hwWait = 0
        while (!controller.previewCameraHandlerReady() && hwWait < 300) {
            delay(50)
            hwWait++
        }
        if (dualAutomation) {
            delay(1500)
        } else {
            delay(2500)
        }
        adbAutomationVideoFps?.takeIf { it >= 120 }?.let { targetFps ->
            selectedFps = targetFps.coerceIn(15, 480)
            controller.setDesired(selectedCameraIdState.value, targetFps)
            PnsAdbLog.i(context, "inAppVideoAutomation hfrSettle fps=$targetFps")
            delay(4000)
            if (targetFps >= 480) delay(6000)
        }
        var waited = 0
        while (selectedCameraIdState.value.isNullOrBlank() && waited < 200) {
            delay(100)
            waited++
        }
        var pumpWait = 0
        while (
            controller.previewTextureFrameCount() < 8 &&
                pumpWait < ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_WAIT_MAX_ITERATIONS
        ) {
            delay(120)
            pumpWait++
        }
        isRecording = true
        if (dualAutomation) {
            controller.ensureDualFrontOpenForRecord()
        }
        val prepCap =
            when {
                dualAutomation -> 2400
                adbAutomationVideoFps != null && adbAutomationVideoFps >= 480 -> 4800
                adbAutomationVideoFps != null && adbAutomationVideoFps >= 240 -> 2800
                adbAutomationVideoFps != null && adbAutomationVideoFps >= 120 -> 2800
                else -> 400
            }
        var prepWait = 0
        while (
            !controller.peekInAppVideoAutomationRecordReady() &&
                !controller.peekInAppVideoShellStartFailureHold() &&
                prepWait < prepCap
        ) {
            if (
                dualAutomation &&
                    controller.peekInAppVideoRecorderPresent() &&
                    !controller.peekInAppVideoRecorderStarted() &&
                    prepWait >= 40
            ) {
                controller.maybeStartInAppVideoRecorder()
            }
            delay(25)
            prepWait++
        }
        if (controller.peekInAppVideoShellStartFailureHold() || !controller.peekInAppVideoAutomationRecordReady()) {
            PnsAdbLog.i(
                context,
                "inAppVideoAutomation recorderMissingOrFailed prepWaitMs=${prepWait * 25} hold=${controller.peekInAppVideoShellStartFailureHold()} dual=$dualAutomation",
            )
            isRecording = false
            return@LaunchedEffect
        }
        if (dualAutomation) {
            var frontWait = 0
            while (!controller.peekDualFrontSessionReady() && frontWait < 400) {
                delay(25)
                frontWait++
            }
            PnsAdbLog.i(
                context,
                "inAppVideoAutomation dualFrontSettle ready=${controller.peekDualFrontSessionReady()} waitMs=${frontWait * 25}",
            )
            var dualWait = 0
            while (!controller.peekDualGlRecordArmed() && dualWait < 400) {
                delay(25)
                dualWait++
            }
            if (!controller.peekDualGlRecordArmed() || !controller.peekDualEncoderSinkReady()) {
                PnsAdbLog.i(
                    context,
                    "inAppVideoAutomation dualGlNotArmed frontReady=${controller.peekDualFrontSessionReady()} " +
                        "sink=${controller.peekDualEncoderSinkReady()} dualWaitMs=${dualWait * 25}",
                )
                isRecording = false
                return@LaunchedEffect
            }
            PnsAdbLog.i(
                context,
                "inAppVideoAutomation dualGlArmed ok dualWaitMs=${dualWait * 25} sinkReady=true",
            )
        }
        delay(sec * 1000L)
        if (adbAutomationVideoFps != null && adbAutomationVideoFps >= 120) {
            delay(3000)
        }
        isRecording = false
        PnsAdbLog.i(
            context,
            "finished in-app video automation recordSec=$sec previewFps=${"%.1f".format(controller.peekPreviewSmoothedFps())} " +
                "targetFps=${adbAutomationVideoFps ?: selectedFps}",
        )
        } finally {
            hostActivity?.intent?.stripPreviewVideoAutomationExtras()
        }
    }

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
            captureScope.pnsShowSnackbar(
                snackbarHostState,
                "Location off — new photos won't be geotagged.",
            )
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
            captureScope.pnsShowSnackbar(
                snackbarHostState,
                "Do Not Disturb in camera needs notification-policy access — tap Policy access in preview options.",
            )
        }
    }

    LaunchedEffect(adbBurstStillCount, adbBurstIntervalMs, composedStillIntent) {
        val n = adbBurstStillCount
        if (n <= 0) return@LaunchedEffect
        PnsAdbLog.i(context, "start burst automation n=$n intervalMs=$adbBurstIntervalMs")
        var waitCap = 0
        while (!controller.canCaptureStill() && waitCap < 300) {
            delay(400)
            waitCap++
        }
        if (!controller.canCaptureStill()) {
            PnsAdbLog.e(context, "burst automation aborted: canCaptureStill=false")
            return@LaunchedEffect
        }
        delay(ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS_FAST)
        val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
        val plan = composedStillIntent.resolveCapturePlan()
        controller.markAdbScriptedStillAutomationActive(true)
        try {
            suspendCoroutine<Unit> { cont ->
                controller.captureComposedStillBurst(
                    context.applicationContext,
                    plan = plan,
                    haptics = haptics,
                    surfaceRotation = rot,
                    shotCount = n,
                    intervalMs = adbBurstIntervalMs.toLong(),
                    dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                    stillsLut = stillsLutLatest.value,
                    adbValidationShotLabel = "burst",
                    onResult = { _ -> cont.resume(Unit) },
                )
            }
            PnsAdbLog.i(context, "finished burst automation n=$n")
        } finally {
            controller.markAdbScriptedStillAutomationActive(false)
        }
    }

    LaunchedEffect(adbComposedStillSmoke, composedStillIntent, adbBurstStillCount) {
        if (!adbComposedStillSmoke || adbBurstStillCount > 0) return@LaunchedEffect
        PnsAdbLog.i(context, "start composed still smoke raw=${composedStillIntent.raw} jpeg=${composedStillIntent.jpeg}")
        var waitCap = 0
        while (!controller.canCaptureStill() && waitCap < 300) {
            delay(400)
            waitCap++
        }
        if (!controller.canCaptureStill()) {
            PnsAdbLog.e(context, "composed still smoke aborted: canCaptureStill=false")
            return@LaunchedEffect
        }
        delay(ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS_FAST)
        val plan = composedStillIntent.resolveCapturePlan()
        val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
        suspendCoroutine<Unit> { cont ->
            controller.captureComposedStill(
                context.applicationContext,
                plan = plan,
                haptics = haptics,
                surfaceRotation = rot,
                dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                stillsLut = stillsLutLatest.value,
                adbValidationShotLabel = "composed_smoke",
            ) { result ->
                result.fold(
                    onSuccess = { out ->
                        PnsAdbLog.i(
                            context,
                            "captureComposedStill composed_smoke ok=true dng=${out.dngUriString.take(80)} " +
                                "tonal=${out.tonalUriString?.take(80) ?: "none"}",
                        )
                    },
                    onFailure = { e ->
                        PnsAdbLog.e(
                            context,
                            "captureComposedStill composed_smoke ok=false err=${e.message}",
                        )
                    },
                )
                cont.resume(Unit)
            }
        }
        PnsAdbLog.i(context, "finished composed still smoke")
    }

    // Keys intentionally omit imagingProfile — profile changes must not cancel mid-burst (race with UI).
    LaunchedEffect(
        adbSequentialRawStills,
        adbRawStillFastAutomation,
        adbBracketPattern,
        adbInitialDial,
        adbFocalMmSlotProbe,
    ) {
        val bracket = adbBracketPattern
        val dial = adbInitialDial
        if (bracket != null && dial == CommandDialMode.BKT) {
            PnsAdbLog.i(context, "start bracket automation pattern=$bracket")
            controller.setCommandDialMode(CommandDialMode.BKT)
            delay(400)
            var waited = 0
            while (selectedCameraIdState.value.isNullOrBlank() && waited < 150) {
                delay(100)
                waited++
            }
            while (!controller.canCaptureBracketBurst()) delay(400)
            delay(2500)
            val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
            controller.markAdbScriptedStillAutomationActive(true)
            try {
                suspendCoroutine<Unit> { cont ->
                    controller.captureBracketBurst(
                        context.applicationContext,
                        haptics,
                        rot,
                        bracket,
                        dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                        stillsLut = stillsLutLatest.value,
                    ) { result ->
                        PnsAdbLog.i(
                            context,
                            "captureBracketBurst pattern=$bracket ok=${result.isSuccess} detail=${result.exceptionOrNull()?.message ?: result.getOrNull()?.take(120)}",
                        )
                        cont.resume(Unit)
                    }
                }
            } finally {
                controller.markAdbScriptedStillAutomationActive(false)
            }
            return@LaunchedEffect
        }
        val n = adbSequentialRawStills
        if (n > 0) {
            val fast = adbRawStillFastAutomation
            PnsAdbLog.i(context, "start sequential stills n=$n fast=$fast profile=${imagingProfileState.value.id}")
            val waitPollMs = if (fast) ADB_WAIT_CAN_CAPTURE_RAW_POLL_MS_FAST else ADB_WAIT_CAN_CAPTURE_RAW_POLL_MS
            if (adbFocalMmSlotProbe != null) {
                var focalWait = 0
                while (!focalSlotProbeConsumed && focalWait < 400) {
                    delay(50)
                    focalWait++
                }
                if (!focalSlotProbeConsumed) {
                    PnsAdbLog.e(
                        context,
                        "sequential still aborted: timeout waiting for focal slot probe (mm=${adbFocalMmSlotProbe.labelMm})",
                    )
                    return@LaunchedEffect
                }
            }
            var waited = 0
            while (selectedCameraIdState.value.isNullOrBlank() && waited < 150) {
                delay(100)
                waited++
            }
            var waitCap = 0
            while (!controller.canCaptureStill() && waitCap < 300) {
                if (waitCap % 12 == 0) {
                    val reason = controller.rawStillNotReadyReason() ?: "ready"
                    val st = controller.status()
                    PnsAdbLog.w(
                        context,
                        "waiting canCaptureStill tries=$waitCap $reason status=$st",
                    )
                }
                delay(waitPollMs)
                waitCap++
            }
            if (!controller.canCaptureStill()) {
                val reason = controller.rawStillNotReadyReason()
                val st = controller.status()
                PnsAdbLog.e(
                    context,
                    "sequential still aborted: timeout waiting for capture session $reason status=$st",
                )
                return@LaunchedEffect
            }
            if (imagingProfileState.value is ImagingProfile.UltraMax) {
                PnsAdbLog.i(context, "sequential RAW: ultra_max extra settle before first still")
                delay(ULTRA_MAX_ADB_SEQUENTIAL_RAW_SETTLE_MS)
            }
            delay(if (fast) ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS_FAST else ADB_SEQUENTIAL_RAW_POST_READY_SETTLE_MS)
            if (
                OnePlus13FleetPolicy.useProShotPureDngSave() &&
                    adbFocalMmSlotProbe != null
            ) {
                PnsAdbLog.i(
                    context,
                    "sequential RAW: proshot focal extra settle ${ADB_SEQUENTIAL_RAW_PROSHOT_FOCAL_EXTRA_SETTLE_MS}ms",
                )
                delay(ADB_SEQUENTIAL_RAW_PROSHOT_FOCAL_EXTRA_SETTLE_MS)
            }
            val minFrames = if (fast) ADB_SEQUENTIAL_RAW_MIN_TEXTURE_FRAMES_FAST else ADB_SEQUENTIAL_RAW_MIN_TEXTURE_FRAMES
            val texPollMs =
                if (fast) ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_POLL_MS_FAST else ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_POLL_MS
            var pumpWait = 0
            while (
                controller.previewTextureFrameCount() < minFrames &&
                    pumpWait < ADB_SEQUENTIAL_RAW_TEXTURE_FRAME_WAIT_MAX_ITERATIONS
            ) {
                delay(texPollMs)
                pumpWait++
            }
            val gapMs = if (fast) ADB_SEQUENTIAL_RAW_GAP_MS_FAST else ADB_SEQUENTIAL_RAW_GAP_MS_DEFAULT
            controller.markAdbScriptedStillAutomationActive(true)
            try {
                repeat(n) { idx ->
                    val label = "${idx + 1}/$n"
                    val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
                    if (imagingProfileState.value is ImagingProfile.JpegOnly) {
                        PnsAdbLog.i(context, "captureJpegHardwareStill begin $label")
                        suspendCoroutine<Unit> { cont ->
                            controller.captureJpegHardwareStill(
                                context.applicationContext,
                                haptics,
                                rot,
                                stillsLut = stillsLutLatest.value,
                                adbValidationShotLabel = label,
                            ) { _ ->
                                cont.resume(Unit)
                            }
                        }
                    } else {
                        PnsAdbLog.i(context, "captureRawStill begin $label")
                        suspendCoroutine<Unit> { cont ->
                            controller.captureRawStill(
                                context.applicationContext,
                                haptics,
                                rot,
                                dngSoftwareDescription = formatDngSoftwareLine(context, stillsLutLatest.value),
                                stillsLut = stillsLutLatest.value,
                                adbValidationShotLabel = label,
                            ) { _ ->
                                cont.resume(Unit)
                            }
                        }
                    }
                    if (idx < n - 1) delay(gapMs)
                }
                PnsAdbLog.i(
                    context,
                    if (imagingProfileState.value is ImagingProfile.JpegOnly) {
                        "finished sequential JPEG stills n=$n"
                    } else {
                        "finished sequential RAW stills n=$n"
                    },
                )
            } finally {
                controller.markAdbScriptedStillAutomationActive(false)
            }
        }
    }

    val cmForMr =
        remember { context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager }
    val videoMrMap =
        remember(selectedCameraId) {
            val id = selectedCameraId ?: return@remember null
            runCatching {
                cmForMr.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull()
        }
    val videoEncodeSizes =
        remember(videoMrMap) { InAppVideoRecordingSupport.sortedMediaRecorderSizes(videoMrMap) }
    val encW = chromePrefs.current.inAppVideoEncodeWidth
    val encH = chromePrefs.current.inAppVideoEncodeHeight
    val videoEncodeResolved =
        remember(videoMrMap, encW, encH) {
            InAppVideoRecordingSupport.pickOutputSize(videoMrMap, encW, encH)
        }
    val videoEncodeShortLabel =
        remember(videoEncodeResolved) { InAppVideoRecordingSupport.shortLabel(videoEncodeResolved) }

    LaunchedEffect(encW, encH) {
        if (encW > 0 && encH > 0) {
            controller.setInAppVideoEncodeSize(Size(encW, encH))
        }
    }

    val seedOpenAboutSheet = adbShowAboutOverlay

    // Create local reference to avoid scope conflict
    val setBespokeGallery: (Boolean) -> Unit = { showBespokeGallery = it }

    // Preview chrome stays on dark Material tokens regardless of global Light/System (layout lock).
    Box(modifier = Modifier.fillMaxSize()) {
    PnsTheme(darkTheme = true) {
    PreviewEngineContent(
        // Single merged status bar + cutout top — [rememberSystemInsetsDp] already maxes cutout
        // with system bars; avoid [asPaddingValuesWithExtraTopBarBand] (2× top) for the chrome band.
        padding = insets.asPaddingValues(),
        previewHostSlot = previewHostSlot,
        lastGalleryUri = lastGalleryUri,
        onBespokeGalleryChange = setBespokeGallery,
        onExternalGalleryViewerLaunched = { pendingHardRestartAfterExternalGallery.set(true) },
        cameraIds = cameraIdsList,
        selectedCameraId = selectedCameraId,
        selectedFps = selectedFps,
        fpsOptions = fpsOptions,
        videoEncodeSizes = videoEncodeSizes,
        videoEncodeShortLabel = videoEncodeShortLabel,
        onPickVideoEncodeSize = { sz ->
            val c = chromePrefs.current
            chromePrefs.update(
                c.copy(inAppVideoEncodeWidth = sz.width, inAppVideoEncodeHeight = sz.height),
            )
            Log.i("PNS.ChromeUx", "inAppVideoResPick=${sz.width}x${sz.height}")
        },
        status = previewState.status,
        capturePipelineHint = controller.readoutCapturePipelineHint(),
        measuredFps = previewState.measuredFps,
        previewReadoutIso = previewState.previewReadoutIso,
        previewReadoutExposureNs = previewState.previewReadoutExposureNs,
        previewReadoutAwbMode = previewState.previewReadoutAwbMode,
        previewJpegCompanion = previewState.previewJpegCompanion,
        surfaceInfo = previewState.surfaceInfo,
        previewBufferSize = previewState.previewBufferSize,
        sensorOrientationDeg = previewState.sensorOrientationDeg,
        isSweeping = sweepJob != null,
        hudState = hudState,
        deviceUiRotationState = deviceUiRotationState,
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
        seedOpenAboutSheet = seedOpenAboutSheet,
        onPickFirstCamera = {
            val ids = controller.cameraIds()
            val m23 = resolveFocalMmSlot(context.applicationContext, FocalMmSlot.M23, ids)
            selectedCameraId = pickCameraIdFromM23Resolve(m23, ids)
        },
        onSwitchToFrontCamera = {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = controller.cameraIds()
            val front = Camera2Facing.frontCameraId(cm, ids)
            if (front != null) {
                selectedCameraId = front
                focalCrop = null
                Log.i("PNS.ChromeUx", "cameraFacingSwitch=front cameraId=$front")
            }
        },
        onSwitchToRearCamera = {
            if (controller.wantsMacroProgram()) return@PreviewEngineContent
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val appCtx = context.applicationContext
            val ids = controller.cameraIds()
            if (ids.isEmpty()) return@PreviewEngineContent
            val last = PreviewChromePreferences.readLastRearCameraId(appCtx)
            if (last != null && last in ids && !Camera2Facing.isFrontCamera(cm, last)) {
                selectedCameraId = last
                focalCrop = null
                Log.i("PNS.ChromeUx", "cameraFacingSwitch=rear cameraId=$last source=lastRear")
            } else {
                val m23 = resolveFocalMmSlot(appCtx, FocalMmSlot.M23, ids)
                val id = pickCameraIdFromM23Resolve(m23, ids) ?: ids.firstOrNull()
                if (id == null) return@PreviewEngineContent
                selectedCameraId = id
                focalCrop = m23?.takeIf { id == it.first }?.second
                Log.i(
                    "PNS.ChromeUx",
                    "cameraFacingSwitch=rear cameraId=$id focalCrop=${focalCrop?.name} source=m23Wide",
                )
            }
        },
        onSetFps = {
            userSelectedFps = it
            selectedFps = it
        },
        onSelectCameraId = { id ->
            selectedCameraId = id
            focalCrop = null
        },
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
                    while (!PreviewLongRunningPause.shouldContinueSweep()) delay(500)
                    if (!isActive) return@launch
                    selectedCameraId = cam
                    Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                    // Give the UI/controller a moment to propagate camera id change + surface resize.
                    delay(700)

                    for (fps in sequence) {
                        while (!PreviewLongRunningPause.shouldContinueSweep()) delay(500)
                        if (!isActive) return@launch
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
            val ids = controller.cameraIds()
            val pair =
                resolveFocalMmSlot(context.applicationContext, slot, ids)
                    ?: return@PreviewEngineContent
            schedulePreviewPhysicalForFocalSlot(
                context.applicationContext,
                controller,
                slot,
                pair,
                ids,
            )
            selectedCameraId = pair.first
            focalCrop = pair.second
            if (pair.second != null && selectedFps >= 120) {
                val cap =
                    PreviewFpsSupport.enumerateQuickFpsOptions(context.applicationContext, pair.first)
                        .asSequence()
                        .map { it.targetFps }
                        .filter { it < 120 }
                        .maxOrNull()
                if (cap != null) {
                    selectedFps = cap
                }
            }
        },
        onEnsureMacroUltraWide = {
            val appCtx = context.applicationContext
            val ids = controller.cameraIds()
            val cm = appCtx.getSystemService(CameraManager::class.java) as CameraManager
            val uw = PreviewMacroProgram.ultraWideCameraId(cm, ids)
            if (uw != null) {
                val pair = resolveFocalMmSlot(appCtx, FocalMmSlot.M14, ids)
                if (pair != null) {
                    schedulePreviewPhysicalForFocalSlot(appCtx, controller, FocalMmSlot.M14, pair, ids)
                    selectedCameraId = pair.first
                    focalCrop = pair.second
                } else {
                    selectedCameraId = uw
                    focalCrop = null
                }
                Log.i("PNS.ChromeUx", "macroMode autoSwitchUW cameraId=$uw")
            }
        },
        composedStillIntent = composedStillIntent,
        onComposedStillIntentChange = { intent ->
            composedStillIntent = intent
            syncHudJpegEncodeFromImgMenu(intent, hudState)
            controller.setComposedCapturePlan(intent.resolveCapturePlan())
        },
        onCaptureDng = {
            trayStillCaptureRef.value?.invoke()
        },
        onBracketBurst = { pattern ->
            HudSettings.saveBracketPattern(context.applicationContext, pattern)
            fun runBracketBurst() {
                val rot = stillCaptureSurfaceRotationFromPhysicalCardinal(latestPhysicalCardinalSnap.value)
                controller.captureBracketBurst(
                    context.applicationContext,
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
                            val retryable = PnsUserFacingErrors.shouldOfferRetryAfterBracketFailure(e)
                            captureScope.pnsShowSnackbar(
                                snackbarHostState,
                                PnsUserFacingErrors.bracketCaptureFailure(e),
                                clipboardDetail =
                                    if (retryable) null else PnsUserFacingErrors.technicalDetailForCopy(e),
                                clipboardAppContext = if (retryable) null else context.applicationContext,
                                onRetry = if (retryable) {
                                    { runBracketBurst() }
                                } else {
                                    null
                                },
                            )
                        },
                    )
                }
            }
            runBracketBurst()
        },
        adbInitialDial = adbInitialDial,
        adbCalibrateGrabSmoke = adbCalibrateGrabSmoke,
        controller = controller,
        primaryPhoto = primaryPhoto,
        onPrimaryPhotoChange = { next ->
            primaryPhoto = next
            // Video → photo while preview is still at 120 fps: no RAW session until user picks <120.
            if (next && selectedFps >= 120) {
                val cap =
                    PreviewFpsSupport.enumerateQuickFpsOptions(context.applicationContext, selectedCameraId)
                        .asSequence()
                        .map { it.targetFps }
                        .filter { it < 120 }
                        .maxOrNull()
                selectedFps = cap ?: 60
            }
            // Photo → video: warn if HFR not available on this device (Sprint 12.2 UI polish)
            if (!next && selectedFps >= 120) {
                val camId = selectedCameraId
                if (camId != null) {
                    val hasHighSpeed = runCatching {
                        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val chars = cm.getCameraCharacteristics(camId)
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        InAppVideoRecordingSupport.supportsHighSpeedVideoRecording(map)
                    }.getOrDefault(false)
                    if (!hasHighSpeed) {
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "HFR video not available on this device",
                            longDuration = false,
                        )
                    }
                }
            }
        },
        adbAutomationVideoDcg = adbAutomationVideoDcg,
        adbForcePowerThermalOverlay = adbForcePowerThermalOverlay,
        adbStorageAvailableBytes = adbStorageAvailableBytes,
        adbAutomationVideoTenBit = adbAutomationVideoTenBit,
        adbAutomationVideoRawSec = adbAutomationVideoRawSec,
        adbAutomationInAppVideoSec = adbAutomationInAppVideoSec,
        videoEncodeSize = videoEncodeResolved,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onApplyWorkflowPreset = { preset ->
            WorkflowPresets.logApplied(context, preset)
            HudSettings.saveCommandDialMode(context, preset.commandDialMode)
            val profile = ImagingProfile.byId(preset.imagingProfileId)
            composedStillIntent =
                ComposedStillIntent.fromLegacyImagingProfile(profile, jpegCompanionOn = true)
            HudSettings.saveImagingProfile(context, profile)
            primaryPhoto = preset.primaryPhoto
            preset.fps?.let { fps ->
                selectedFps = fps
                userSelectedFps = fps
            }
            controller.kickPreviewPipelineRestart()
        },
        eyeOverlayCalibratorActive = eyeOverlayCalibratorActive,
        faceOverlayCalibration = faceOverlayCalibration,
        onFaceOverlayCalibrationChange = { next ->
            faceOverlayCalibration = next
            FaceOverlayCalibrationStore.save(appContext, next)
        },
        onEyeOverlayCalibratorDone = onEyeOverlayCalibratorDone,
    )

    // Bespoke Gallery Overlay
    if (showBespokeGallery) {
        BespokeGalleryScreen(
            initialUri = lastGalleryUri,
            adbBatchShareCount = adbGalleryBatchShareCount,
            onBack = { showBespokeGallery = false },
            onExternalGallery = {
                showBespokeGallery = false
                lastGalleryUri?.let { uri ->
                    if (openMediaWithSystemResolver(context, uri)) {
                        pendingHardRestartAfterExternalGallery.set(true)
                    }
                }
            }
        )
    }
    }
    }

    DisposableEffect(Unit) {
        onDispose {
            PreviewChromePreferences.clearSessionSnapshot()
            controller.stop()
        }
    }
}

// rememberPreviewChromeTwistDegrees was removed when the activity moved to a fixed
// landscape orientation: the preview is no longer rotated by the system, so chrome no
// longer needs to "twist with the screen". Per-element UI rotation now comes from
// [rememberDeviceUiRotationDegrees] (DeviceUiRotation.kt) — chrome rotates around the
// preview, not with it (Sony Photography Pro behavior).

@Composable
private fun PreviewEngineChromeShell(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
private fun PreviewEngineContent(
    padding: PaddingValues,
    previewHostSlot: PreviewHostSlot,
    lastGalleryUri: Uri?,
    onBespokeGalleryChange: (Boolean) -> Unit,
    /** Invoked when [openMediaWithSystemResolver] starts a viewer; next [ON_RESUME] cold-restarts the task. */
    onExternalGalleryViewerLaunched: () -> Unit,
    cameraIds: List<String>,
    selectedCameraId: String?,
    selectedFps: Int,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    videoEncodeSizes: List<Size>,
    videoEncodeShortLabel: String,
    onPickVideoEncodeSize: (Size) -> Unit,
    status: String,
    capturePipelineHint: String?,
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
    deviceUiRotationState: DeviceUiRotationState,
    compositionGuide: CompositionGuideSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    onOpenDeveloperMenu: () -> Unit,
    seedOpenAboutSheet: Boolean = false,
    onPickFirstCamera: () -> Unit,
    onSwitchToFrontCamera: () -> Unit,
    onSwitchToRearCamera: () -> Unit,
    onSetFps: (Int) -> Unit,
    onSelectCameraId: (String) -> Unit,
    onStartSweep: () -> Unit,
    onStopSweep: () -> Unit,
    focalCrop: FocalMode?,
    onApplyFocalMmSlot: (FocalMmSlot) -> Unit,
    onEnsureMacroUltraWide: () -> Unit,
    composedStillIntent: ComposedStillIntent,
    onComposedStillIntentChange: (ComposedStillIntent) -> Unit,
    onCaptureDng: () -> Unit,
    onBracketBurst: (BracketPattern) -> Unit,
    adbInitialDial: CommandDialMode? = null,
    adbCalibrateGrabSmoke: Boolean = false,
    controller: PreviewController,
    primaryPhoto: Boolean,
    onPrimaryPhotoChange: (Boolean) -> Unit,
    adbAutomationVideoDcg: Boolean = false,
    adbForcePowerThermalOverlay: Boolean = false,
    adbStorageAvailableBytes: Long? = null,
    adbAutomationVideoTenBit: Boolean = false,
    adbAutomationVideoRawSec: Int = 0,
    adbAutomationInAppVideoSec: Int = 0,
    videoEncodeSize: Size,
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
    onApplyWorkflowPreset: ((WorkflowPreset) -> Unit)? = null,
    eyeOverlayCalibratorActive: Boolean = false,
    faceOverlayCalibration: FaceOverlayCalibration = FaceOverlayCalibration.Default,
    onFaceOverlayCalibrationChange: (FaceOverlayCalibration) -> Unit = {},
    onEyeOverlayCalibratorDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbarHostState = LocalPnsSnackbarHostState.current
    val settings = hudState.current
    val chrome = chromePrefs.current
    val cameraManager =
        remember(context) {
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    val imagingProfile = composedStillIntent.storageProfile()
    val focalMapCalibratingHint = rememberFocalMapCalibratingHintVisible()
    val fpsTargetEditable =
        CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto) == CaptureMediaFamily.Video || isSweeping
    val captureScope = rememberCoroutineScope()
    var commandDialMode by remember(adbInitialDial) {
        mutableStateOf(
            adbInitialDial ?: HudSettings.loadCommandDialMode(context),
        )
    }
    LaunchedEffect(primaryPhoto, commandDialMode) {
        val allowed =
            CaptureMediaFamily.commandDialModesFor(CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto))
                .toSet()
        if (commandDialMode !in allowed) {
            commandDialMode = CommandDialMode.Auto
            HudSettings.saveCommandDialMode(context, CommandDialMode.Auto)
        }
    }
    // Clamp fps before arming dual — opening the front camera during a rear session rebuild
    // (fps 120→30) leaves the rear band black on CPH2655-class devices.
    LaunchedEffect(primaryPhoto, commandDialMode, selectedFps) {
        if (!primaryPhoto && commandDialMode == CommandDialMode.Dual &&
            selectedFps > DualVideoRecordingController.V1_TARGET_FPS
        ) {
            onSetFps(DualVideoRecordingController.V1_TARGET_FPS)
        }
    }
    LaunchedEffect(primaryPhoto, commandDialMode, selectedCameraId, cameraIds, selectedFps) {
        val dual =
            !primaryPhoto &&
                commandDialMode == CommandDialMode.Dual &&
                selectedFps <= DualVideoRecordingController.V1_TARGET_FPS
        if (!dual) {
            controller.setDualVideoActive(false)
            // Dual concurrent routing opens logical **0**; HFR must record on leaf wide **2**.
            if (selectedCameraId == dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_LOGICAL) {
                val wide = dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_WIDE
                if (wide in cameraIds) {
                    Log.i(
                        DualVideoRecordingController.TAG,
                        "dual off — restore rear cameraId=$wide for preview/HFR",
                    )
                    onSelectCameraId(wide)
                    controller.setPreviewSurfacePhysicalCameraId(null)
                }
            }
            return@LaunchedEffect
        }
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val front = Camera2Facing.frontCameraId(cm, cameraIds)
        val resolved =
            DualVideoHalConcurrency.resolveRearForDual(cm, selectedCameraId, front)
        if (resolved.rearId != null && resolved.rearId != selectedCameraId) {
            Log.i(
                DualVideoRecordingController.TAG,
                "dual rear reroute ${selectedCameraId} -> ${resolved.rearId} (HAL concurrent with front=$front)",
            )
            onSelectCameraId(resolved.rearId)
            if (resolved.rearId == dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_LOGICAL) {
                controller.setPreviewSurfacePhysicalCameraId(
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_WIDE,
                )
            }
            return@LaunchedEffect
        }
        controller.setDualVideoActive(true)
        DualVideoRecordingController.logStatus(
            active = true,
            rearId = selectedCameraId,
            frontId = front,
        )
    }
    LaunchedEffect(commandDialMode, selectedCameraId) {
        if (commandDialMode == CommandDialMode.M) {
            controller.ensureManualFocusForDialM()
        } else {
            controller.clearManualFocusDistance()
        }
    }
    var focusModePickerOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = focusModePickerOpen) {
        focusModePickerOpen = false
    }
    val previewFocusSelection = controller.previewFocusSelection()
    val macroLensLocked =
        PreviewMacroProgram.wantsMacroProgram(commandDialMode, previewFocusSelection)
    val macroFocusDialCoupling = rememberMacroFocusDialCouplingState()
    val manualFocusUi =
        rememberPreviewManualFocusUiState(
            commandDialMode = commandDialMode,
            previewFocusSelection = previewFocusSelection,
            selectedCameraId = selectedCameraId,
            controller = controller,
            chromePrefs = chromePrefs,
            chromeTapPreviewToCapture = chrome.tapPreviewToCapture,
        )
    val focusChipValue =
        PreviewFocusMode.chipValue(previewFocusSelection, manualFocusUi.focusChipDiopters)
    val effectiveTapPreviewToCapture = manualFocusUi.effectiveTapPreviewToCapture
    val onApplyFocalMmSlotGuarded: (FocalMmSlot) -> Unit = { slot ->
        if (macroLensLocked && slot != FocalMmSlot.M14) {
            captureScope.pnsShowSnackbar(
                snackbarHostState,
                "Macro mode locks the ultra-wide lens (14 mm).",
                longDuration = false,
            )
        } else {
            onApplyFocalMmSlot(slot)
        }
    }
    LaunchedEffect(macroLensLocked, commandDialMode, selectedCameraId, cameraIds) {
        if (!macroLensLocked) return@LaunchedEffect
        if (selectedCameraId == null || cameraIds.isEmpty()) return@LaunchedEffect
        if (commandDialMode == CommandDialMode.Macro) {
            val macroFocus =
                PreviewMacroProgram.preferredFocusSelectionForDialMacro(
                    controller.previewFocusMenuSelections(),
                )
            if (macroFocus != null && controller.previewFocusSelection() != macroFocus) {
                controller.setPreviewFocusSelection(macroFocus)
            }
        }
        onEnsureMacroUltraWide()
    }
    var selfTimerRemaining by remember { mutableIntStateOf(0) }
    var selfTimerCountdownActive by remember { mutableStateOf(false) }

    fun triggerStillCapture() {
        val delaySec =
            PreviewChromePreferences.normalizeSelfTimerDelaySec(chromePrefs.current.selfTimerDelaySec)
        if (commandDialMode == CommandDialMode.BKT) {
            val pat = HudSettings.loadBracketPattern(context)
            when {
                controller.canCaptureBracketBurst() -> onBracketBurst(pat)
                else ->
                    captureScope.pnsShowSnackbar(
                        snackbarHostState,
                        "Bracket: set IMG tiers (RAW and/or JPEG) and preview ≤119 fps.",
                        longDuration = true,
                    )
            }
            return
        }
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

    var centerViewSize by remember { mutableStateOf(IntSize.Zero) }
    /** TextureView / rotated inner box size in px (for buffer→eye-mark mapping; not the full letterboxed viewport). */
    var previewTilePx by remember { mutableStateOf(IntSize.Zero) }
    val liveChartTarget = remember { BundledReferenceTargets.ColorCheckerClassic24 }
    var chartCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var chartAutoDetectBusy by remember { mutableStateOf(false) }
    LaunchedEffect(chrome.liveChartCornerOverlay) {
        if (!chrome.liveChartCornerOverlay) {
            chartCorners = emptyList()
            chartAutoDetectBusy = false
        }
    }
    fun chartCornersFromDetector(
        det: ChartQuadDetector.Result,
        layoutW: Int,
        layoutH: Int,
    ): List<Offset> {
        val scaled = ChartQuadDetector.scaleResultToSize(det, layoutW, layoutH)
        return listOf(
            Offset(scaled.tl.x, scaled.tl.y),
            Offset(scaled.tr.x, scaled.tr.y),
            Offset(scaled.br.x, scaled.br.y),
            Offset(scaled.bl.x, scaled.bl.y),
        )
    }
    fun runLiveChartAutoDetect() {
        val gl = previewHostSlot.view
        val lw = centerViewSize.width
        val lh = centerViewSize.height
        if (gl == null || lw <= 0 || lh <= 0) {
            captureScope.pnsShowSnackbar(snackbarHostState, "Preview not ready for auto-detect.")
            return
        }
        if (chartAutoDetectBusy) return
        chartAutoDetectBusy = true
        gl.requestRender()
        captureScope.launch(Dispatchers.IO) {
            try {
                val bmp = controller.grabPreviewFrameBitmap(gl)
                if (bmp == null) {
                    withContext(Dispatchers.Main) {
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Could not grab preview for auto-detect.",
                        )
                    }
                    return@launch
                }
                val det = ChartQuadDetector.detectFromBitmap(bmp)
                bmp.recycle()
                withContext(Dispatchers.Main) {
                    if (det == null) {
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "No chart quad found — tap corners or reframe.",
                        )
                    } else {
                        chartCorners = chartCornersFromDetector(det, lw, lh)
                        PnsAdbLog.i(
                            context,
                            "liveChartAutoDetect conf=${"%.2f".format(det.confidence)} corners=4",
                        )
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { chartAutoDetectBusy = false }
            }
        }
    }
    fun applyLiveChartCalibration() {
        if (chartCorners.size < 4) return
        val gl = previewHostSlot.view
        val lw = centerViewSize.width
        val lh = centerViewSize.height
        if (gl == null || lw <= 0 || lh <= 0) {
            captureScope.pnsShowSnackbar(snackbarHostState, "Preview not ready.")
            return
        }
        gl.requestRender()
        captureScope.launch(Dispatchers.IO) {
            val bmp = controller.grabPreviewFrameBitmap(gl)
            if (bmp == null) {
                withContext(Dispatchers.Main) {
                    captureScope.pnsShowSnackbar(snackbarHostState, "Could not grab preview frame.")
                }
                return@launch
            }
            val planeCorners =
                CalibrationWorkflow.chartCornersForPlane(
                    corners = chartCorners,
                    layoutWidth = lw,
                    layoutHeight = lh,
                    planeWidth = bmp.width,
                    planeHeight = bmp.height,
                )
            val result =
                runCatching {
                    CalibrationWorkflow.computeFromBitmap(
                        bitmap = bmp,
                        target = liveChartTarget,
                        corners = planeCorners,
                        cameraId = selectedCameraId ?: "preview",
                    )
                }
            bmp.recycle()
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { computed ->
                        val saved =
                            CalibrationProfileStorage.save(context, computed.profile)
                        hudState.update(
                            CalibrationWorkflow.hudNaturalDefaults(hudState.current),
                        )
                        controller.applyChartCalibrationProfile(
                            computed.profile,
                            computed.exposureStops,
                        )
                        CalibrationWorkflow.logPostApplyParity(context, computed.profile)
                        val pathNote = saved?.name?.let { " ($it)" } ?: ""
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Chart calibration applied$pathNote",
                        )
                    },
                    onFailure = { ex ->
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Apply failed: ${ex.message}",
                        )
                    },
                )
            }
        }
    }
    LaunchedEffect(chrome.liveChartCornerOverlay, chartCorners.size, centerViewSize) {
        if (!chrome.liveChartCornerOverlay) return@LaunchedEffect
        if (chartCorners.size >= 4) return@LaunchedEffect
        while (chrome.liveChartCornerOverlay && chartCorners.size < 4) {
            delay(1800)
            if (!chrome.liveChartCornerOverlay || chartCorners.size >= 4 || chartAutoDetectBusy) {
                continue
            }
            val gl = previewHostSlot.view ?: continue
            if (centerViewSize.width <= 0 || centerViewSize.height <= 0) continue
            chartAutoDetectBusy = true
            try {
                gl.requestRender()
                withContext(Dispatchers.IO) {
                    val bmp = controller.grabPreviewFrameBitmap(gl) ?: return@withContext
                    val det = ChartQuadDetector.detectFromBitmap(bmp)
                    bmp.recycle()
                    if (det != null) {
                        chartCorners =
                            chartCornersFromDetector(
                                det,
                                centerViewSize.width,
                                centerViewSize.height,
                            )
                        PnsAdbLog.i(
                            context,
                            "liveChartAutoDetect debounced conf=${"%.2f".format(det.confidence)}",
                        )
                    }
                }
            } finally {
                chartAutoDetectBusy = false
            }
        }
    }
    /** Measured preview content host (GL + overlays); same size as [LutCameraPreviewRenderer.setGeometry]. */
    var previewContentPx by remember { mutableStateOf(IntSize.Zero) }
    var previewFinderPx by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }
    var lastStillPostReadout by remember { mutableStateOf<StillPostReadoutSnapshot?>(null) }
    DisposableEffect(controller) {
        val listener: (StillPostReadoutSnapshot?) -> Unit = { lastStillPostReadout = it }
        controller.setLastStillPostReadoutListener(listener)
        onDispose { controller.setLastStillPostReadoutListener(null) }
    }
    var afShutterGateActiveForUi by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        controller.setAfShutterGateUiListener { active -> afShutterGateActiveForUi = active }
        onDispose { controller.setAfShutterGateUiListener(null) }
    }
    // Same-frame sync: LaunchedEffect runs after the first frame, so a TextureView-driven
    // maybeRestart could observe a stale dial on the controller — SideEffect aligns first.
    SideEffect {
        controller.setCommandDialMode(commandDialMode)
    }
    SideEffect {
        controller.setPreviewFlashMode(chrome.previewFlashMode)
    }
    TrackModeTransition("camera", selectedCameraId ?: "null")
    TrackModeTransition("fps", selectedFps.toString())
    TrackModeTransition(
        "imaging_profile",
        runCatching { imagingProfile.id }.getOrElse { "invalid_profile" },
    )
    TrackModeTransition("recording", isRecording.toString())
    TrackModeTransition("focal_crop", focalCrop?.name ?: "null")
    TrackModeTransition("command_dial", commandDialMode.name)
    TrackModeTransition("primary_photo", primaryPhoto.toString())
    LaunchedEffect(primaryPhoto) {
        Log.i(
            "PNS.ChromeUx",
            "readoutMode=${PreviewReadoutChipMode.readoutModeLogValue(primaryPhoto)}",
        )
        if (PreviewTrayVideoChrome.showVideoFormatFab(primaryPhoto)) {
            Log.i("PNS.ChromeUx", "trayVideoFormatFab=visible anchor=galleryThumb")
        }
        // Preview chrome always uses shrink-to-fit unless the user enables cover-crop in dev settings.
    }
    var showVideoFormatPicker by remember { mutableStateOf(false) }
    val supportsInAppVideoDcg =
        remember(selectedCameraId, settings.enableResearchDcgHDR, adbAutomationVideoDcg) {
            val halDcg =
                selectedCameraId?.let { id ->
                    runCatching {
                        DcgModeSupport.supportsDcgMode(
                            cameraManager.getCameraCharacteristics(id),
                        )
                    }.getOrDefault(false)
                } ?: false
            halDcg || settings.enableResearchDcgHDR || adbAutomationVideoDcg
        }
    var supportsInAppVideoAv1 by remember { mutableStateOf(MediaCodecCapabilityProbe.supportsAv1Encoder()) }
    LaunchedEffect(Unit) {
        supportsInAppVideoAv1 = MediaCodecCapabilityProbe.probe().supportsAv1
    }
    val videoFormatHighSpeedMap =
        remember(selectedCameraId) {
            selectedCameraId?.let { id ->
                runCatching {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                }.getOrNull()
            }
        }
    val videoFormatCatalog =
        remember(selectedCameraId, supportsInAppVideoDcg, supportsInAppVideoAv1, videoFormatHighSpeedMap) {
            InAppVideoFormatSelection.loadCatalog(
                supportsDcg = supportsInAppVideoDcg,
                supportsAv1 = supportsInAppVideoAv1,
                highSpeedMap = videoFormatHighSpeedMap,
            )
        }
    val videoFormatTruth =
        remember(videoFormatHighSpeedMap) {
            InAppVideoFormatSelection.buildVideoTruth(videoFormatHighSpeedMap)
        }
    val selectedInAppVideoFormat =
        remember(videoFormatCatalog, chrome, videoEncodeSize, selectedFps) {
            InAppVideoFormatSelection.resolveSelected(
                catalog = videoFormatCatalog,
                chrome = chrome,
                fallbackWidth = videoEncodeSize.width,
                fallbackHeight = videoEncodeSize.height,
                fallbackFps = selectedFps,
            )
        }
    LaunchedEffect(videoFormatCatalog, primaryPhoto, chrome, adbAutomationInAppVideoSec) {
        if (adbAutomationInAppVideoSec > 0) return@LaunchedEffect
        if (primaryPhoto || videoFormatCatalog.isEmpty()) return@LaunchedEffect
        val resolved =
            InAppVideoFormatSelection.resolveSelected(
                catalog = videoFormatCatalog,
                chrome = chrome,
                fallbackWidth = chrome.inAppVideoEncodeWidth,
                fallbackHeight = chrome.inAppVideoEncodeHeight,
                fallbackFps = chrome.inAppVideoFps,
            )
        if (resolved != null) return@LaunchedEffect
        val fallback = videoFormatCatalog.firstOrNull() ?: return@LaunchedEffect
        Log.i(
            "PNS.ChromeUx",
            "videoFormatCatalogMigrate stalePref -> ${fallback.getLabel()} " +
                "${fallback.resolution.width}x${fallback.resolution.height}@${fallback.frameRate}",
        )
        chromePrefs.update(InAppVideoFormatSelection.chromeAfterSelect(chrome, fallback))
        if (fallback.frameRate != selectedFps) {
            onSetFps(fallback.frameRate)
        }
        onPickVideoEncodeSize(fallback.resolution)
    }
    var recordStartElapsedMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            if (recordStartElapsedMs == null) {
                recordStartElapsedMs = SystemClock.elapsedRealtime()
            }
        } else {
            recordStartElapsedMs = null
        }
    }
    val previewStatusLine =
        previewStatusBarLine(
            capturePipelineHint = capturePipelineHint,
            focalMapCalibratingHint = focalMapCalibratingHint,
            sessionStatus = status,
        )
    // Highlight (H) metering + hardware highlight AE need a non-HFR preview session: [createSession] only
    // attaches YUV when `desiredFps < 120` under `!useHighSpeed`. Default fps is 120, so H at 120 skips YUV.
    LaunchedEffect(commandDialMode, selectedFps, fpsOptions) {
        if (commandDialMode != CommandDialMode.H) return@LaunchedEffect
        if (selectedFps < 120) return@LaunchedEffect
        val cap = fpsOptions.asSequence().map { it.targetFps }.filter { it < 120 }.maxOrNull()
        if (cap == null) {
            Log.w("PNS.Preview", "Highlight (H): no fps ladder entry below 120; YUV metering unavailable")
            return@LaunchedEffect
        }
        val prev = selectedFps
        if (cap != prev) {
            onSetFps(cap)
            Log.i("PNS.Preview", "Highlight (H): preview fps set to $cap for YUV metering (was $prev)")
        }
    }
    // QR dial needs a REGULAR (non-HFR) session so the YUV analysis reader is attached.
    LaunchedEffect(commandDialMode, selectedFps, fpsOptions) {
        if (commandDialMode != CommandDialMode.Qr) return@LaunchedEffect
        if (selectedFps < 120) return@LaunchedEffect
        val cap = fpsOptions.asSequence().map { it.targetFps }.filter { it < 120 }.maxOrNull()
        if (cap == null) {
            Log.w("PNS.Preview", "QR scan: no fps ladder entry below 120; YUV decode unavailable")
            return@LaunchedEffect
        }
        val prev = selectedFps
        if (cap != prev) {
            onSetFps(cap)
            Log.i("PNS.Preview", "QR scan: preview fps set to $cap for YUV decode (was $prev)")
        }
    }
    var qrDecodedText by remember { mutableStateOf<String?>(null) }
    var qrDecodedFormat by remember { mutableStateOf<String?>(null) }
    var qrAction by remember { mutableStateOf<QrScanAction?>(null) }
    var lastQrPresentedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(commandDialMode) {
        if (commandDialMode == CommandDialMode.Qr) {
            Log.i("PNS.ChromeUx", "qrScanMode=active")
        } else {
            qrDecodedText = null
            qrDecodedFormat = null
            qrAction = null
            lastQrPresentedText = null
        }
    }
    LaunchedEffect(qrDecodedText, qrDecodedFormat) {
        val text = qrDecodedText ?: return@LaunchedEffect
        if (text == lastQrPresentedText) return@LaunchedEffect
        lastQrPresentedText = text
        val format = qrDecodedFormat
        qrAction = QrScanResultActions.resolve(text, format)
        QrScanResultActions.present(captureScope, snackbarHostState, context.applicationContext, text, format)
    }
    DisposableEffect(controller) {
        controller.setQrScanListener { text, format ->
            qrDecodedText = text
            qrDecodedFormat = format
            if (text == null) {
                qrAction = null
            }
        }
        onDispose { controller.setQrScanListener(null) }
    }
    var eyeMarksBuffer by remember { mutableStateOf<List<EyeMark>>(emptyList()) }
    var faceTrackBoxesBuffer by remember { mutableStateOf<List<FaceTrackBoxBuffer>>(emptyList()) }

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

    val stillCaptureJpegCompanionPref = chromePrefs.current.stillCaptureJpegCompanion
    LaunchedEffect(previewJpegCompanion, composedStillIntent, stillCaptureJpegCompanionPref) {
        val captureLabel =
            PreviewReadoutStillPipeline.chromeUxLogValue(
                composedStillIntent,
                stillCaptureJpegCompanionPref,
                previewJpegCompanion,
            )
        Log.i(
            "PNS.ChromeUx",
            "readoutCapture=$captureLabel",
        )
    }

    DisposableEffect(controller) {
        controller.setFaceHudOverlayListener { state ->
            eyeMarksBuffer = state.eyeMarks
            faceTrackBoxesBuffer = state.faceBoxesBuffer
        }
        onDispose {
            controller.setFaceHudOverlayListener(null)
        }
    }

    var previewHistogramBins by remember { mutableStateOf<IntArray?>(null) }
    DisposableEffect(controller) {
        controller.setPreviewHistogramListener { previewHistogramBins = it }
        onDispose {
            controller.setPreviewHistogramListener(null)
        }
    }

    LaunchedEffect(settings.showHistogram, controller) {
        controller.setPreviewHistogramEnabled(settings.showHistogram)
    }

    var highlightClipZebraFrame by remember { mutableStateOf<HighlightClipZebraFrame?>(null) }
    DisposableEffect(controller) {
        controller.setHighlightClipZebraListener { highlightClipZebraFrame = it }
        onDispose {
            controller.setHighlightClipZebraListener(null)
            highlightClipZebraFrame = null
        }
    }
    LaunchedEffect(settings.showHighlightClipZebra, controller) {
        controller.setHighlightClipZebraEnabled(settings.showHighlightClipZebra)
    }

    // Sony-Photography-Pro chrome rotation: each rail icon / settings cube counter-rotates
    // about its own centre while the preview texture stays visually fixed (buffer aspect + fit
    // transform only; device rotation does not re-layout the preview).
    // Per-element rotation keeps the rails fixed in screen position while only the glyphs
    // spin to read upright.
    val uiRotationDeg = deviceUiRotationState.snappedDegrees
    val uiRotationDegSmooth = deviceUiRotationState.smoothDegrees

    var calibrateOverlayActive by remember { mutableStateOf(false) }
    BackHandler(enabled = calibrateOverlayActive) {
        calibrateOverlayActive = false
    }
    var calibratePendingInitialBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun openCalibrateFromPreviewFrame() {
        val gl = previewHostSlot.view
        if (gl == null) {
            captureScope.pnsShowSnackbar(snackbarHostState, "Preview not ready.")
            return
        }
        captureScope.launch(Dispatchers.IO) {
            val bmp = controller.grabPreviewFrameBitmap(gl)
            if (bmp == null) {
                withContext(Dispatchers.Main) {
                    captureScope.pnsShowSnackbar(snackbarHostState, "Could not grab preview frame.")
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                calibratePendingInitialBitmap = bmp
                calibrateOverlayActive = true
            }
        }
    }

    LaunchedEffect(adbCalibrateGrabSmoke, controller) {
        if (!adbCalibrateGrabSmoke) return@LaunchedEffect
        PnsAdbLog.i(context, "calibrate preview grab smoke: polling GLSurfaceView")
        repeat(90) {
            delay(400)
            val gl = previewHostSlot.view
            if (gl != null && gl.width > 0 && gl.height > 0) {
                // RENDERMODE_WHEN_DIRTY: nudge a frame before PixelCopy.
                gl.requestRender()
                // [grabPreviewFrameBitmap] posts PixelCopy completion to the main looper and awaits
                // on the caller thread — must not run that await on Main or we deadlock (Milestone 6
                // `calibrate preview frame grab ok` gate never fired on device).
                val bmp =
                    withContext(Dispatchers.IO) {
                        controller.grabPreviewFrameBitmap(gl)
                    }
                if (bmp != null) {
                    bmp.recycle()
                    return@LaunchedEffect
                }
            }
        }
        PnsAdbLog.e(context, "calibrate preview grab smoke FAILED (no successful grab)")
    }

    val readoutMenuSnapshot =
        remember(selectedCameraId) {
            controller.readoutMenuSnapshot()
        }
    val readoutAeCoupling =
        remember(previewReadoutIso, previewReadoutExposureNs, selectedCameraId) {
            controller.readoutAeCoupling()
        }
    LaunchedEffect(primaryPhoto, selectedFps, settings.videoShutterAngle) {
        if (!primaryPhoto) {
            controller.applyVideoShutterAnglePreset(settings.videoShutterAngleEnum(), selectedFps)
        }
    }

    LaunchedEffect(
        previewContentPx,
        previewFinderPx,
        previewBufferSize,
        sensorOrientationDeg,
    ) {
        val raw = previewBufferSize
        val disp =
            raw?.let {
                val (dw, dh) = previewBufferDimensionsForDisplay(it.width, it.height, sensorOrientationDeg)
                IntSize(dw, dh)
            }
        FaceHudOverlayMapping.logViewportDiagOnce(
            finderPx = previewFinderPx,
            contentPx = previewContentPx,
            bufferSize = disp,
            coverCrop = PREVIEW_FINDER_CONTAIN,
        )
    }
    val hudStScratch = remember { FloatArray(16) }
    val hudStReady =
        controller.lutPreviewRendererForDual?.readSurfaceTransformMatrix(hudStScratch) == true
    val eyeMarksView =
        remember(
            eyeMarksBuffer,
            previewContentPx,
            previewBufferSize,
            sensorOrientationDeg,
            faceOverlayCalibration,
            hudStReady,
        ) {
            val buf = previewBufferSize
            val contentW = previewContentPx.width
            val contentH = previewContentPx.height
            val tileCenterX = contentW / 2f
            val tileCenterY = contentH / 2f
            if (buf == null || contentW <= 0 || contentH <= 0) {
                emptyList()
            } else {
                eyeMarksBuffer.map { m ->
                    val (xOut, vy) =
                        FaceHudOverlayMapping.mapBufferPointToTile(
                            bufferX = m.position.x,
                            bufferY = m.position.y,
                            tileW = contentW,
                            tileH = contentH,
                            bufferW = buf.width,
                            bufferH = buf.height,
                            coverCrop = PREVIEW_FINDER_CONTAIN,
                            surfaceTransformColumnMajor4x4 = if (hudStReady) hudStScratch else null,
                        )
                    FaceOverlayCalibration.applyViewMark(
                        EyeMark(
                            Offset(xOut, vy),
                            m.confidence,
                            m.trackingLocked,
                            m.referenceTrack,
                        ),
                        faceOverlayCalibration,
                        tileCenterX,
                        tileCenterY,
                    )
                }
            }
        }

    val faceTrackBoxesView =
        remember(
            faceTrackBoxesBuffer,
            previewContentPx,
            previewBufferSize,
            sensorOrientationDeg,
            faceOverlayCalibration,
            hudStReady,
        ) {
            val buf = previewBufferSize
            val contentW = previewContentPx.width
            val contentH = previewContentPx.height
            val tileCenterX = contentW / 2f
            val tileCenterY = contentH / 2f
            if (buf == null || contentW <= 0 || contentH <= 0) {
                emptyList()
            } else {
                faceTrackBoxesBuffer.mapNotNull { box ->
                    val stForHud = if (hudStReady) hudStScratch else null
                    val (l0, t0) =
                        FaceHudOverlayMapping.mapBufferPointToTile(
                            bufferX = box.left,
                            bufferY = box.top,
                            tileW = contentW,
                            tileH = contentH,
                            bufferW = buf.width,
                            bufferH = buf.height,
                            coverCrop = PREVIEW_FINDER_CONTAIN,
                            surfaceTransformColumnMajor4x4 = stForHud,
                        )
                    val (r0, b0) =
                        FaceHudOverlayMapping.mapBufferPointToTile(
                            bufferX = box.right,
                            bufferY = box.bottom,
                            tileW = contentW,
                            tileH = contentH,
                            bufferW = buf.width,
                            bufferH = buf.height,
                            coverCrop = PREVIEW_FINDER_CONTAIN,
                            surfaceTransformColumnMajor4x4 = stForHud,
                        )
                    val left = kotlin.math.min(l0, r0)
                    val right = kotlin.math.max(l0, r0)
                    val top = kotlin.math.min(t0, b0)
                    val bottom = kotlin.math.max(t0, b0)
                    if (right - left < 4f || bottom - top < 4f) return@mapNotNull null
                    val pLt =
                        FaceOverlayCalibration.applyViewPoint(left, top, faceOverlayCalibration, tileCenterX, tileCenterY)
                    val pRb =
                        FaceOverlayCalibration.applyViewPoint(
                            right,
                            bottom,
                            faceOverlayCalibration,
                            tileCenterX,
                            tileCenterY,
                        )
                    FaceTrackBoxView(
                        rect =
                            androidx.compose.ui.geometry.Rect(
                                offset = Offset(kotlin.math.min(pLt.x, pRb.x), kotlin.math.min(pLt.y, pRb.y)),
                                size =
                                    androidx.compose.ui.geometry.Size(
                                        kotlin.math.abs(pRb.x - pLt.x),
                                        kotlin.math.abs(pRb.y - pLt.y),
                                    ),
                            ),
                        trackingLocked = box.trackingLocked,
                    )
                }
            }
        }

    val layoutDirection = LocalLayoutDirection.current
    val previewChromeModifier =
        Modifier
            .fillMaxSize()
            .background(PnsColors.Charcoal)
            .padding(
                start = padding.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                if (!chrome.volumeKeysCapture) return@onPreviewKeyEvent false
                when (it.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                        when {
                            commandDialMode == CommandDialMode.BKT && controller.canCaptureBracketBurst() ->
                                onBracketBurst(HudSettings.loadBracketPattern(context))
                            controller.canCaptureStill() && !afShutterGateActiveForUi -> triggerStillCapture()
                            else ->
                                captureScope.pnsShowSnackbar(
                                    snackbarHostState,
                                    "DNG/BKT: switch preview to 119 fps or below (RAW session); BKT needs dial on BKT",
                                    longDuration = true,
                                )
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val next = !isRecording
                        onRecordingChange(next)
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            if (next) "Recording started (volume down)" else "Recording stopped (volume down)",
                            longDuration = false,
                        )
                        true
                    }
                    else -> false
                }
            }

    // Preview tile: **3:4** width:height (4:3 sensor upright — long edge vertical). Chrome scroll stack fills remaining height.
    PreviewEngineChromeShell(modifier = previewChromeModifier) {
        var frontRearSpotlightStep by remember { mutableIntStateOf(-1) }
        val spotlightCtx = context.applicationContext
        LaunchedEffect(Unit) {
            if (!PnsUiHintsStore.hasSeenFrontRearSpotlight(spotlightCtx)) {
                frontRearSpotlightStep = 0
            }
        }
        val showBottomTrayForSpotlight =
            chrome.showOnScreenShutter || lastGalleryUri != null || settings.showCommandDial
        if (frontRearSpotlightStep in 0..2) {
            val spotlightBody =
                when (frontRearSpotlightStep) {
                    0 ->
                        "On the live preview (not the tray or side tiles), swipe up for the front camera " +
                            "and swipe down to return to rear cameras. System edge back/home gestures can steal tall vertical drags — " +
                            "use Capture and tools → Front / Rear if a swipe fails."
                    1 ->
                        if (showBottomTrayForSpotlight) {
                            "When the bottom shutter strip is visible, use it for Photo vs Video and the on-screen shutter."
                        } else {
                            "Enable the on-screen shutter or mode strip in Settings if you want Photo vs Video controls on the bottom."
                        }
                    2 ->
                        if (settings.showCommandDial) {
                            "The Mode dial in the tray switches P, Auto, S, M, H, BKT, and more (HUD can hide it)."
                        } else {
                            "Turn on Show command dial in HUD settings to open P / Auto / S / M from the tray."
                        }
                    else -> ""
                }
            AlertDialog(
                onDismissRequest = {
                    PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                    frontRearSpotlightStep = -1
                },
                title = {
                    Text("Preview quick tour", color = Color.White)
                },
                text = {
                    Text(
                        spotlightBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                },
                confirmButton = {
                    if (frontRearSpotlightStep < 2) {
                        TextButton(
                            onClick = { frontRearSpotlightStep = frontRearSpotlightStep + 1 },
                        ) {
                            Text("Next", color = PnsColors.PhotoOrange)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                                frontRearSpotlightStep = -1
                            },
                        ) {
                            Text("Got it", color = PnsColors.PhotoOrange)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            PnsUiHintsStore.markFrontRearSpotlightSeen(spotlightCtx)
                            frontRearSpotlightStep = -1
                        },
                    ) {
                        Text("Skip", color = Color.White.copy(alpha = 0.75f))
                    }
                },
                containerColor = PnsColors.Charcoal,
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Top → bottom: inset band, finder, readout chips, 7×3 quick settings (+ focal row), shutter tray.
            // Canonical spec: docs/preview-chrome-layout-style-guide.md + .cursor/rules/preview-chrome-ui-lock.mdc
            val topInsetBand = padding.calculateTopPadding()
            val frontCameraActive =
                remember(selectedCameraId) {
                    isPreviewFrontCameraActive(cameraManager, selectedCameraId)
                }
            val dualVideoSelfieRing =
                !primaryPhoto &&
                    commandDialMode == CommandDialMode.Dual &&
                    selectedFps <= DualVideoRecordingController.V1_TARGET_FPS
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(topInsetBand)
                        .background(PnsColors.Charcoal),
            ) {
                PreviewTopStatusBar(
                    statusLine = previewStatusLine,
                    showTimecode = settings.showTimecode,
                    videoPrimary = !primaryPhoto,
                    isRecording = isRecording,
                    selectedFps = selectedFps,
                    recordStartElapsedMs = recordStartElapsedMs,
                    sampleAudioAmplitude = { controller.peekInAppVideoAudioAmplitude() },
                    modifier = Modifier.fillMaxSize(),
                )
                PreviewSelfieRingIndicator(
                    visible = frontCameraActive || dualVideoSelfieRing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PreviewChromeSectionDivider()
            // Share vertical space with the chrome rail ([PreviewChromeFinderFlexWeight] : rail).
            // Target **width / height = 3 / 4**; when the slot is tall enough, use full width and
            // exact height (no side letterbox). Otherwise fit inside the slot without clipping.
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(PreviewChromeFinderFlexWeight)
                        .fillMaxWidth()
                        // Keep preview + overlays from painting into the chrome below when collapsed.
                        .clip(RectangleShape),
            ) {
                val targetAspect = 3f / 4f // width / height
                val idealTileH = maxWidth / targetAspect
                val tileW: Dp
                val tileH: Dp
                if (idealTileH <= maxHeight) {
                    tileW = maxWidth
                    tileH = idealTileH
                } else if (maxWidth / maxHeight >= targetAspect) {
                    tileW = maxHeight * targetAspect
                    tileH = maxHeight
                } else {
                    tileW = maxWidth
                    tileH = maxWidth / targetAspect
                }
                val bandAlignment =
                    if (idealTileH <= maxHeight) {
                        // Slack sits toward the status bar; preview sits just above the readout strip.
                        Alignment.BottomCenter
                    } else {
                        Alignment.Center
                    }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = bandAlignment,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(tileW)
                                .height(tileH),
                    ) {
                        PreviewMainViewport(
                            modifier = Modifier.fillMaxSize(),
                            centerViewSize = centerViewSize,
                            onCenterViewSize = { centerViewSize = it },
                            onPreviewTilePx = { previewTilePx = it },
                            onPreviewContentSized = { finder, content ->
                                previewFinderPx = finder
                                previewContentPx = content
                            },
                            previewHostSlot = previewHostSlot,
                            controller = controller,
                            uiRotationDeg = uiRotationDeg,
                            uiRotationDegSmooth = uiRotationDegSmooth,
                            hudState = hudState,
                            compositionGuide = compositionGuide,
                            previewBufferSize = previewBufferSize,
                            isRecording = isRecording,
                            eyeMarks = eyeMarksView,
                            faceTrackBoxes = faceTrackBoxesView,
                            focusRequester = focusRequester,
                            tapPreviewToCapture = effectiveTapPreviewToCapture,
                            manualFocusRackEnabled = manualFocusUi.rackActive,
                            manualFocusRackDiopters = manualFocusUi.rackDiopters,
                            manualFocusRackMaxDiopters = manualFocusUi.rackMaxDiopters,
                            onManualFocusRackDiopters = manualFocusUi.onRackDiopters,
                            macroLocksCameraSwipe = macroLensLocked,
                            liveChartCornerOverlay = chrome.liveChartCornerOverlay,
                            chartCorners = chartCorners,
                            onChartCornersChange = { chartCorners = it },
                            liveChartRows = liveChartTarget.rows,
                            liveChartCols = liveChartTarget.cols,
                            sensorOrientationDeg = sensorOrientationDeg,
                            staticPreviewRotationDeg = chrome.staticPreviewRotationDeg,
                            previewHistogramBins = previewHistogramBins,
                            highlightClipZebraFrame = highlightClipZebraFrame,
                            previewMirrorHorizontally = controller.previewMirrorHorizontally(),
                            onSwitchToFrontCamera = onSwitchToFrontCamera,
                            onSwitchToRearCamera = onSwitchToRearCamera,
                            onCaptureDng = { triggerStillCapture() },
                            afShutterGateBlocksTapCapture = afShutterGateActiveForUi,
                            commandDialMode = commandDialMode,
                            videoPrimaryPreview = !primaryPhoto,
                            selectedFps = selectedFps,
                            enableResearchDcgHdr =
                                settings.enableResearchDcgHDR || adbAutomationVideoDcg,
                            adbForcePowerThermalOverlay = adbForcePowerThermalOverlay,
                            videoEncodeSize = videoEncodeSize,
                            adbStorageAvailableBytes = adbStorageAvailableBytes,
                            adbAutomationVideoDcg = adbAutomationVideoDcg,
                            adbAutomationVideoTenBit = adbAutomationVideoTenBit,
                            adbAutomationVideoRawSec = adbAutomationVideoRawSec,
                            rawVideoLane =
                                settings.videoEncodeLane == VideoEncodeLane.Raw ||
                                    adbAutomationVideoRawSec > 0,
                            eyeOverlayCalibratorActive = eyeOverlayCalibratorActive,
                            eyeOverlayMarkerSizeScale = faceOverlayCalibration.markerSizeScale,
                        )
                        if (eyeOverlayCalibratorActive) {
                            FaceOverlayCalibratorPanel(
                                calibration = faceOverlayCalibration,
                                onCalibrationChange = onFaceOverlayCalibrationChange,
                                onDone = onEyeOverlayCalibratorDone,
                                onReset = {
                                    onFaceOverlayCalibrationChange(FaceOverlayCalibration.Default)
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .fillMaxWidth(0.62f),
                            )
                        }
                        if (commandDialMode == CommandDialMode.Qr) {
                            PreviewQrScanOverlay(
                                decodedText = qrDecodedText,
                                action = qrAction,
                                onOpen = {
                                    val viewUri = qrAction as? QrScanAction.ViewUri ?: return@PreviewQrScanOverlay
                                    val text = qrDecodedText
                                    val ok = QrScanResultActions.launchViewUri(context, viewUri.uri)
                                    Log.i(
                                        QrCodeAnalyzer.TAG,
                                        "open userInitiated=true uri=${viewUri.uri.take(120)} ok=$ok",
                                    )
                                    if (!ok && text != null) {
                                        captureScope.pnsShowSnackbar(
                                            snackbarHostState,
                                            "No app to open this",
                                            clipboardDetail = text,
                                            clipboardAppContext = context.applicationContext,
                                        )
                                    }
                                },
                                onCopy = {
                                    qrDecodedText?.let {
                                        QrScanResultActions.copyToClipboard(context.applicationContext, it)
                                        captureScope.pnsShowSnackbar(snackbarHostState, "Copied")
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
                        if (chrome.liveChartCornerOverlay) {
                            ChartCalibrationApplyOverlay(
                                modifier = Modifier.fillMaxSize(),
                                overlayEnabled = true,
                                cornerCount = chartCorners.size,
                                onApply = { applyLiveChartCalibration() },
                                onAutoDetectCorners = { runLiveChartAutoDetect() },
                                onExitCalibration = {
                                    chromePrefs.updateMutate {
                                        it.copy(liveChartCornerOverlay = false)
                                    }
                                    chartCorners = emptyList()
                                },
                            )
                        }
                    }
                }
            }
            PreviewChromeSectionDivider()
            PreviewReadoutStrip(
                iso = previewReadoutIso,
                exposureNs = previewReadoutExposureNs,
                awbMode = previewReadoutAwbMode,
                measuredFps = measuredFps,
                stillCaptureJpegCompanion = chrome.stillCaptureJpegCompanion,
                sessionJpegCompanionReady = previewJpegCompanion,
                composedStillIntent = composedStillIntent,
                menu = readoutMenuSnapshot,
                readoutAeCoupling = readoutAeCoupling,
                videoShutterAngleLabel =
                    if (!primaryPhoto) settings.videoShutterAngleEnum().chipLabel() else null,
                fpsOptions = fpsOptions,
                fpsTargetEditable = fpsTargetEditable,
                videoResSelectorVisible = fpsTargetEditable,
                videoEncodeSizes = videoEncodeSizes,
                videoEncodeShortLabel = videoEncodeShortLabel,
                onPickVideoEncodeSize = onPickVideoEncodeSize,
                onPickIso = { iso -> controller.setReadoutManualIso(iso) },
                onPickIsoBand = { band -> controller.setReadoutIsoBand(band) },
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
                onComposedStillIntentChange = onComposedStillIntentChange,
                onGrayCardWb = {
                    controller.applyGrayCardWhiteBalance { err ->
                        if (err != null) {
                            captureScope.pnsShowSnackbar(snackbarHostState, err, longDuration = true)
                        } else {
                            captureScope.pnsShowSnackbar(
                                snackbarHostState,
                                "Custom WB from center chroma (preview shader + AWB off).",
                                longDuration = false,
                            )
                        }
                    }
                },
                focalMapCalibratingHint = focalMapCalibratingHint,
                capturePipelineHint = capturePipelineHint,
                lastStillPostReadout = lastStillPostReadout,
                primaryPhoto = primaryPhoto,
                focusChipValue = focusChipValue,
                onFocusChipClick = { focusModePickerOpen = true },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape),
            )
            if (showVideoFormatPicker) {
                VideoFormatPickerSheet(
                    formats = videoFormatCatalog,
                    selectedFormat = selectedInAppVideoFormat,
                    videoTruth = videoFormatTruth,
                    chrome = chromePrefs.current,
                    patchChrome = { transform -> chromePrefs.update(transform(chromePrefs.current)) },
                    onSelect = { format ->
                        val nextChrome =
                            InAppVideoFormatSelection.chromeAfterSelect(chromePrefs.current, format)
                        chromePrefs.update(nextChrome)
                        if (format.frameRate != selectedFps) {
                            onSetFps(format.frameRate)
                        }
                        val size = format.resolution
                        if (size.width > 0 && size.height > 0) {
                            onPickVideoEncodeSize(size)
                        }
                        Log.i(
                            "PNS.ChromeUx",
                            "videoFormatPick=${format.getLabel()} ${size.width}x${size.height}@${format.frameRate}",
                        )
                        showVideoFormatPicker = false
                    },
                    onDismiss = { showVideoFormatPicker = false },
                )
            }
            if (focusModePickerOpen) {
                PreviewFocusModePickerDialog(
                    onDismiss = { focusModePickerOpen = false },
                    menuSelections = controller.previewFocusMenuSelections(),
                    current = previewFocusSelection,
                    onPick = { pick ->
                        commandDialMode =
                            macroFocusDialCoupling.applyFocusPick(
                                pick = pick,
                                currentDial = commandDialMode,
                                setFocus = { controller.setPreviewFocusSelection(it) },
                            )
                        HudSettings.saveCommandDialMode(context, commandDialMode)
                    },
                )
            }
            PreviewChromeSectionDivider()
            PreviewRightRail(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(PreviewChromeRailFlexWeight)
                        .clip(RectangleShape),
                uiRotationDeg = uiRotationDeg,
                cameraIds = cameraIds,
                onApplyFocalMmSlot = onApplyFocalMmSlotGuarded,
                onOpenDeveloperMenu = onOpenDeveloperMenu,
                seedOpenAboutSheet = seedOpenAboutSheet,
                fpsOptions = fpsOptions,
                selectedFps = selectedFps,
                onSetFps = onSetFps,
                hudState = hudState,
                compositionGuide = compositionGuide,
                chromePrefs = chromePrefs,
                onPickFirstCamera = onPickFirstCamera,
                onSwitchToFrontCamera = onSwitchToFrontCamera,
                onSwitchToRearCamera = onSwitchToRearCamera,
                selectedCameraId = selectedCameraId,
                focalCrop = focalCrop,
                onCaptureDng = { triggerStillCapture() },
                onBracketBurst = onBracketBurst,
                canCaptureRawStill = controller.canCaptureStill() && !afShutterGateActiveForUi,
                canCaptureBracketBurst = controller.canCaptureBracketBurst(),
                commandDialMode = commandDialMode,
                onCalibrateFromPreviewFrame = { openCalibrateFromPreviewFrame() },
                previewJpegCompanion = previewJpegCompanion,
                rawStillNotReadyReason = controller.rawStillNotReadyReason(),
                fineLocationGranted = fineLocationGranted,
                onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                onRequestLocationForGeotag = onRequestLocationForGeotag,
                fpsTargetEditable = fpsTargetEditable,
                onKickPreviewPipeline = { controller.kickPreviewPipelineRestart() },
                onOpenFocusModePicker = { focusModePickerOpen = true },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onPictureProfileImaging = { imaging ->
                    onComposedStillIntentChange(
                        ComposedStillIntent.fromLegacyImagingProfile(imaging, jpegCompanionOn = true),
                    )
                    HudSettings.saveImagingProfile(context, imaging)
                    controller.kickPreviewPipelineRestart()
                },
                onApplyWorkflowPreset = onApplyWorkflowPreset,
            )
            val showBottomTray =
                chrome.showOnScreenShutter || lastGalleryUri != null || settings.showCommandDial
            if (showBottomTray) {
                PreviewChromeSectionDivider()
                PnsGestureExclusionBottomBand(modifier = Modifier.fillMaxWidth()) {
                    PreviewBottomCaptureTray(
                        context = context,
                        lastGalleryUri = lastGalleryUri,
                        onExternalGalleryViewerLaunched = onExternalGalleryViewerLaunched,
                        onBespokeGalleryChange = onBespokeGalleryChange,
                        showOnScreenShutter = chrome.showOnScreenShutter,
                        canCaptureRawStill = controller.canCaptureStill() && !afShutterGateActiveForUi,
                        onCaptureDng = { triggerStillCapture() },
                        isRecording = isRecording,
                        onRecordingChange = onRecordingChange,
                        onSetFps = onSetFps,
                        selectedCameraId = selectedCameraId,
                        primaryPhoto = primaryPhoto,
                        onPrimaryPhotoChange = onPrimaryPhotoChange,
                        showVideoFormatFab = PreviewTrayVideoChrome.showVideoFormatFab(primaryPhoto),
                        onOpenVideoFormat = { showVideoFormatPicker = true },
                        selectedFps = selectedFps,
                        shootingModesSlot =
                        if (settings.showCommandDial) {
                            {
                                var modeMenuExpanded by remember { mutableStateOf(false) }
                                Box(contentAlignment = Alignment.Center) {
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
                                    PreviewCommandDialDropdownMenu(
                                        expanded = modeMenuExpanded,
                                        onDismissRequest = { modeMenuExpanded = false },
                                        primaryPhoto = primaryPhoto,
                                        selectedMode = commandDialMode,
                                        onModeSelected = { mode ->
                                            commandDialMode =
                                                macroFocusDialCoupling.applyDialChange(
                                                    previousDial = commandDialMode,
                                                    newDial = mode,
                                                    currentFocus = previewFocusSelection,
                                                    menuSelections =
                                                        controller.previewFocusMenuSelections(),
                                                    setFocus = {
                                                        controller.setPreviewFocusSelection(it)
                                                    },
                                                )
                                            HudSettings.saveCommandDialMode(context, commandDialMode)
                                            modeMenuExpanded = false
                                            Log.i(
                                                "PNS.ChromeUx",
                                                "modeDialPopout=menuSelect mode=${mode.name}",
                                            )
                                        },
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RectangleShape),
                    )
                }
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
                    cameraIdForProfile = selectedCameraId ?: "preview",
                    onProfileSaved = { profile, exposureStops ->
                        hudState.update(
                            CalibrationWorkflow.hudNaturalDefaults(hudState.current),
                        )
                        controller.applyChartCalibrationProfile(profile, exposureStops)
                        CalibrationWorkflow.logPostApplyParity(context, profile)
                        captureScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Calibration profile saved and applied",
                        )
                    },
                )
            }
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }

/**
 * After an external viewer (e.g. Google Photos) opened from the tray thumb, GLES preview aspect can
 * stay wrong in-process; clearing the task and relaunching [Activity] matches a cold start.
 */
private fun restartMainActivityCold(activity: Activity) {
    val src = activity.intent
    val next =
        Intent(activity, activity.javaClass).apply {
            src?.let { old ->
                if (old.action != null) {
                    action = old.action
                }
                old.extras?.let { putExtras(it) }
                if (old.data != null) {
                    data = old.data
                }
            }
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }
    PnsAdbLog.i(
        activity.applicationContext,
        "preview external viewer return -> restartMainActivityCold",
    )
    activity.startActivity(next)
    activity.finishAffinity()
}

private class PreviewHostSlot {
    var view: GLSurfaceView? = null
}

@Composable
private fun PreviewMainViewport(
    modifier: Modifier,
    centerViewSize: IntSize,
    onCenterViewSize: (IntSize) -> Unit,
    onPreviewTilePx: (IntSize) -> Unit,
    onPreviewContentSized: (finderPx: IntSize, contentPx: IntSize) -> Unit,
    previewHostSlot: PreviewHostSlot,
    controller: PreviewController,
    uiRotationDeg: Float,
    uiRotationDegSmooth: Float,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    previewBufferSize: Size?,
    isRecording: Boolean,
    eyeMarks: List<EyeMark>,
    faceTrackBoxes: List<FaceTrackBoxView>,
    focusRequester: FocusRequester,
    tapPreviewToCapture: Boolean,
    liveChartCornerOverlay: Boolean,
    chartCorners: List<Offset>,
    onChartCornersChange: (List<Offset>) -> Unit,
    liveChartRows: Int,
    liveChartCols: Int,
    sensorOrientationDeg: Int?,
    /** [PreviewChromePreferences.staticPreviewRotationDeg] — user Spin (preview). */
    staticPreviewRotationDeg: Int,
    /** Latest luma histogram for overlay (null when disabled or not yet sampled). */
    previewHistogramBins: IntArray?,
    highlightClipZebraFrame: HighlightClipZebraFrame?,
    previewMirrorHorizontally: Boolean,
    onSwitchToFrontCamera: () -> Unit,
    onSwitchToRearCamera: () -> Unit,
    onCaptureDng: () -> Unit,
    /** When true, tap-to-shoot must not fire (matches tray shutter disabled during AF gate). */
    afShutterGateBlocksTapCapture: Boolean,
    commandDialMode: CommandDialMode,
    manualFocusRackEnabled: Boolean = false,
    manualFocusRackDiopters: Float = 0f,
    manualFocusRackMaxDiopters: Float = 8f,
    onManualFocusRackDiopters: (Float) -> Unit = {},
    /** Disables vertical finder swipes (front/rear) while macro mode locks ultra-wide. */
    macroLocksCameraSwipe: Boolean = false,
    /** When true, GLES preview uses [HudSettings.videoLut] even before record starts. */
    videoPrimaryPreview: Boolean,
    selectedFps: Int,
    enableResearchDcgHdr: Boolean,
    adbForcePowerThermalOverlay: Boolean,
    videoEncodeSize: Size,
    adbStorageAvailableBytes: Long? = null,
    adbAutomationVideoDcg: Boolean = false,
    adbAutomationVideoTenBit: Boolean = false,
    adbAutomationVideoRawSec: Int = 0,
    rawVideoLane: Boolean = false,
    eyeOverlayCalibratorActive: Boolean = false,
    eyeOverlayMarkerSizeScale: Float = 1f,
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
    //   * Aspect handling: once `previewBufferSize` is known, the inner GLES box uses portrait
    //     display aspect and **contain** sizing (full stream visible, letterboxed in the finder).
    //     While `previewBufferSize` is unknown, the GLES view fills the parent.
    //
    //   * Fixed default buffer orientation: [effectivePreviewStaticRotationDeg] with stored
    //     nominal **90°** maps to **0°** effective rotation (see [PreviewLayoutOrientation]).
    //
    // Layout tree:
    //
    //     centerView (BoxWithConstraints, black background)
    //       └ rotated content box (centered, sized to PRE-rotation footprint, graphicsLayer
    //         applies the static rotation; buffer-locked overlays live here so they track the
    //         rotated image)
    //         ├ TextureView (fillMaxSize → matches buffer aspect once known → no distortion)
    //         ├ CompositionGuideOverlay (rule-of-thirds, locked to the buffer)
    //         ├ FaceTrackOverlay (face bounds → buffer → view)
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
        val context = LocalContext.current
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val reportPreviewTilePx = rememberUpdatedState(onPreviewTilePx)
        val reportPreviewContentSized = rememberUpdatedState(onPreviewContentSized)
        val hudForPeakingUniforms = rememberUpdatedState(hudState.current)
        val forcePeakingForManualVideo = remember { mutableStateOf(false) }
        SideEffect {
            forcePeakingForManualVideo.value =
                isRecording && controller.peekManualFocusActive()
        }
        val lutPreviewRenderer =
            remember(mainHandler, controller) {
                LutCameraPreviewRenderer(
                    assetLoader = { path ->
                        context.assets.open(path).bufferedReader().use { it.readText() }
                    },
                    mainHandler = mainHandler,
                    readoutWbRgb = { controller.previewShaderWbRgbForGl() },
                    focusPeakingUniforms = {
                        FocusPeakingGlUniforms.fromHud(
                            hudForPeakingUniforms.value,
                            forceForManualVideo = forcePeakingForManualVideo.value,
                        )
                    },
                    onSurfaceTextureAvailable = { st, w, h ->
                        controller.onSurfaceTextureAvailable(st, w, h)
                    },
                    onSurfaceTextureSizeChanged = { w, h ->
                        controller.onSurfaceTextureSizeChanged(w, h)
                    },
                    onSurfaceTextureDestroyed = { st ->
                        controller.onSurfaceTextureDestroyed(st)
                    },
                    onPreviewFramePresented = { controller.onTextureUpdated() },
                )
            }
        var glSurfaceHost by remember { mutableStateOf<GLSurfaceView?>(null) }
        SideEffect {
            controller.glSurfaceHostForDual = glSurfaceHost
            controller.lutPreviewRendererForDual = lutPreviewRenderer
        }
        LaunchedEffect(
            commandDialMode,
            videoPrimaryPreview,
            selectedFps,
            isRecording,
            glSurfaceHost,
            lutPreviewRenderer,
        ) {
            val dual =
                videoPrimaryPreview &&
                    commandDialMode == CommandDialMode.Dual &&
                    selectedFps <= DualVideoRecordingController.V1_TARGET_FPS
            val host = glSurfaceHost ?: return@LaunchedEffect
            host.queueEvent {
                lutPreviewRenderer.setDualSplitEnabled(dual) { fst, w, h ->
                    mainHandler.post { controller.onDualFrontSurfaceTextureReady(fst, w, h) }
                }
            }
            if (!dual) {
                host.queueEvent {
                    lutPreviewRenderer.setEncoderCompositeSink(null, record = false)
                }
                if (commandDialMode != CommandDialMode.Dual) {
                    controller.closeDualFrontCamera()
                }
            }
        }
        LaunchedEffect(isRecording, commandDialMode, videoPrimaryPreview, selectedFps, glSurfaceHost, lutPreviewRenderer) {
            val dual =
                videoPrimaryPreview &&
                    commandDialMode == CommandDialMode.Dual &&
                    selectedFps <= DualVideoRecordingController.V1_TARGET_FPS
            val host = glSurfaceHost ?: return@LaunchedEffect
            if (dual && isRecording) {
                controller.ensureDualFrontOpenForRecord()
                controller.maybeArmDualStackedPreview()
            }
            if (!isRecording || !dual) {
                controller.markDualGlRecordArmed(false)
                host.queueEvent {
                    lutPreviewRenderer.setEncoderCompositeSink(controller.dualVideoEncoderSink, false)
                }
                return@LaunchedEffect
            }
            var wait = 0
            while (
                (!controller.peekDualFrontSessionReady() ||
                    (!controller.peekInAppVideoRecorderStarted() &&
                        !controller.peekInAppVideoMcEncoderRecording()) ||
                    controller.getInAppVideoRecordingSurface() == null) &&
                    wait < 240
            ) {
                if (
                    controller.peekInAppVideoRecorderPresent() &&
                        !controller.peekInAppVideoRecorderStarted() &&
                        !controller.peekInAppVideoMcEncoderRecording() &&
                        wait >= 4
                ) {
                    controller.maybeStartInAppVideoRecorder()
                }
                delay(50)
                wait++
            }
            val size = DualVideoRecordingController.compositeRecordSize()
            var bindWait = 0
            while (
                controller.getInAppVideoRecordingSurface() == null &&
                    bindWait < 80
            ) {
                delay(50)
                bindWait++
            }
            var bindAttempts = 0
            while (!controller.peekDualEncoderSinkReady() && bindAttempts < 20) {
                controller.bindDualEncoderSurface(size.width, size.height)
                delay(100)
                bindAttempts++
            }
            host.queueEvent {
                lutPreviewRenderer.setEncoderCompositeSink(
                    controller.dualVideoEncoderSink,
                    record = true,
                )
            }
            host.requestRender()
            val armed = controller.peekDualEncoderSinkReady()
            controller.markDualGlRecordArmed(armed)
            Log.i(
                DualVideoRecordingController.TAG,
                "dualGlRecordArmed frontReady=${controller.peekDualFrontSessionReady()} " +
                    "recorder=${controller.peekInAppVideoRecorderStarted()} " +
                    "mcRec=${controller.peekInAppVideoMcEncoderRecording()} waitMs=${wait * 50}",
            )
        }
        val hudForPreviewLut = rememberUpdatedState(hudState.current)
        // HFR record: skip heavy video LUT on GLES so preview keeps up with HS preview frames.
        val previewLutCatalog =
            if (isRecording && videoPrimaryPreview && selectedFps >= 120) {
                LutCatalog.None
            } else {
                PreviewLutSelection.activeCatalog(
                    isRecording = isRecording,
                    videoPrimary = videoPrimaryPreview,
                    hud = hudForPreviewLut.value,
                )
            }
        // HFR finder: WHEN_DIRTY + requestRender per monitor frame (avoid CONTINUOUSLY strobing).
        LaunchedEffect(isRecording, videoPrimaryPreview, selectedFps, glSurfaceHost) {
            val glv = glSurfaceHost ?: return@LaunchedEffect
            glv.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            glv.requestRender()
        }
        LaunchedEffect(isRecording, videoPrimaryPreview, selectedFps, glSurfaceHost, lutPreviewRenderer) {
            val host = glSurfaceHost ?: return@LaunchedEffect
            val hfrRec =
                isRecording &&
                    videoPrimaryPreview &&
                    selectedFps >= 120 &&
                    controller.peekWantsMediaCodecVideoRecord()
            controller.onHfrInterleavedComposeRecordState(recording = hfrRec)
            // Enable as soon as HFR record starts — monitor camera opens later during HS
            // session configure; waiting on peekHfrEncoderOnlyMonitorActive() left the finder
            // on a starved primary SurfaceTexture (frozen frame) for H.264/HEVC @ 120.
            host.queueEvent { lutPreviewRenderer.setHfrYuvMonitorEnabled(hfrRec) }
            host.requestRender()
        }
        LaunchedEffect(
            previewLutCatalog.name,
            isRecording,
            videoPrimaryPreview,
            lutPreviewRenderer,
            glSurfaceHost,
        ) {
            val lut3d = previewLutCatalog.load(BuiltInLuts.DEFAULT_SIZE)
            lutPreviewRenderer.setLut(lut3d)
            val enabled = LutShaderProgram.BypassPolicy.lutEnabledUniform(lut3d) > 0.5f
            Log.i(
                "PNS.LutPreview",
                "previewLut=${previewLutCatalog.name} recording=$isRecording " +
                    "videoPrimary=$videoPrimaryPreview lutEnabled=$enabled",
            )
            glSurfaceHost?.requestRender()
        }
        val peakHud = hudState.current
        LaunchedEffect(
            peakHud.focusPeakingColor,
            peakHud.focusPeakingStrength,
            forcePeakingForManualVideo,
            glSurfaceHost,
        ) {
            glSurfaceHost?.requestRender()
        }
        DisposableEffect(controller, glSurfaceHost) {
            val listener = Runnable { glSurfaceHost?.requestRender() }
            controller.setReadoutWbShaderChangedListener(listener)
            onDispose { controller.setReadoutWbShaderChangedListener(null) }
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, glSurfaceHost, lutPreviewRenderer, controller) {
            val v = glSurfaceHost
            if (v == null) {
                onDispose { }
            } else {
                lutPreviewRenderer.attachView(v)
                val obs =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> v.onResume()
                            Lifecycle.Event.ON_PAUSE -> v.onPause()
                            else -> Unit
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(obs)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    v.onResume()
                }
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(obs)
                    val st = lutPreviewRenderer.surfaceTextureOrNull()
                    if (st != null) {
                        controller.onSurfaceTextureDestroyed(st)
                    }
                    val latch = CountDownLatch(1)
                    v.queueEvent {
                        lutPreviewRenderer.releaseGlThread()
                        latch.countDown()
                    }
                    latch.await(2, TimeUnit.SECONDS)
                    v.onPause()
                    lutPreviewRenderer.detachView()
                }
            }
        }

        val parentW = constraints.maxWidth
        val parentH = constraints.maxHeight
        // Negotiated stream size from the controller first — Compose poll can lag and still hold
        // a stale view-sized Size from an early GLES layout callback.
        val buf = controller.previewBufferSize() ?: previewBufferSize
        val bufW = buf?.width ?: 0
        val bufH = buf?.height ?: 0
        val knownBuf = bufW > 0 && bufH > 0 && parentW > 0 && parentH > 0
        val rotationAppliedDeg = effectivePreviewStaticRotationDeg(staticPreviewRotationDeg, false)
        val isQuarterTurn = rotationAppliedDeg == 90 || rotationAppliedDeg == 270

        // Layout footprint: portrait **display** aspect (e.g. 1440/1920). GLES fit uses raw HAL WxH
        // + [SurfaceTexture.getTransformMatrix] via [TexturePreviewFit.composeExternalOesPreviewMatrix].
        val (displayBufW, displayBufH) =
            if (knownBuf) {
                previewBufferDimensionsForDisplay(bufW, bufH, sensorOrientationDeg)
            } else {
                3 to 4
            }
        val footprintAspectWH: Float =
            if (knownBuf) {
                displayBufW.toFloat() / displayBufH.coerceAtLeast(1)
            } else {
                3f / 4f
            }
        val (boxW, boxH) =
            if (knownBuf) {
                TexturePreviewFit.largestAxisAlignedRectWithAspect(
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
                    bufferSize = buf,
                    centerViewSize = IntSize(parentW, parentH),
                    sensorOrientationDeg = sensorOrientationDeg,
                    chromeRotationDegSnapped = uiRotationDeg,
                    chromeRotationDegSmooth = uiRotationDegSmooth,
                ),
            )
        }

        val density = LocalDensity.current
        val preWDp = with(density) { preW.toDp() }
        val preHDp = with(density) { preH.toDp() }

        // Tap and overlays share the measured GL content box (not the full finder band).
        val displayW = preW
        val displayH = preH
        val displayLeft = 0
        val displayTop = 0
        val tapStMatrixScratch = remember { FloatArray(16) }

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
                afShutterGateBlocksTapCapture,
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
                        val stOk = lutPreviewRenderer.readSurfaceTransformMatrix(tapStMatrixScratch)
                        controller.applyTapFocusFromView(
                            vx,
                            vy,
                            displayW,
                            displayH,
                            0f,
                            surfaceTransformColumnMajor4x4 = if (stOk) tapStMatrixScratch else null,
                        )
                    }

                    override fun onFire() {
                        if (!isRecording && tapPreviewToCapture && controller.canCaptureStill() && !afShutterGateBlocksTapCapture) {
                            onCaptureDng()
                        }
                    }

                    override fun onCancel() {}
                }
            }

        // Rotated content container — buffer-aspect-correct PRE-rotation dimensions (or
        // parent-fill while buffer is unknown) plus a graphicsLayer rotation. GLES preview and
        // buffer-locked overlays live inside.
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(preWDp, preHDp)
                    .onSizeChanged {
                        if (it.width > 0 && it.height > 0) {
                            reportPreviewContentSized.value(
                                IntSize(parentW, parentH),
                                IntSize(it.width, it.height),
                            )
                        }
                    }
                    .graphicsLayer {
                        rotationZ = rotationAppliedDeg.toFloat()
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(lutPreviewRenderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            val glv = v as GLSurfaceView
                            val b = controller.previewBufferSize() ?: previewBufferSize
                            lutPreviewRenderer.queueSetGeometry(
                                viewW = glv.width,
                                viewH = glv.height,
                                bufferW = b?.width ?: 0,
                                bufferH = b?.height ?: 0,
                                coverCrop = PREVIEW_FINDER_CONTAIN,
                            )
                            if (glv.width > 0 && glv.height > 0) {
                                mainHandler.post {
                                    reportPreviewTilePx.value(IntSize(glv.width, glv.height))
                                }
                            }
                        }
                    }
                },
                update = { glv ->
                    previewHostSlot.view = glv
                    glSurfaceHost = glv
                    val b = controller.previewBufferSize() ?: previewBufferSize
                    lutPreviewRenderer.queueSetGeometry(
                        viewW = glv.width,
                        viewH = glv.height,
                        bufferW = b?.width ?: 0,
                        bufferH = b?.height ?: 0,
                        coverCrop = PREVIEW_FINDER_CONTAIN,
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
                    faceTrackBoxes = faceTrackBoxes,
                    uiRotationDeg = uiRotationDeg,
                    tapToShootEnabled = tapToShootEnabled,
                    tapShootCallbacks = tapShootCallbacks,
                    manualFocusDragEnabled = false,
                    onManualFocusDragPixels = { controller.nudgeManualFocusFromDrag(it) },
                    manualFocusRackEnabled = manualFocusRackEnabled,
                    manualFocusRackDiopters = manualFocusRackDiopters,
                    manualFocusRackMaxDiopters = manualFocusRackMaxDiopters,
                    onManualFocusRackDiopters = onManualFocusRackDiopters,
                    onRequestVolumeKeyFocus = { focusRequester.requestFocus() },
                    showHorizonLevel = false, // drawn outside the rotated box (gravity-locked)
                    showVideoTallyPip = false, // tally pip is chrome; drawn outside the rotated box
                    previewHistogramBins = previewHistogramBins,
                    highlightClipZebraFrame = highlightClipZebraFrame,
                    previewBufferWidthPx = bufW,
                    previewBufferHeightPx = bufH,
                    previewMirrorHorizontally = previewMirrorHorizontally,
                    previewCoverCrop = PREVIEW_FINDER_CONTAIN,
                    liveChartCornerOverlay = liveChartCornerOverlay,
                    macroLocksCameraSwipe = macroLocksCameraSwipe,
                    onSwitchToFrontCamera = onSwitchToFrontCamera,
                    onSwitchToRearCamera = onSwitchToRearCamera,
                    eyeOverlayCalibratorActive = eyeOverlayCalibratorActive,
                    eyeOverlayMarkerSizeScale = eyeOverlayMarkerSizeScale,
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
        val powerThermalContext =
            remember(
                videoPrimaryPreview,
                isRecording,
                selectedFps,
                enableResearchDcgHdr,
                adbForcePowerThermalOverlay,
            ) {
                PreviewHighDrainMode.Context(
                    videoPrimary = videoPrimaryPreview,
                    isRecording = isRecording,
                    selectedFps = selectedFps,
                    enableResearchDcgHdr = enableResearchDcgHdr,
                    adbForceOverlay = adbForcePowerThermalOverlay,
                )
            }
        val encodeSizeForStorage =
            remember(videoEncodeSize, previewBufferSize) {
                if (videoEncodeSize.width > 0 && videoEncodeSize.height > 0) {
                    videoEncodeSize
                } else {
                    previewBufferSize ?: Size(1920, 1080)
                }
            }
        if (knownBuf) {
            PreviewStorageRemainingOverlay(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer {
                            rotationZ = uiRotationDeg
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                visible = true,
                videoPrimary = videoPrimaryPreview,
                isRecording = isRecording,
                encodeWidth = encodeSizeForStorage.width,
                encodeHeight = encodeSizeForStorage.height,
                targetFps = selectedFps,
                rawVideoLane = rawVideoLane,
                enableResearchDcgHdr =
                    hudState.current.enableResearchDcgHDR,
                adbPreviewVideoDcg = adbAutomationVideoDcg,
                adbPreviewVideoTenBit = adbAutomationVideoTenBit,
                hudShowStorageRemaining = settings.showStorageRemainingOverlay,
                adbStorageAvailableBytes = adbStorageAvailableBytes,
            )
            PreviewPowerThermalOverlay(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 52.dp)
                        .graphicsLayer {
                            rotationZ = uiRotationDeg
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                visible = true,
                highDrainContext = powerThermalContext,
                hudShowPowerThermal = settings.showPowerThermalOverlay,
            )
        }
    }
}

/** Photo / Video: one circular FAB toggles [primaryPhoto]; icon reflects active mode (52dp, bordered). */
@Suppress("FunctionNaming")
@Composable
private fun PreviewTrayPhotoVideoModeToggleFab(
    primaryPhoto: Boolean,
    isRecording: Boolean,
    onPrimaryPhotoChange: (Boolean) -> Unit,
    onRecordingChange: (Boolean) -> Unit,
) {
    val ring = Color.White.copy(alpha = 0.88f)
    FloatingActionButton(
        onClick = {
            if (primaryPhoto) {
                onPrimaryPhotoChange(false)
            } else {
                if (isRecording) onRecordingChange(false)
                onPrimaryPhotoChange(true)
            }
        },
        modifier =
            Modifier
                .size(52.dp)
                .border(2.dp, ring, CircleShape)
                .semantics {
                    contentDescription =
                        if (primaryPhoto) {
                            "Photo mode active. Tap to switch to video mode."
                        } else {
                            "Video mode active. Tap to switch to photo mode."
                        }
                },
        containerColor =
            if (primaryPhoto) {
                PnsColors.PhotoOrange.copy(alpha = 0.92f)
            } else {
                PnsColors.RecordRed.copy(alpha = 0.88f)
            },
        contentColor = if (primaryPhoto) Color.Black else Color.White.copy(alpha = 0.92f),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = if (primaryPhoto) Icons.Outlined.Image else Icons.Outlined.Videocam,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Bottom tray: gallery (+ video format FAB), centered shutter, Photo/Video + mode dial (end). */
@Suppress("FunctionNaming")
@Composable
private fun PreviewBottomCaptureTray(
    context: Context,
    lastGalleryUri: Uri?,
    onExternalGalleryViewerLaunched: () -> Unit,
    onBespokeGalleryChange: (Boolean) -> Unit,
    showOnScreenShutter: Boolean,
    canCaptureRawStill: Boolean,
    onCaptureDng: () -> Unit,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    /** Clamps preview FPS when starting video at HFR (MediaRecorder path requires &lt;120). */
    onSetFps: (Int) -> Unit,
    selectedCameraId: String?,
    /** Photo vs video intent — sibling FABs + center shutter ([CaptureMediaFamily]). */
    primaryPhoto: Boolean,
    onPrimaryPhotoChange: (Boolean) -> Unit,
    showVideoFormatFab: Boolean,
    onOpenVideoFormat: () -> Unit,
    /** Preview FPS target — video record blocked at HFR (≥120). */
    selectedFps: Int,
    modifier: Modifier = Modifier,
    /** Mode dial / Tune FAB — anchored to the bottom-right as the right neighbour of the shutters. */
    shootingModesSlot: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalPnsSnackbarHostState.current
    val traySnackbarScope = rememberCoroutineScope()
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(showOnScreenShutter, primaryPhoto, showVideoFormatFab) {
        if (showOnScreenShutter) {
            Log.i("PNS.ChromeUx", "dualShutter=visible")
            Log.i("PNS.ChromeUx", "trayShutter=centerOfBar photoVideoToggle=combinedToggleFab")
            Log.i(
                "PNS.ChromeUx",
                "trayMediaFamily=${CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto).name}",
            )
            if (showVideoFormatFab) {
                Log.i("PNS.ChromeUx", "trayVideoFormatFab=visible anchor=galleryThumb")
            }
        }
    }

    LaunchedEffect(lastGalleryUri) {
        PnsBitmapGuard.safeRecycle(thumbBitmap, "PreviewTray.thumb")
        thumbBitmap = null
        val u = lastGalleryUri ?: return@LaunchedEffect
        thumbBitmap = loadGalleryThumbnail(context.applicationContext, u)
    }

    DisposableEffect(Unit) {
        onDispose {
            PnsBitmapGuard.safeRecycle(thumbBitmap, "PreviewTray.thumb")
        }
    }

    val edgeSlotWidth = PreviewGalleryThumbSize
    val tapUri = lastGalleryUri
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
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(PreviewGalleryThumbSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                // Check gallery preference
                                if (GalleryPrefs.useBespokeGallery(context)) {
                                    // Show bespoke gallery
                                    onBespokeGalleryChange(true)
                                } else {
                                    // Open external gallery directly
                                    onExternalGalleryViewerLaunched()
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    if (tapUri != null) {
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
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "No capture yet — gallery opens after first save",
                            tint = Color.White.copy(alpha = 0.32f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            if (showVideoFormatFab) {
                PreviewTrayVideoFormatFab(onClick = onOpenVideoFormat)
            }
        }

        if (showOnScreenShutter) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                if (primaryPhoto) {
                    FloatingActionButton(
                        onClick = {
                            if (canCaptureRawStill) {
                                onCaptureDng()
                            } else {
                                traySnackbarScope.pnsShowSnackbar(
                                    snackbarHostState,
                                    "Still capture: use preview at 119 fps or below and enable RAW and/or JPEG in IMG.",
                                    longDuration = false,
                                )
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
                            Log.i("PNS.ChromeUx", "trayVideoRecordTap wantToggle=${!isRecording} fps=$selectedFps")
                            onRecordingChange(!isRecording)
                        },
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

        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewTrayPhotoVideoModeToggleFab(
                primaryPhoto = primaryPhoto,
                isRecording = isRecording,
                onPrimaryPhotoChange = onPrimaryPhotoChange,
                onRecordingChange = onRecordingChange,
            )
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
 * Quick-setting block used under the 7×3 shortcut grid. When [showIconHeader] is **false** (preview chrome),
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

/** Short-press toggle for binary quick-setting tiles; menu-only tiles use [ChromeGridQuickAction.TimerStub], [ChromeGridQuickAction.ExtraShutterMenu], or [ChromeGridQuickAction.CycleFlash] (handled in [PreviewChromeScrollSlot]). */
private fun performQuickActionToggle(
    kind: ChromeGridQuickAction,
    hudState: HudSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
) {
    when (kind) {
        ChromeGridQuickAction.TimerStub -> Unit
        ChromeGridQuickAction.ToggleHistogram -> {
            val h = hudState.current
            hudState.update(h.copy(showHistogram = !h.showHistogram))
        }
        ChromeGridQuickAction.ToggleHorizonLevel -> {
            val h = hudState.current
            hudState.update(h.copy(showHorizonLevel = !h.showHorizonLevel))
        }
        ChromeGridQuickAction.ToggleEyeAfOverlay -> {
            val h = hudState.current
            hudState.update(h.copy(showEyeAfOverlay = !h.showEyeAfOverlay))
        }
        ChromeGridQuickAction.ToggleVideoTally -> {
            val h = hudState.current
            hudState.update(h.copy(showVideoTally = !h.showVideoTally))
        }
        ChromeGridQuickAction.ToggleMaxBrightnessPreview -> {
            val c = chromePrefs.current
            chromePrefs.update(c.copy(maxBrightnessInPreview = !c.maxBrightnessInPreview))
        }
        ChromeGridQuickAction.ToggleDndInPreview -> {
            val c = chromePrefs.current
            chromePrefs.update(c.copy(dndWhileInPreview = !c.dndWhileInPreview))
        }
        ChromeGridQuickAction.ExtraShutterMenu -> Unit
        ChromeGridQuickAction.CycleFlash -> Unit
        ChromeGridQuickAction.ToggleSaveLocation -> {
            val c = chromePrefs.current
            if (c.saveLocationWithMedia) {
                chromePrefs.update(c.copy(saveLocationWithMedia = false))
                CaptureLocationBridge.update(null)
            } else if (fineLocationGranted) {
                chromePrefs.update(c.copy(saveLocationWithMedia = true))
            } else {
                onPendingEnableGeotagChange(true)
                onRequestLocationForGeotag()
            }
        }
        ChromeGridQuickAction.ToggleLensOis -> {
            val h = hudState.current
            hudState.update(h.copy(enableLensOpticalStabilization = !h.enableLensOpticalStabilization))
            Log.i("PNS.ChromeUx", "ois=${!h.enableLensOpticalStabilization}")
        }
        ChromeGridQuickAction.ToggleVideoEis -> {
            val h = hudState.current
            hudState.update(h.copy(enableVideoStabilizationPreview = !h.enableVideoStabilizationPreview))
            Log.i("PNS.ChromeUx", "eis=${!h.enableVideoStabilizationPreview}")
        }
    }
}

@Composable
private fun ChromeGridQuickActionPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    kind: ChromeGridQuickAction,
    menuTitle: String,
    hudState: HudSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
) {
    val hud = hudState.current
    val chrome = chromePrefs.current
    PnsChromeDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        title = menuTitle,
    ) {
        when (kind) {
            ChromeGridQuickAction.ToggleHistogram -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !hud.showHistogram,
                    onClick = {
                        hudState.update(hudState.current.copy(showHistogram = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = hud.showHistogram,
                    onClick = {
                        hudState.update(hudState.current.copy(showHistogram = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.TimerStub -> {
                PnsChromeMenuItem(
                    label = "Single shot",
                    selected =
                        ShutterCaptureMode.current(chrome, hud) == ShutterCaptureMode.Single,
                    onClick = {
                        applyShutterCaptureMode(ShutterCaptureMode.Single, chromePrefs, hudState)
                        Log.i("PNS.ChromeUx", "shutterMode=Single")
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "Burst",
                    selected =
                        ShutterCaptureMode.current(chrome, hud) == ShutterCaptureMode.Burst,
                    onClick = {
                        applyShutterCaptureMode(ShutterCaptureMode.Burst, chromePrefs, hudState)
                        Log.i("PNS.ChromeUx", "shutterMode=Burst")
                        onDismissRequest()
                    },
                )
                androidx.compose.material3.HorizontalDivider(
                    color = Color.White.copy(alpha = 0.18f),
                )
                for (sec in PreviewChromePreferences.SELF_TIMER_DELAY_SEC_OPTIONS) {
                    if (sec == 0) continue
                    val label = "${sec}s timer"
                    val selected =
                        ShutterCaptureMode.current(chrome, hud) == ShutterCaptureMode.Timer &&
                            chromePrefs.current.selfTimerDelaySec == sec
                    PnsChromeMenuItem(
                        label = label,
                        selected = selected,
                        onClick = {
                            applyShutterCaptureMode(
                                ShutterCaptureMode.Timer,
                                chromePrefs,
                                hudState,
                                timerSec = sec,
                            )
                            Log.i("PNS.ChromeUx", "shutterMode=Timer selfTimerSec=$sec")
                            onDismissRequest()
                        },
                    )
                }
            }
            ChromeGridQuickAction.ToggleHorizonLevel -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !hud.showHorizonLevel,
                    onClick = {
                        hudState.update(hudState.current.copy(showHorizonLevel = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = hud.showHorizonLevel,
                    onClick = {
                        hudState.update(hudState.current.copy(showHorizonLevel = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.ToggleEyeAfOverlay -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !hud.showEyeAfOverlay,
                    onClick = {
                        hudState.update(hudState.current.copy(showEyeAfOverlay = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = hud.showEyeAfOverlay,
                    onClick = {
                        hudState.update(hudState.current.copy(showEyeAfOverlay = true))
                        onDismissRequest()
                    },
                )
                androidx.compose.material3.HorizontalDivider(
                    color = Color.White.copy(alpha = 0.18f),
                )
                PnsChromeMenuItem(
                    label = "Smile to capture — Off",
                    selected = !hud.enableSmileTriggeredStill,
                    onClick = {
                        hudState.update(hudState.current.copy(enableSmileTriggeredStill = false))
                        Log.i("PNS.SmileStill", "smileStillEnabled=false (eyeAfMenu)")
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "Smile to capture — On",
                    selected = hud.enableSmileTriggeredStill,
                    onClick = {
                        hudState.update(hudState.current.copy(enableSmileTriggeredStill = true))
                        Log.i("PNS.SmileStill", "smileStillEnabled=true (eyeAfMenu)")
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.ToggleVideoTally -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !hud.showVideoTally,
                    onClick = {
                        hudState.update(hudState.current.copy(showVideoTally = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = hud.showVideoTally,
                    onClick = {
                        hudState.update(hudState.current.copy(showVideoTally = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.ToggleMaxBrightnessPreview -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !chrome.maxBrightnessInPreview,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(maxBrightnessInPreview = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = chrome.maxBrightnessInPreview,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(maxBrightnessInPreview = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.ToggleDndInPreview -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !chrome.dndWhileInPreview,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(dndWhileInPreview = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = chrome.dndWhileInPreview,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(dndWhileInPreview = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.ExtraShutterMenu -> {
                PnsChromeMenuItem(
                    label = "Tap preview: Off",
                    selected = !chrome.tapPreviewToCapture,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(tapPreviewToCapture = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "Tap preview: On",
                    selected = chrome.tapPreviewToCapture,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(tapPreviewToCapture = true))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "Volume keys: Off",
                    selected = !chrome.volumeKeysCapture,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(volumeKeysCapture = false))
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "Volume keys: On",
                    selected = chrome.volumeKeysCapture,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(volumeKeysCapture = true))
                        onDismissRequest()
                    },
                )
            }
            ChromeGridQuickAction.CycleFlash -> {
                for (m in PreviewFlashMode.entries) {
                    val label =
                        when (m) {
                            PreviewFlashMode.Off -> "Off"
                            PreviewFlashMode.Auto -> "Auto"
                            PreviewFlashMode.On -> "On"
                            PreviewFlashMode.Torch -> "Torch"
                        }
                    PnsChromeMenuItem(
                        label = label,
                        selected = chromePrefs.current.previewFlashMode == m,
                        onClick = {
                            chromePrefs.update(chromePrefs.current.copy(previewFlashMode = m))
                            Log.i("PNS.ChromeUx", "flashMode=${m.name}")
                            onDismissRequest()
                        },
                    )
                }
            }
            ChromeGridQuickAction.ToggleLensOis,
            ChromeGridQuickAction.ToggleVideoEis,
            -> Unit
            ChromeGridQuickAction.ToggleSaveLocation -> {
                PnsChromeMenuItem(
                    label = "Off",
                    selected = !chrome.saveLocationWithMedia,
                    onClick = {
                        val c = chromePrefs.current
                        chromePrefs.update(c.copy(saveLocationWithMedia = false))
                        CaptureLocationBridge.update(null)
                        onDismissRequest()
                    },
                )
                PnsChromeMenuItem(
                    label = "On",
                    selected = chrome.saveLocationWithMedia,
                    onClick = {
                        val c = chromePrefs.current
                        if (fineLocationGranted) {
                            chromePrefs.update(c.copy(saveLocationWithMedia = true))
                        } else {
                            onPendingEnableGeotagChange(true)
                            onRequestLocationForGeotag()
                        }
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewChromeScrollSlot(
    spec: ChromeGridSlotSpec,
    expandedKey: String?,
    onToggleShortcutTitle: (String) -> Unit,
    onOpenDeveloperMenuFromSettingsLongPress: () -> Unit,
    hudState: HudSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    uiRotationDeg: Float,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
) {
    val hud = hudState.current
    val chrome = chromePrefs.current
    val shutterMode = ShutterCaptureMode.current(chrome, hud)
    val rot = Modifier.chromeGlyphRotation(uiRotationDeg)
    val context = LocalContext.current
    val snackbarHost = LocalPnsSnackbarHostState.current
    val snackScope = rememberCoroutineScope()
    when (spec) {
        is ChromeGridSlotSpec.ExpandShortcut ->
            IconCubeVectorButton(
                onClick = { onToggleShortcutTitle(spec.title) },
                contentDescription = spec.contentDescription,
                imageVector = spec.icon,
                selected = expandedKey == spec.title,
                modifier = rot.then(Modifier.fillMaxSize()),
                chromeChipStyle = true,
                fillMaxTile = true,
                onLongClick =
                    if (spec.title == "Settings") {
                        {
                            onOpenDeveloperMenuFromSettingsLongPress()
                        }
                    } else {
                        null
                    },
            )
        is ChromeGridSlotSpec.QuickAction -> {
            var qsMenuExpanded by remember { mutableStateOf(false) }
            val menuOnlyQs = spec.kind == ChromeGridQuickAction.ExtraShutterMenu
            val cycleFlashQs = spec.kind == ChromeGridQuickAction.CycleFlash
            val cycleShutterQs = spec.kind == ChromeGridQuickAction.TimerStub
            val binaryQs = !menuOnlyQs && !cycleFlashQs && !cycleShutterQs
            val selectedQuick =
                when (spec.kind) {
                    ChromeGridQuickAction.ToggleHistogram ->
                        hud.showHistogram
                    ChromeGridQuickAction.TimerStub ->
                        shutterMode != ShutterCaptureMode.Single
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
                    ChromeGridQuickAction.ExtraShutterMenu ->
                        chromePrefs.current.tapPreviewToCapture ||
                            chromePrefs.current.volumeKeysCapture
                    ChromeGridQuickAction.CycleFlash ->
                        chromePrefs.current.previewFlashMode != PreviewFlashMode.Off
                    ChromeGridQuickAction.ToggleSaveLocation ->
                        chromePrefs.current.saveLocationWithMedia && fineLocationGranted
                    ChromeGridQuickAction.ToggleLensOis ->
                        hud.enableLensOpticalStabilization
                    ChromeGridQuickAction.ToggleVideoEis ->
                        hud.enableVideoStabilizationPreview
                }
            val tileIcon =
                when (spec.kind) {
                    ChromeGridQuickAction.TimerStub ->
                        when (shutterMode) {
                            ShutterCaptureMode.Burst -> Icons.Outlined.BurstMode
                            ShutterCaptureMode.Timer -> Icons.Outlined.Timer
                            ShutterCaptureMode.Single -> Icons.Outlined.PhotoCamera
                        }
                    ChromeGridQuickAction.CycleFlash ->
                        if (chromePrefs.current.previewFlashMode == PreviewFlashMode.Off) {
                            Icons.Outlined.FlashOff
                        } else {
                            Icons.Outlined.FlashOn
                        }
                    else -> spec.icon
                }
            val qsA11yLabel =
                when (spec.kind) {
                    ChromeGridQuickAction.TimerStub ->
                        "${spec.contentDescription}, ${shutterCaptureModeLabel(shutterMode, chrome)}"
                    ChromeGridQuickAction.CycleFlash ->
                        "${spec.contentDescription}, current mode ${chromePrefs.current.previewFlashMode}"
                    else ->
                        "${spec.contentDescription}, ${if (selectedQuick) "on" else "off"}"
                }
            Box(modifier = rot.then(Modifier.fillMaxSize())) {
                IconCubeVectorButton(
                    onClick = {
                        when (spec.kind) {
                            ChromeGridQuickAction.TimerStub -> {
                                val next = ShutterCaptureMode.cycle(shutterMode)
                                applyShutterCaptureMode(next, chromePrefs, hudState)
                                Log.i("PNS.ChromeUx", "shutterMode=$next")
                            }
                            ChromeGridQuickAction.CycleFlash -> {
                                val c = chromePrefs.current
                                val next = c.previewFlashMode.cycle()
                                chromePrefs.update(c.copy(previewFlashMode = next))
                                Log.i("PNS.ChromeUx", "flashMode=${next.name}")
                            }
                            else -> {
                                if (binaryQs) {
                                    performQuickActionToggle(
                                        spec.kind,
                                        hudState,
                                        chromePrefs,
                                        fineLocationGranted,
                                        onPendingEnableGeotagChange,
                                        onRequestLocationForGeotag,
                                    )
                                } else {
                                    qsMenuExpanded = true
                                }
                            }
                        }
                    },
                    contentDescription = qsA11yLabel,
                    imageVector = tileIcon,
                    selected = selectedQuick,
                    modifier = Modifier.fillMaxSize(),
                    chromeChipStyle = true,
                    fillMaxTile = true,
                    onLongClick =
                        if (binaryQs || cycleFlashQs || cycleShutterQs) {
                            {
                                if (cycleFlashQs &&
                                    !PnsUiHintsStore.hasSeenFlashLongPressMenuTip(context.applicationContext)
                                ) {
                                    PnsUiHintsStore.markFlashLongPressMenuTipSeen(context.applicationContext)
                                    snackScope.pnsShowSnackbar(
                                        snackbarHost,
                                        "Flash: long-press opens the mode menu (Torch, Auto, …).",
                                        longDuration = false,
                                    )
                                }
                                qsMenuExpanded = true
                            }
                        } else {
                            null
                        },
                )
                ChromeGridQuickActionPopup(
                    expanded = qsMenuExpanded,
                    onDismissRequest = { qsMenuExpanded = false },
                    kind = spec.kind,
                    menuTitle = spec.contentDescription,
                    hudState = hudState,
                    chromePrefs = chromePrefs,
                    fineLocationGranted = fineLocationGranted,
                    onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                    onRequestLocationForGeotag = onRequestLocationForGeotag,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun PreviewChromeGrid7x3(
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
    onOpenDeveloperMenuFromSettingsLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val focalSlots = FocalMmSlot.entries

    val digitalEqOk =
        remember(cameraIds) {
            FocalLensStripSupport.digitalEqSlotsEnabledForWide(appCtx, cameraIds)
        }
    val nativeFocalBySlot =
        remember(cameraIds) {
            FocalMmSlot.entries.associateWith { slot ->
                FocalLensStripSupport.nativeFocalLengthMmForSlot(appCtx, slot, cameraIds)
            }
        }

    LaunchedEffect(Unit) {
        val gridUx =
            "grid7x3=layout shortcutRows=2 settingsAt=r2c6=true " +
                "quickActions=timer,histogram,horizon,eyeAf,tally,bright,dnd,extraShutter,flash,saveLoc " +
                "quickGrid=focalRow7_iconTiles_matchFpsChip_scrolledSlots targetFpsOnReadout=true"
        Log.i("PNS.ChromeUx", gridUx)
    }

    val gap = 6.dp
    val cols = 7
    val shortcutLogicalRows = previewChromeGridSlots.map { it.row }.distinct().size
    val physicalRows = 1 + shortcutLogicalRows
    BoxWithConstraints(modifier = modifier) {
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val cellH = (maxHeight - gap * (physicalRows - 1)) / physicalRows
        val cell = minOf(cellW, cellH).coerceAtLeast(1.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        Box(
                            modifier = Modifier.size(cell),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c < focalSlots.size) {
                                val slot = focalSlots[c]
                                val interactionEnabled =
                                    FocalLensStripSupport.focalSlotInteractionEnabled(
                                        appCtx,
                                        slot,
                                        cameraIds,
                                        selectedCameraId,
                                        digitalEqOk,
                                    )
                                val selected =
                                    focalMmSlotIsActive(
                                        appCtx,
                                        slot,
                                        cameraIds,
                                        selectedCameraId,
                                        focalCrop,
                                    )
                                val nativeMm = nativeFocalBySlot[slot]
                                val sub = nativeMm?.let { FocalLensStripSupport.formatShortNativeFocalMm(it) }
                                val cd =
                                    buildString {
                                        append(slot.labelMm)
                                        append(" millimeter equivalent")
                                        if (sub != null) {
                                            append(", native ")
                                            append(sub)
                                        }
                                        if (!interactionEnabled) {
                                            append(", unavailable")
                                        }
                                    }
                                FpsQuickChip(
                                    label = slot.labelMm,
                                    selected = selected,
                                    requiresRoot = false,
                                    enabled = interactionEnabled,
                                    onClick = { onApplyFocalMmSlot(slot) },
                                    fillMaxTile = true,
                                    subLabel = sub,
                                    contentDescription = cd,
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
                val gridRows = previewChromeGridSlots.map { it.row }.distinct().sorted()
                val specAt =
                    previewChromeGridSlots.associateBy { it.row to it.col }
                for (gridRow in gridRows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(cols) { col ->
                            val spec = specAt[gridRow to col]
                            Box(
                                modifier = Modifier.size(cell),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (spec != null) {
                                    PreviewChromeScrollSlot(
                                        spec = spec,
                                        expandedKey = expandedKey,
                                        onToggleShortcutTitle = onToggleShortcutTitle,
                                        onOpenDeveloperMenuFromSettingsLongPress =
                                            onOpenDeveloperMenuFromSettingsLongPress,
                                        hudState = hudState,
                                        chromePrefs = chromePrefs,
                                        uiRotationDeg = uiRotationDeg,
                                        fineLocationGranted = fineLocationGranted,
                                        onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                                        onRequestLocationForGeotag = onRequestLocationForGeotag,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Guides & framing: home lists crop + grid; [pane] selects which preset list to show (same pattern as Settings ▸ Guides).
 */
@Composable
private fun GuidesFramingMenuContent(
    compositionGuide: CompositionGuideSettingsState,
    pane: String?,
    onPaneChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (pane) {
            null -> {
                ChromeSettingsIntroText(
                    "Crop outlines and grids draw over the preview; the full sensor view can stay visible (letterboxed).",
                )
                RailSettingsMenuEntryCard(
                    title = "Crop guide",
                    subtitle = compositionGuide.current.cropGuide.label,
                    onClick = { onPaneChange("crop") },
                )
                RailSettingsMenuEntryCard(
                    title = "Framing grid",
                    subtitle = compositionGuide.current.gridMode.label,
                    onClick = { onPaneChange("grid") },
                )
            }
            "crop" -> {
                val currentCrop = compositionGuide.current.cropGuide
                for (opt in CropGuideAspect.entries) {
                    FpsQuickChip(
                        label = opt.label,
                        selected = opt == currentCrop,
                        requiresRoot = false,
                        onClick = {
                            compositionGuide.update(compositionGuide.current.copy(cropGuide = opt))
                            onPaneChange(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            "grid" -> {
                val currentGrid = compositionGuide.current.gridMode
                for (opt in GridOverlayMode.entries) {
                    FpsQuickChip(
                        label = opt.label,
                        selected = opt == currentGrid,
                        requiresRoot = false,
                        onClick = {
                            compositionGuide.update(compositionGuide.current.copy(gridMode = opt))
                            onPaneChange(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TargetFpsRailSheetContent(
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    selectedFps: Int,
    onSetFps: (Int) -> Unit,
    focalCrop: FocalMode?,
) {
    val snackbarHostState = LocalPnsSnackbarHostState.current
    val sheetScope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChromeSettingsIntroText(
            "Same targets as the FPS chip on the readout strip. Options follow each cameraId’s AE + " +
                "high-speed tables (per-lens ceiling); switching lenses may auto-step the target down if the " +
                "prior fps is not stock-achievable on the new camera. Root-only entries use a blue tint and may " +
                "still be rejected by the HAL.",
        )
        val maxStock = PreviewFpsSupport.maxStockTargetFromOptions(fpsOptions)
        if (maxStock != null) {
            FpsQuickChip(
                label = "Max HFR (stock): ${maxStock} fps",
                selected = selectedFps == maxStock,
                requiresRoot = false,
                onClick = { onSetFps(maxStock) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for (opt in fpsOptions) {
            FpsQuickChip(
                label = "${opt.targetFps}",
                selected = opt.targetFps == selectedFps,
                requiresRoot = opt.requiresRoot,
                onClick = {
                    if (opt.requiresRoot && opt.targetFps != selectedFps) {
                        sheetScope.pnsShowSnackbar(
                            snackbarHostState,
                            "Root-only on this camera: ${opt.targetFps} fps is not advertised without root or vendor unlock. You can still try; the app falls back if the HAL rejects it.",
                        )
                    }
                    onSetFps(opt.targetFps)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (selectedFps >= 120 && focalCrop != null) {
            Text(
                "120 fps and above: focal-length crop is disabled for this path.",
                style = MaterialTheme.typography.bodySmall,
                color = PnsColors.WarnAmber,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun RailSettingsHomeContent(
    onGuides: () -> Unit,
    onHud: () -> Unit,
    onPreview: () -> Unit,
    onCapture: () -> Unit,
    onVideo: () -> Unit,
    onAbout: () -> Unit,
    onTargetFps: () -> Unit,
    onQuickSettings: () -> Unit,
    fpsTargetEditable: Boolean,
    settingsSearchQuery: String,
    onSettingsSearchQueryChange: (String) -> Unit,
    onSettingsSearchPick: (ChromeSettingSearchHit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChromeSettingsSearchField(
            query = settingsSearchQuery,
            onQueryChange = onSettingsSearchQueryChange,
        )
        if (settingsSearchQuery.isNotBlank()) {
            ChromeSettingsSearchResults(
                query = settingsSearchQuery,
                onPick = onSettingsSearchPick,
            )
        } else {
        ChromeSettingsIntroText(
            "Quick-setting tiles mirror these groups. Long-press the Settings tile for developer / research items.",
        )
        RailSettingsMenuEntryCard(
            title = "Quick settings",
            subtitle = "All 7×3 toggles: shutter mode, histogram, DND, flash, geotag, stabilization.",
            onClick = onQuickSettings,
        )
        PreviewRailSectionTitle("Capture")
        RailSettingsMenuEntryCard(
            title = "Capture & stills",
            subtitle = "RAW / JPEG, brackets, shutter sound, flash, default camera.",
            onClick = onCapture,
        )
        PreviewRailSectionTitle("Video")
        RailSettingsMenuEntryCard(
            title = "Video & stabilization",
            subtitle = "OIS, EIS, shutter-angle presets, encode lane.",
            onClick = onVideo,
        )
        if (fpsTargetEditable) {
            RailSettingsMenuEntryCard(
                title = "Target frame rate",
                subtitle = "Matches the FPS menu on the readout strip.",
                onClick = onTargetFps,
            )
        }
        PreviewRailSectionTitle("Focus & metering")
        RailSettingsMenuEntryCard(
            title = "HUD & readouts",
            subtitle = "Dial, tally, timecode, meters, histogram, focus peaking, LUT chips.",
            onClick = onHud,
        )
        PreviewRailSectionTitle("Display")
        RailSettingsMenuEntryCard(
            title = "Guides & framing",
            subtitle = "Crop guide and framing grid (same as the Guides tile).",
            onClick = onGuides,
        )
        RailSettingsMenuEntryCard(
            title = "Preview & behavior",
            subtitle = "Shutter, gestures, preview fit, brightness, keys, Do Not Disturb.",
            onClick = onPreview,
        )
        PreviewRailSectionTitle("About")
        RailSettingsMenuEntryCard(
            title = "About & heritage",
            subtitle = "Credits, LG dual-camera nod, support development (Venmo).",
            onClick = onAbout,
        )
        }
    }
}

@Composable
private fun VideoSettingsRailSheetContent(
    hudState: HudSettingsState,
    onKickPreviewPipeline: () -> Unit,
) {
    val hud = hudState.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewRailSectionTitle("Stabilization")
        PreviewRailSettingToggle(
            title = "Optical stabilization (OIS)",
            subtitle = "Lens OIS on preview and stills when the HAL exposes a non-OFF mode.",
            checked = hud.enableLensOpticalStabilization,
            onCheckedChange = { on ->
                hudState.update(hud.copy(enableLensOpticalStabilization = on))
                onKickPreviewPipeline()
            },
        )
        PreviewRailSettingToggle(
            title = "Electronic stabilization (EIS)",
            subtitle = "Preview-stream EIS only; skipped for HFR (≥120 fps) and still captures.",
            checked = hud.enableVideoStabilizationPreview,
            onCheckedChange = { on ->
                hudState.update(hud.copy(enableVideoStabilizationPreview = on))
                onKickPreviewPipeline()
            },
        )
        PreviewRailSectionTitle("Shutter angle (video)")
        Text(
            "Locks shutter speed as a fraction of the frame interval; ISO chases via readout (15.11).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        for (angle in VideoShutterAngle.entries) {
            FocusPeakingOptionRow(
                label = angle.label,
                selected = hud.videoShutterAngleEnum() == angle,
                swatchColor = null,
                onClick = {
                    hudState.update(hud.copy(videoShutterAngle = angle.name))
                },
            )
        }
    }
}

@Composable
internal fun FocusPeakingOptionRow(
    label: String,
    selected: Boolean,
    swatchColor: Color?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor =
        if (selected) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.35f)
    val bg =
        if (selected) PnsColors.PhotoOrange else Color.Black.copy(alpha = 0.45f)
    val fg =
        if (selected) Color.Black else Color.White.copy(alpha = 0.92f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatchColor != null) {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(swatchColor),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FocusPeakingSettingsDialog(
    onDismiss: () -> Unit,
    hudState: HudSettingsState,
) {
    val color = hudState.current.focusPeakingColor
    val strength = hudState.current.focusPeakingStrength
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Focus peaking",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Text(
                    "False color on high-contrast edges in the GL preview (luminance gradient). " +
                        "This is not a Camera2 autofocus readout — use still sharpness / PNS.StillBoundary for AF triage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Text(
                    "Color",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
                for (opt in FocusPeakingColor.entries) {
                    FocusPeakingOptionRow(
                        label = opt.displayName,
                        selected = opt == color,
                        swatchColor = if (opt == FocusPeakingColor.Off) null else opt.toOverlayColor(),
                        onClick = {
                            hudState.update(
                                hudState.current.copy(focusPeakingColor = opt),
                            )
                        },
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Text(
                    "Sensitivity",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Text(
                    "Edge threshold in the GLES preview shader (works in photo and video).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
                for (opt in FocusPeakingStrength.entries) {
                    FocusPeakingOptionRow(
                        label = opt.displayName,
                        selected = opt == strength,
                        swatchColor = null,
                        onClick = {
                            hudState.update(
                                hudState.current.copy(focusPeakingStrength = opt),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewKeysRailSheetContent(
    chrome: PreviewChromePreferences,
    chromePrefs: PreviewChromePreferencesState,
    onCalibrateFromPreviewFrame: () -> Unit,
    hudState: HudSettingsState,
    onKickPreviewPipeline: () -> Unit,
    onOpenFocusModePicker: () -> Unit,
) {
    val context = LocalContext.current
    var focusPeakingDialogOpen by remember { mutableStateOf(false) }
    val hud = hudState.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewRailSectionTitle("Shutter & gestures")
        PreviewRailSettingToggle(
            title = "On-screen shutter button",
            subtitle = "Shows a shutter control on the preview overlay.",
            checked = chrome.showOnScreenShutter,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(showOnScreenShutter = checked) } },
        )
        PreviewRailSectionTitle("Gallery")
        var useBespokeGallery by remember { mutableStateOf(GalleryPrefs.useBespokeGallery(context)) }
        PreviewRailSettingToggle(
            title = "Use in-app gallery",
            subtitle = "Open photos in the app gallery instead of system gallery.",
            checked = useBespokeGallery,
            onCheckedChange = { checked ->
                useBespokeGallery = checked
                GalleryPrefs.setUseBespokeGallery(context, checked)
            },
        )
        PreviewRailSectionTitle("Extra shutters")
        PreviewRailSettingToggle(
            title = "Tap preview to capture",
            subtitle = "Single tap on the live finder fires the still shutter.",
            checked = chrome.tapPreviewToCapture,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(tapPreviewToCapture = checked) } },
        )
        PreviewRailSettingToggle(
            title = "Volume keys shutter",
            subtitle = "Hardware volume keys trigger capture when preview has focus.",
            checked = chrome.volumeKeysCapture,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(volumeKeysCapture = checked) } },
        )
        PreviewRailSettingToggle(
            title = "Bluetooth remote shutter",
            subtitle = "Headset / AVRCP play-pause fires the tray shutter when preview is foregrounded.",
            checked = chrome.btRemoteShutter,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(btRemoteShutter = checked) } },
        )
        PreviewRailSectionTitle("Preview framing & overlays")
        PreviewRailSettingToggle(
            title = "Corner test chart overlay",
            subtitle = "Small alignment grid overlay for display checks.",
            checked = chrome.liveChartCornerOverlay,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(liveChartCornerOverlay = checked) } },
        )
        PreviewRailSectionTitle("HDR / wide gamut (preview session)")
        PreviewRailSettingToggle(
            title = "HDR / 10-bit live preview",
            subtitle = "Same as HUD ▸ Extensions. Reopens the camera session after change.",
            checked = hudState.current.enableHdr10LivePreview,
            onCheckedChange = { on ->
                val next = hudState.current.copy(enableHdr10LivePreview = on)
                HudSettings.save(context.applicationContext, next)
                hudState.update(next)
                Log.i("PNS.ChromeUx", "hdr10LivePreviewToggle=$on")
                onKickPreviewPipeline()
            },
        )
        RailSettingsMenuEntryCard(
            title = "Focus mode",
            subtitle = "HAL AF modes, manual distance rack, Auto restores CAF",
            onClick = onOpenFocusModePicker,
        )
        RailSettingsMenuEntryCard(
            title = "Focus peaking",
            subtitle =
                if (hud.focusPeakingColor == FocusPeakingColor.Off) {
                    "Off — tap for color and sensitivity"
                } else {
                    "${hud.focusPeakingColor.displayName} · ${hud.focusPeakingStrength.displayName}"
                },
            onClick = { focusPeakingDialogOpen = true },
        )
        if (focusPeakingDialogOpen) {
            FocusPeakingSettingsDialog(
                onDismiss = { focusPeakingDialogOpen = false },
                hudState = hudState,
            )
        }
        RailSettingsMenuEntryCard(
            title = "Calibrate from preview",
            subtitle = "Uses the current preview frame for color workflows.",
            onClick = onCalibrateFromPreviewFrame,
        )
        PreviewRailSectionTitle("Brightness")
        PreviewRailSettingToggle(
            title = "Max brightness in preview",
            subtitle = "Keeps the screen bright while the preview is open (uses more battery).",
            checked = chrome.maxBrightnessInPreview,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(maxBrightnessInPreview = checked) } },
        )
        PreviewRailSectionTitle("Do Not Disturb")
        PreviewRailSettingToggle(
            title = "Silence notifications (preview open)",
            subtitle = "Requires notification policy access on supported Android versions.",
            checked = chrome.dndWhileInPreview,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(dndWhileInPreview = checked) } },
        )
        PreviewRailSettingToggle(
            title = "Silence notifications (while recording)",
            subtitle = "Same policy gate as above; applies during video recording.",
            checked = chrome.dndWhileRecording,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(dndWhileRecording = checked) } },
        )
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
                        "Open system Do Not Disturb access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PnsColors.RootAccentBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureToolsRailSheetContent(
    chrome: PreviewChromePreferences,
    chromePrefs: PreviewChromePreferencesState,
    hudState: HudSettingsState,
    onCaptureDng: () -> Unit,
    canCaptureRawStill: Boolean,
    previewJpegCompanion: Boolean,
    rawStillNotReadyReason: String?,
    commandDialMode: CommandDialMode,
    onBracketBurst: (BracketPattern) -> Unit,
    canCaptureBracketBurst: Boolean,
    onPickFirstCamera: () -> Unit,
    onSwitchToFrontCamera: () -> Unit,
    onSwitchToRearCamera: () -> Unit,
) {
    val context = LocalContext.current
    val shutterSoundPreview = remember { ShutterSoundManager(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { shutterSoundPreview.release() }
    }
    val shutterPack = ShutterSoundPack.fromStorageKey(chrome.shutterSoundPackKey)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShutterModeRailSection(chromePrefs = chromePrefs, hudState = hudState)
        PreviewRailSectionTitle("Imaging")
        Text(
            text = "Still pipeline tiers (RAW vs HDR / JPEG) live on the IMG chip in the readout strip above the rail.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
        )
        PreviewRailSectionTitle("Still capture")
        Button(
            onClick = onCaptureDng,
            enabled = canCaptureRawStill,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save DNG now")
        }
        PreviewRailSectionTitle("Shutter sound")
        Text(
            text = "Tone played when a still is captured (not during video record).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        for (pack in ShutterSoundPack.entries) {
            FocusPeakingOptionRow(
                label = pack.label,
                selected = pack == shutterPack,
                swatchColor = null,
                onClick = {
                    val next = chromePrefs.current.copy(shutterSoundPackKey = pack.storageKey)
                    chromePrefs.update(next)
                    Log.i("PNS.ChromeUx", "shutterSoundPack=${pack.storageKey}")
                    shutterSoundPreview.playShutter(next, haptics = null)
                },
            )
        }
        if (shutterPack != ShutterSoundPack.Silent) {
            Text(
                text = "Volume ${(chrome.shutterSoundVolume * 100f).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            Slider(
                value = chromePrefs.current.shutterSoundVolume.coerceIn(0f, 1f),
                onValueChange = { v ->
                    chromePrefs.update(
                        chromePrefs.current.copy(shutterSoundVolume = v.coerceIn(0f, 1f)),
                    )
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PreviewRailSettingToggle(
            title = "Haptic with shutter",
            subtitle = "Vibration tick when the shutter fires (instead of after readout only).",
            checked = chrome.shutterHapticSync,
            onCheckedChange = { checked -> chromePrefs.updateMutate { it.copy(shutterHapticSync = checked) } },
        )
        PreviewRailSectionTitle("Flash (rear)")
        Text(
            text = "Mode: ${chrome.previewFlashMode.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (m in PreviewFlashMode.entries) {
                val sel = chrome.previewFlashMode == m
                TextButton(
                    onClick = {
                        chromePrefs.updateMutate { it.copy(previewFlashMode = m) }
                        Log.i("PNS.ChromeUx", "flashMode=${m.name}")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            when (m) {
                                PreviewFlashMode.Off -> "Off"
                                PreviewFlashMode.Auto -> "Auto"
                                PreviewFlashMode.On -> "On"
                                PreviewFlashMode.Torch -> "Torch"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (sel) {
                                PnsColors.PhotoOrange
                            } else {
                                Color.White.copy(alpha = 0.72f)
                            },
                    )
                }
            }
        }
        Text(
            text =
                if (previewJpegCompanion) {
                    "This session will emit RAW plus a JPEG sidecar when the pipeline allows it."
                } else {
                    "This session is RAW-only until you enable the companion switch above."
                },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        rawStillNotReadyReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = PnsColors.WarnAmber,
            )
        }
        if (!canCaptureRawStill) {
            Text(
                "DNG capture needs 119 fps or lower on this device path.",
                style = MaterialTheme.typography.bodySmall,
                color = PnsColors.WarnAmber,
            )
        }
        if (commandDialMode == CommandDialMode.BKT) {
            PreviewRailSectionTitle("Bracket burst")
            Text(
                "Fires a bracket while the dial is in BKT mode. Outputs follow IMG tiers (RAW DNG, JPEG, or both).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((pat, label) in
                    listOf(
                        BracketPattern.Three to "3 frames",
                        BracketPattern.Five to "5 frames",
                        BracketPattern.Seven to "7 frames",
                    )) {
                    Button(
                        onClick = { onBracketBurst(pat) },
                        enabled = canCaptureBracketBurst,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        PreviewRailSectionTitle("Camera")
        RailSettingsMenuEntryCard(
            title = "Front camera",
            subtitle = "Same as swiping up on the live preview.",
            onClick = {
                onSwitchToFrontCamera()
                Log.i("PNS.ChromeUx", "cameraFacingRail=tapFront")
            },
        )
        RailSettingsMenuEntryCard(
            title = "Rear cameras",
            subtitle = "Same as swiping down on the live preview.",
            onClick = {
                onSwitchToRearCamera()
                Log.i("PNS.ChromeUx", "cameraFacingRail=tapRear")
            },
        )
        RailSettingsMenuEntryCard(
            title = "Pick default wide camera",
            subtitle = "Opens the system logical camera picker when available.",
            onClick = onPickFirstCamera,
        )
    }
}

@Composable
private fun PreviewRightRail(
    modifier: Modifier = Modifier,
    uiRotationDeg: Float,
    cameraIds: List<String>,
    onApplyFocalMmSlot: (FocalMmSlot) -> Unit,
    onOpenDeveloperMenu: () -> Unit,
    seedOpenAboutSheet: Boolean = false,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    selectedFps: Int,
    onSetFps: (Int) -> Unit,
    /** When false, hide FPS target controls in Settings (photo-primary tray). */
    fpsTargetEditable: Boolean,
    hudState: HudSettingsState,
    compositionGuide: CompositionGuideSettingsState,
    chromePrefs: PreviewChromePreferencesState,
    onPickFirstCamera: () -> Unit,
    onSwitchToFrontCamera: () -> Unit,
    onSwitchToRearCamera: () -> Unit,
    selectedCameraId: String?,
    focalCrop: FocalMode?,
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
    onKickPreviewPipeline: () -> Unit,
    onOpenFocusModePicker: () -> Unit,
    onPictureProfileImaging: (ImagingProfile) -> Unit,
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
    onApplyWorkflowPreset: ((WorkflowPreset) -> Unit)? = null,
) {
    val context = LocalContext.current
    val chrome = chromePrefs.current
    var aboutLiveSummary by remember { mutableStateOf<EncoderSummary?>(null) }
    var aboutHalHfrMaxByCameraId by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    /** Nested pane for the Guides tile dialog ("crop" / "grid"). */
    var guidesPane by rememberSaveable { mutableStateOf<String?>(null) }
    /** Settings ▸ Guides & framing nested pane. */
    var settingsGuidesPane by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = expandedKey != null) {
        expandedKey = null
        settingsSubPage = null
        guidesPane = null
        settingsGuidesPane = null
        settingsSearchQuery = ""
    }
    LaunchedEffect(seedOpenAboutSheet) {
        if (seedOpenAboutSheet) {
            Log.i("PNS.ChromeUx", "settingsAbout=open adbSeed=true")
            expandedKey = "Settings"
            settingsSubPage = "about"
        }
    }
    LaunchedEffect(fpsTargetEditable) {
        if (!fpsTargetEditable && settingsSubPage == "fps") {
            settingsSubPage = null
        }
    }
    LaunchedEffect(Unit) {
        Log.i(
            "PNS.ChromeUx",
            "expandShortcuts=surface=modalDialog host=PreviewRightRail",
        )
    }
    LaunchedEffect(expandedKey) {
        if (expandedKey != "Settings") {
            settingsSubPage = null
        }
        if (expandedKey != "Guides") {
            guidesPane = null
        }
    }
    LaunchedEffect(settingsSubPage) {
        if (settingsSubPage != "guides") {
            settingsGuidesPane = null
        }
        if (settingsSubPage == "about") {
            aboutLiveSummary =
                EncoderAttemptJsonAdapter.loadLatest(context)?.let { result ->
                    EncoderResultAggregator.summarize(result.attempts)
                }
            aboutHalHfrMaxByCameraId =
                DeviceCameraCapabilityCache.halHighSpeedMaxFpsByCameraId(context.applicationContext)
        }
    }
    LaunchedEffect(expandedKey, settingsSubPage) {
        if (expandedKey == "Settings" &&
            settingsSubPage != null &&
            settingsSubPage !in setOf("preview", "capture", "video", "guides", "fps", "hud", "about", "quick")
        ) {
            settingsSubPage = null
        }
    }
    val dialogScroll = rememberScrollState()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(horizontal = 4.dp)
                .padding(top = 4.dp, bottom = 8.dp),
    ) {
        PreviewChromeGrid7x3(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
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
            onOpenDeveloperMenuFromSettingsLongPress = {
                expandedKey = null
                settingsSubPage = null
                guidesPane = null
                settingsGuidesPane = null
                onOpenDeveloperMenu()
            },
        )
        expandedKey?.let { key ->
            val showNestedBack =
                (key == "Guides" && guidesPane != null) ||
                    (key == "Settings" && settingsSubPage != null)
            val sheetTitle =
                when {
                    key == "Settings" && settingsSubPage == "preview" -> "Preview & behavior"
                    key == "Settings" && settingsSubPage == "capture" -> "Capture & stills"
                    key == "Settings" && settingsSubPage == "video" -> "Video & stabilization"
                    key == "Settings" && settingsSubPage == "guides" ->
                        when (settingsGuidesPane) {
                            "crop" -> "Crop guide"
                            "grid" -> "Framing grid"
                            else -> "Guides & framing"
                        }
                    key == "Settings" && settingsSubPage == "fps" -> "Target frame rate"
                    key == "Settings" && settingsSubPage == "hud" -> "HUD & readouts"
                    key == "Settings" && settingsSubPage == "quick" -> "Quick settings"
                    key == "Settings" && settingsSubPage == "about" -> "About & heritage"
                    key == "Guides" ->
                        when (guidesPane) {
                            "crop" -> "Crop guide"
                            "grid" -> "Framing grid"
                            else -> "Guides"
                        }
                    else -> key
                }
            Dialog(
                onDismissRequest = {
                    expandedKey = null
                    settingsSubPage = null
                    guidesPane = null
                    settingsGuidesPane = null
                    settingsSearchQuery = ""
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PreviewChromeMenuColors.dialogSurface,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        Modifier
                            .padding(12.dp)
                            .widthIn(max = 420.dp)
                            .verticalScroll(dialogScroll),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                sheetTitle,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 72.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            if (showNestedBack) {
                                TextButton(
                                    onClick = {
                                        when {
                                            key == "Guides" && guidesPane != null ->
                                                guidesPane = null
                                            key == "Settings" &&
                                                settingsSubPage == "guides" &&
                                                settingsGuidesPane != null ->
                                                settingsGuidesPane = null
                                            key == "Settings" && settingsSubPage != null ->
                                                settingsSubPage = null
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterStart),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    expandedKey = null
                                    settingsSubPage = null
                                    guidesPane = null
                                    settingsGuidesPane = null
                                    settingsSearchQuery = ""
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Text("Close", color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                        HorizontalDivider(color = PreviewChromeMenuColors.divider)
                        when (key) {
                            "Settings" -> {
                                when (settingsSubPage) {
                                    null ->
                                        RailSettingsHomeContent(
                                            onGuides = { settingsSubPage = "guides" },
                                            onHud = { settingsSubPage = "hud" },
                                            onPreview = { settingsSubPage = "preview" },
                                            onCapture = { settingsSubPage = "capture" },
                                            onVideo = { settingsSubPage = "video" },
                                            onAbout = {
                                                Log.i("PNS.ChromeUx", "settingsAbout=open")
                                                settingsSubPage = "about"
                                            },
                                            onTargetFps = { settingsSubPage = "fps" },
                                            onQuickSettings = { settingsSubPage = "quick" },
                                            fpsTargetEditable = fpsTargetEditable,
                                            settingsSearchQuery = settingsSearchQuery,
                                            onSettingsSearchQueryChange = { settingsSearchQuery = it },
                                            onSettingsSearchPick = { hit ->
                                                settingsSubPage = hit.subPage
                                                settingsSearchQuery = ""
                                                Log.i(
                                                    "PNS.ChromeUx",
                                                    "settingsSearchPick title=${hit.title} subPage=${hit.subPage}",
                                                )
                                            },
                                        )
                                    "guides" ->
                                        GuidesFramingMenuContent(
                                            compositionGuide = compositionGuide,
                                            pane = settingsGuidesPane,
                                            onPaneChange = { settingsGuidesPane = it },
                                        )
                                    "fps" ->
                                        TargetFpsRailSheetContent(
                                            fpsOptions = fpsOptions,
                                            selectedFps = selectedFps,
                                            onSetFps = onSetFps,
                                            focalCrop = focalCrop,
                                        )
                                    "hud" ->
                                        HudRailSheetContent(
                                            hudState = hudState,
                                            themeMode = themeMode,
                                            onThemeModeChange = onThemeModeChange,
                                            onPictureProfileImaging = onPictureProfileImaging,
                                            onApplyWorkflowPreset = onApplyWorkflowPreset,
                                        )
                                    "preview" ->
                                        PreviewKeysRailSheetContent(
                                            chrome = chrome,
                                            chromePrefs = chromePrefs,
                                            onCalibrateFromPreviewFrame = onCalibrateFromPreviewFrame,
                                            hudState = hudState,
                                            onKickPreviewPipeline = onKickPreviewPipeline,
                                            onOpenFocusModePicker = onOpenFocusModePicker,
                                        )
                                    "video" ->
                                        VideoSettingsRailSheetContent(
                                            hudState = hudState,
                                            onKickPreviewPipeline = onKickPreviewPipeline,
                                        )
                                    "capture" ->
                                        CaptureToolsRailSheetContent(
                                            chrome = chrome,
                                            chromePrefs = chromePrefs,
                                            hudState = hudState,
                                            onCaptureDng = onCaptureDng,
                                            canCaptureRawStill = canCaptureRawStill,
                                            previewJpegCompanion = previewJpegCompanion,
                                            rawStillNotReadyReason = rawStillNotReadyReason,
                                            commandDialMode = commandDialMode,
                                            onBracketBurst = onBracketBurst,
                                            canCaptureBracketBurst = canCaptureBracketBurst,
                                            onPickFirstCamera = onPickFirstCamera,
                                            onSwitchToFrontCamera = onSwitchToFrontCamera,
                                            onSwitchToRearCamera = onSwitchToRearCamera,
                                        )
                                    "about" ->
                                        AboutRailSheetContent(
                                            liveSummary = aboutLiveSummary,
                                            liveHalHfrMaxByCameraId = aboutHalHfrMaxByCameraId,
                                        )
                                    "quick" ->
                                        QuickSettingsRailSheetContent(
                                            chromePrefs = chromePrefs,
                                            hudState = hudState,
                                            fineLocationGranted = fineLocationGranted,
                                            onPendingEnableGeotagChange = onPendingEnableGeotagChange,
                                            onRequestLocationForGeotag = onRequestLocationForGeotag,
                                            onKickPreviewPipeline = onKickPreviewPipeline,
                                        )
                                    else -> Unit
                                }
                            }
                            "Guides" ->
                                GuidesFramingMenuContent(
                                    compositionGuide = compositionGuide,
                                    pane = guidesPane,
                                    onPaneChange = { guidesPane = it },
                                )
                            "Preview & keys" -> {
                                PreviewKeysRailSheetContent(
                                    chrome = chrome,
                                    chromePrefs = chromePrefs,
                                    onCalibrateFromPreviewFrame = onCalibrateFromPreviewFrame,
                                    hudState = hudState,
                                    onKickPreviewPipeline = onKickPreviewPipeline,
                                    onOpenFocusModePicker = onOpenFocusModePicker,
                                )
                            }
                            "Capture & tools" -> {
                                CaptureToolsRailSheetContent(
                                    chrome = chrome,
                                    chromePrefs = chromePrefs,
                                    hudState = hudState,
                                    onCaptureDng = onCaptureDng,
                                    canCaptureRawStill = canCaptureRawStill,
                                    previewJpegCompanion = previewJpegCompanion,
                                    rawStillNotReadyReason = rawStillNotReadyReason,
                                    commandDialMode = commandDialMode,
                                    onBracketBurst = onBracketBurst,
                                    canCaptureBracketBurst = canCaptureBracketBurst,
                                    onPickFirstCamera = onPickFirstCamera,
                                    onSwitchToFrontCamera = onSwitchToFrontCamera,
                                    onSwitchToRearCamera = onSwitchToRearCamera,
                                )
                            }
                            else -> Unit
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
    faceTrackBoxes: List<FaceTrackBoxView>,
    uiRotationDeg: Float,
    tapToShootEnabled: Boolean,
    tapShootCallbacks: TapToShootCallbacks,
    manualFocusDragEnabled: Boolean = false,
    onManualFocusDragPixels: (Float) -> Unit = {},
    manualFocusRackEnabled: Boolean = false,
    manualFocusRackDiopters: Float = 0f,
    manualFocusRackMaxDiopters: Float = 8f,
    onManualFocusRackDiopters: (Float) -> Unit = {},
    onRequestVolumeKeyFocus: () -> Unit,
    showHorizonLevel: Boolean = true,
    showVideoTallyPip: Boolean = true,
    previewHistogramBins: IntArray? = null,
    highlightClipZebraFrame: HighlightClipZebraFrame? = null,
    previewBufferWidthPx: Int = 0,
    previewBufferHeightPx: Int = 0,
    previewMirrorHorizontally: Boolean = false,
    previewCoverCrop: Boolean = PREVIEW_FINDER_CONTAIN,
    liveChartCornerOverlay: Boolean = false,
    macroLocksCameraSwipe: Boolean = false,
    onSwitchToFrontCamera: () -> Unit = {},
    onSwitchToRearCamera: () -> Unit = {},
    eyeOverlayCalibratorActive: Boolean = false,
    eyeOverlayMarkerSizeScale: Float = 1f,
) {
    val settings = hudState.current
    val guides = compositionGuide.current
    val showFaceHud = settings.showEyeAfOverlay || eyeOverlayCalibratorActive
    val focusTap = remember { MutableInteractionSource() }
    val swipeThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }
    val cameraSwipeActive = !isRecording && !liveChartCornerOverlay && !macroLocksCameraSwipe
    val pointerModifier =
        Modifier.previewManualFocusDrag(
            enabled = manualFocusDragEnabled,
            onDragPixels = onManualFocusDragPixels,
        ).then(
            if (cameraSwipeActive) {
                Modifier.previewFinderPointer(
                    swipeEnabled = !manualFocusDragEnabled,
                    swipeThresholdPx = swipeThresholdPx,
                    tapToShootEnabled = tapToShootEnabled,
                    tapCallbacks = tapShootCallbacks,
                    onSwipeUpToFront = onSwitchToFrontCamera,
                    onSwipeDownToRear = onSwitchToRearCamera,
                    onTapFallbackFocus = onRequestVolumeKeyFocus,
                )
            } else if (tapToShootEnabled) {
                Modifier.tapToShoot(tapShootCallbacks)
            } else {
                Modifier.clickable(
                    interactionSource = focusTap,
                    indication = null,
                ) {
                    onRequestVolumeKeyFocus()
                }
            },
        )
    val finderSemantics =
        Modifier.semantics(mergeDescendants = false) {
            contentDescription =
                "Preview finder. Swipe up on the live preview for the front camera, swipe down for " +
                    "rear cameras. Use accessibility actions for the same without swipes. " +
                    "Tray and side controls are outside this area."
            customActions =
                listOf(
                    CustomAccessibilityAction("Front camera") {
                        onSwitchToFrontCamera()
                        true
                    },
                    CustomAccessibilityAction("Rear cameras") {
                        onSwitchToRearCamera()
                        true
                    },
                )
        }
    Box(
        modifier =
            modifier
                .then(finderSemantics)
                .then(pointerModifier),
    ) {
        // Composition guide stays aligned to the camera buffer (preview never rotates), so
        // it does NOT inherit chrome rotation — the rule-of-thirds lines belong on the
        // sensor frame, not on the user's view.
        CompositionGuideOverlay(
            crop = guides.cropGuide,
            grid = guides.gridMode,
            modifier = Modifier.fillMaxSize(),
        )
        if (settings.showHighlightClipZebra &&
            highlightClipZebraFrame != null &&
            previewBufferWidthPx > 0 &&
            previewBufferHeightPx > 0
        ) {
            HighlightClipZebraOverlay(
                frame = highlightClipZebraFrame,
                bufferWidthPx = previewBufferWidthPx,
                bufferHeightPx = previewBufferHeightPx,
                coverCrop = previewCoverCrop,
                mirrorHorizontally = previewMirrorHorizontally,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showHorizonLevel && settings.showHorizonLevel) {
            HorizonLevelOverlay(modifier = Modifier.fillMaxSize())
        }
        // Face rectangles first, then pupil marks so eye crosses sit visually on top of the box stroke.
        if (showFaceHud) {
            FaceTrackOverlay(faceBoxes = faceTrackBoxes, modifier = Modifier.fillMaxSize())
            EyeAfOverlay(
                eyes = eyeMarks,
                modifier = Modifier.fillMaxSize(),
                markerSizeScale = eyeOverlayMarkerSizeScale,
            )
        }
        if (settings.showFaceAlignmentDebugCrosshair) {
            FaceAlignmentDebugCrosshairOverlay(modifier = Modifier.fillMaxSize())
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
        if (settings.showHistogram) {
            val bins = previewHistogramBins ?: PreviewHistogramPendingBins
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(132.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                ) {
                    PreviewHistogramOverlay(
                        bins = bins,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (manualFocusRackEnabled && manualFocusRackMaxDiopters > 0f) {
            ManualFocusRackBar(
                diopters = manualFocusRackDiopters,
                maxDiopters = manualFocusRackMaxDiopters,
                onDioptersChange = onManualFocusRackDiopters,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
            )
        }
    }
}

private sealed class InAppVideoRecordingUiEvent {
    data object StartFailed : InAppVideoRecordingUiEvent()

    data class Stopped(
        val uri: Uri?,
        val audioEnabled: Boolean,
    ) : InAppVideoRecordingUiEvent()
}

/** Logcat tag for [PreviewController.captureRawStill] failures (always logged; not gated by [PnsAdbLog]). */
private object CaptureStillLog {
    const val TAG = "PNS.CaptureStill"
}

private class PreviewController(
    private val appContext: Context,
) {
    private val shutterSoundManager = ShutterSoundManager(appContext)

    fun onStillShutterFired(haptics: CaptureHaptics) {
        val chrome = PreviewChromePreferences.load(appContext)
        shutterSoundManager.playShutter(chrome, haptics.takeIf { chrome.shutterHapticSync })
    }

    fun onStillCaptureReadoutComplete(
        haptics: CaptureHaptics,
        deferUntilTonalCompanion: Boolean = false,
    ) {
        val chrome = PreviewChromePreferences.load(appContext)
        if (
            !chrome.shutterHapticSync &&
            PreviewCaptureHapticsPolicy.shouldFireStillTick(
                rawComplete = true,
                deferUntilTonal = deferUntilTonalCompanion,
            )
        ) {
            haptics.scheduleStillTick()
        }
    }

    companion object {
        /** [Handler.postDelayed] before [maybeRestartBody] after macro OutputConfiguration abandon recovery. */
        private const val MACRO_OUTPUT_CONFIG_RETRY_DELAY_MS = 48L

        /**
         * Some HALs need a beat after [CameraCaptureSession.stopRepeating] before [CameraCaptureSession.capture]
         * for a RAW surface; otherwise [onCaptureCompleted] fires with no RAW [ImageReader] frame (OEM CPH2655).
         */
        private const val RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS = 160L

        /** ProShot APK still path: short settle after AE precapture before [TEMPLATE_STILL_CAPTURE]. */
        private const val PRO_SHOT_STILL_AFTER_PRECAPTURE_DELAY_MS = 500L

        /** Minimum post-[stopRepeating] delay for scripted ADB RAW still (fleet §4e); see `REVERTED_FEATURES_RESTORE_LIST.md`. */
        private const val RAW_STILL_SCRIPTED_MIN_POST_STOP_DEBOUNCE_MS = 420L

        /**
         * After [CameraCaptureSession.CaptureCallback.onCaptureCompleted], the HAL may still be
         * filling the RAW [ImageReader] (especially RAW12). If we fail too early, automation sees
         * `No RAW buffer` while the frame is still in flight (Milestone 6 `m6_raw12_ultra_50708`).
         *
         * On at least one OEM turbo-RAW path, vendor log showed the RAW snapshot callback ~2s
         * after a 3200ms gate fired, so RAW12 uses a longer tail than [RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT].
         */
        private const val RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT = 6500L

        private const val RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12 = 6000L

        /**
         * If neither [CameraCaptureSession.CaptureCallback.onCaptureCompleted] nor the post-complete
         * buffer gate fires (HAL stuck / session torn down mid-flight), fail the still so ADB
         * sequential RAW does not hang forever (Milestone 6 ultra_max).
         */
        private const val RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_DEFAULT = 28_000L

        /** RAW12: some OEM stacks sit >28s before completion under preview+YUV+H+M6 cold start. */
        private const val RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_RAW12 = 55_000L

        /** Bracket uses a shorter non-RAW12 tail than single stills (historical 750ms gate). */
        private const val BRACKET_POST_COMPLETE_WAIT_MS_DEFAULT = 750L

        /**
         * Collapse burst [maybeRestart] calls (TextureView size + seed + dial) so [closeCamera] /
         * [openAndStart] do not stack while a Surface is mid-handoff (abandoned BufferQueue).
         */
        private const val MAYBE_RESTART_DEBOUNCE_MS = 48L

        /**
         * Initial [desiredFps] before [LaunchedEffect] runs [setDesired] with [PreviewEngineScreen]'s
         * [selectedFps]. Must stay **< 120** so [canCaptureRawStill] is not blocked and the first
         * session can attach RAW ([createSession] non-HFR path). Matches photo-primary default fps.
         */
        private const val DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC = 60

        /**
         * When async session configure or camera open is pending, [maybeRestartBody] defers
         * [closeCamera] and reschedules itself — cap iterations so we never spin forever.
         */
        private const val MAYBE_RESTART_SESSION_PENDING_DEFERRAL_CAP = 36

        /** Delay before retrying [maybeRestartBody] while session configure / camera open is pending. */
        private const val MAYBE_RESTART_SESSION_PENDING_RESCHEDULE_MS = 160L

        /** When [SurfaceTexture.setDefaultBufferSize] races teardown, retry after layout catches up. */
        private const val SURFACE_TEXTURE_BUFFER_RETRY_DELAY_MS = 120L

        /** EMA alpha when [highlightDarkenEngageEma] falls (slower release — less brighten pump). */
        private const val HIGHLIGHT_ENGAGE_EMA_FALL_ALPHA = 0.22

        /** EMA alpha when [highlightDarkenEngageEma] rises (very slow — protect highlights, less breathing). */
        private const val HIGHLIGHT_ENGAGE_EMA_RISE_ALPHA = 0.07

        // Video constants moved to VideoRecordingController (Sprint 12.4)

        /** HUD **Wait for AF before still**: poll repeating AF state on [PreviewController.handler]. */
        private const val AF_SHUTTER_GATE_POLL_MS = 28L

        private const val AF_SHUTTER_GATE_TIMEOUT_MS = 2800L
    }

    private val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val tag = "PNS.Cam"

    private fun stillBoundaryDiagEnabled(): Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * After [PreviewFlashPolicy.applyStillFlashKeys]: OpenCamera-style **AF idle + lock** when safe,
     * plus face-detect parity on the still request ([StillCaptureFaceDetectParity]).
     */
    private fun applyStillAfFreezeAndFaceParity(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        flashMode: PreviewFlashMode,
        manualSensorStill: Boolean,
    ) {
        val skipAfFreeze =
            PreviewFlashPolicy.stillFlashSkipsAfFreeze(flashMode, manualSensorStill, commandDialMode, chars)
        StillCaptureAfFreeze.applyToStillRequestIfAllowed(req, chars, lastPreviewControlAfState, skipAfFreeze)
        StillCaptureFaceDetectParity.applyWhenFaceHudEnabled(
            req,
            chars,
            faceHudEnabled = hudFaceOverlayEnabled,
            automationSuppressFacePipeline = automationSuppressFacePipeline,
        )
    }

    /** Dedupes one-shot `PNS.TeleRoute` lines per preview generation + physical id. */
    private var lastTeleRouteAdbKey: String? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val maybeRestartDebouncedRunnable = Runnable { maybeRestartBody() }

    private var previewSurfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    /** [setDesired] runs on Main; capture/recorder paths run on [handler] — volatile for publish/consume visibility. */
    @Volatile private var selectedCameraId: String? = null
    /** Default below 120 so the first session can attach RAW before Compose syncs [selectedFps]. */
    @Volatile private var desiredFps: Int = DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC

    /**
     * Latest preview repeating [TotalCaptureResult] metadata (debuggable builds only), sampled for
     * [StillCaptureBoundaryDiag] immediately before still capture [stopRepeating].
     */
    @Volatile private var lastPreviewBoundarySnapshot: StillCaptureBoundaryDiag.Snapshot? = null

    /** Last repeating [CaptureResult.CONTROL_AF_STATE] for [StillCaptureAfFreeze] (always updated). */
    @Volatile private var lastPreviewControlAfState: Int? = null

    /** Latest preview [TotalCaptureResult] for OP13 still AWB gain correction. */
    @Volatile private var lastPreviewTotalCaptureResult: TotalCaptureResult? = null

    @Volatile private var afShutterGateActive: Boolean = false
    private val afShutterGateUiListener = AtomicReference<((Boolean) -> Unit)?>(null)

    /**
     * When [selectedCameraId] is a **logical** multi-camera, pin preview [OutputConfiguration] to
     * this physical child (API 28 [OutputConfiguration.setPhysicalCameraId]).
     */
    @Volatile private var previewSurfacePhysicalCameraId: String? = null

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    /** Video recording controller (Sprint 12.4 refactoring). */
    private val videoController: VideoRecordingController by lazy {
        VideoRecordingController(appContext, handler, mainHandler)
    }

    /** Sprint **14.12** — front camera + GL composite into encoder. */
    val dualVideoEncoderSink = DualVideoGlEncoderSink()
    private var dualFrontController: DualVideoFrontCameraController? = null
    @Volatile private var dualVideoActive: Boolean = false

    private var dualFrontSurfaceTexture: SurfaceTexture? = null
    private var dualFrontSurfaceW: Int = 0
    private var dualFrontSurfaceH: Int = 0
    @Volatile private var dualFrontOpenPending: Boolean = false
    /** Set by [ensureDualFrontOpenForRecord] before [inAppVideoRecordingArmed] on non-concurrent devices. */
    @Volatile private var dualFrontOpenForced: Boolean = false
    @Volatile private var dualHalConcurrencyProbe: DualVideoHalConcurrency.Probe? = null
    @Volatile private var dualFrontDelayedPreviewTry: Boolean = false
    private var dualFrontDelayedOpenRunnable: Runnable? = null
    private var dualFrontThread: HandlerThread? = null
    private var dualFrontHandler: Handler? = null
    @Volatile private var rearTextureFramesAtDualFrontOpen: Long = 0L

    /** Set when stacked GL composite is feeding the encoder (ADB / dual gate). */
    @Volatile
    var dualGlRecordArmed: Boolean = false
        private set

    /** True only between successful record prepare ([applyInAppVideoRecordingShell] wantRecord) and stop. */
    @Volatile private var inAppVideoRecordingArmed: Boolean = false
    @Volatile private var hfrInterleavedRecordActive: Boolean = false
    @Volatile private var hfrInterleavedEncodeRetryDone: Boolean = false
    @Volatile private var deferMcStartUntilPreviewFrame: Boolean = false
    private var hfrInterleavedWatchdogToken: Int = 0
    /** Wall clock when encode watchdogs were armed (muxer can lag HS burst start). */
    private var hfrEncodeWatchdogArmedAtMs: Long = 0L
    private val hfrInterleavedEncodeWatchdog =
        Runnable {
            if (!useHfrInterleavedMcPreview()) return@Runnable
            if (!hfrInterleavedRecordActive && !hfrEncoderOnlyRecordActive) return@Runnable
            if (videoController.peekMcVideoSamplesWritten() > 0L) return@Runnable
            if (!videoController.isMuxerReadyForRecord()) {
                val elapsedMs = SystemClock.uptimeMillis() - hfrEncodeWatchdogArmedAtMs
                if (elapsedMs < 20_000L) {
                    scheduleHfrInterleavedWatchdogs()
                    return@Runnable
                }
                Log.w(
                    HfrInterleavedPreviewSupport.TAG,
                    "muxer not ready after ${elapsedMs}ms with zero video samples — rebuild session",
                )
            } else {
                Log.e(
                    HfrInterleavedPreviewSupport.TAG,
                    "encoder stalled on encoder-only HS — rebuild session",
                )
            }
            requestSessionRebuildHfr()
            scheduleHfrInterleavedWatchdogs()
        }
    private fun lastPreviewActivityNs(): Long =
        if (hfrInterleavedRecordActive) {
            maxOf(lastFrameNs, lastTimestampNs)
        } else {
            lastFrameNs
        }

    private val hfrInterleavedPreviewWatchdog =
        Runnable {
            if (!hfrInterleavedRecordActive || !useHfrInterleavedMcPreview()) return@Runnable
            if (videoController.peekMcVideoSamplesWritten() <= 0L) return@Runnable
            val last = lastPreviewActivityNs()
            if (last <= 0L) return@Runnable
            val stallNs = SystemClock.elapsedRealtimeNanos() - last
            if (stallNs < 1_500_000_000L) return@Runnable
            Log.w(
                HfrInterleavedPreviewSupport.TAG,
                "preview stalled ${stallNs / 1_000_000}ms with encode active; rebuild interleaved session",
            )
            requestSessionRebuildHfr()
        }

    private val rawVideoController = RawVideoRecordingController(appContext)

    /**
     * True when video recording is prepared but session hasn't been rebuilt with recording surface yet.
     * Prevents adding recording surface to capture requests before session is ready.
     */
    @Volatile private var videoRecordingSessionRebuildPending: Boolean = false

    /**
     * HFR MediaCodec stop: [maybeRestartBody] must run **after** muxer finalizes. Rebuilding the
     * HS session while the encoder [Surface] is still in use freezes the app and drops the MP4.
     */
    @Volatile private var deferPreviewRebuildUntilMcStopped: Boolean = false
    private var deferredMcStopRebuildRunnable: Runnable? = null

    private var lastStatus: String = "Idle"

    private val lastStillPostReadout = AtomicReference<StillPostReadoutSnapshot?>(null)
    private val lastStillPostReadoutListener = AtomicReference<((StillPostReadoutSnapshot?) -> Unit)?>(null)

    /**
     * Short DR label for the **current** REGULAR preview session's [OutputConfiguration] profile
     * (output index 0), updated when a session create attempt is submitted without throwing.
     * Used for Milestone **10.6** post-still readout (HAL still metadata does not expose this uniformly).
     */
    @Volatile private var sessionPreviewDynamicRangeShort: String? = null

    fun setLastStillPostReadoutListener(listener: ((StillPostReadoutSnapshot?) -> Unit)?) {
        lastStillPostReadoutListener.set(listener)
        listener?.invoke(lastStillPostReadout.get())
    }

    private fun publishLastStillPostReadout(snap: StillPostReadoutSnapshot?) {
        lastStillPostReadout.set(snap)
        lastStillPostReadoutListener.get()?.invoke(snap)
    }

    @Volatile private var desiredHighSpeedSize: Size? = null
    @Volatile private var desiredSurfaceSize: Size? = null
    @Volatile private var currentSurfaceSize: Size? = null
    /** Chrome video RES picker — drives HFR [StreamConfigurationMap] size (Sprint **13V.16**). */
    @Volatile private var inAppVideoEncodeSizePref: Size? = null
    @Volatile private var generation: Long = 0L
    /**
     * Set when [session] is assigned from a non-stale [CameraCaptureSession.StateCallback.onConfigured].
     * Guards [canCaptureRawStill] against transient readers + an older session object during gen bumps.
     */
    @Volatile private var sessionCommittedGeneration: Long = -1L
    /**
     * True between submitting an async [android.hardware.camera2.CameraDevice.createCaptureSession]
     * (or high-speed / macro variant) and the corresponding [CameraCaptureSession.StateCallback] firing.
     * [maybeRestartBody] defers [closeCamera] while this is set so we do not bump [generation] under
     * an in-flight configure (stale `onConfigured` / `No RAW buffer` on cold start).
     */
    @Volatile private var captureSessionAsyncConfigurePending: Boolean = false
    /** True between [CameraManager.openCamera] and [CameraDevice.StateCallback.onOpened] (or error paths). */
    @Volatile private var cameraDeviceOpenPending: Boolean = false
    private var maybeRestartSessionPendingDeferrals: Int = 0
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
    /** When true and still pipeline uses [CaptureLocationBridge.snapshot], embed GPS in DNG/JPEG EXIF. */
    @Volatile private var stillEmbedLocationInFiles: Boolean = false
    @Volatile private var textureWindowStartNs: Long = 0L
    @Volatile private var textureWindowFrames: Long = 0L

    /** Latest repeating-request metering triple; updated atomically from capture callbacks. */
    private val previewMetadata = AtomicReference(PreviewMetadata(null, null, null))
    /** [CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID] when the HAL reports a logical switch. */
    @Volatile private var lastLogicalMultiCameraPhysicalId: String? = null

    /** Null = automatic AE for sensitivity / exposure time; non-null forces manual sensor row (AE off). */
    @Volatile private var manualIsoOverride: Int? = null

    @Volatile private var manualExposureNsOverride: Long? = null

    @Volatile private var manualAwbModeOverride: Int? = null

    /** Manual focus distance (diopters) when [commandDialMode] is [CommandDialMode.M] or [previewFocusSelection] is manual. */
    @Volatile private var manualFocusDiopters: Float? = null

    /** Sprint **14.8** — readout / settings focus picker (dial **S** / **M** still override in [applyScalerCropAndMetering]). */
    @Volatile private var previewFocusSelection: PreviewFocusSelection = PreviewFocusSelection.Auto

    @Volatile private var lastFocusPeakingDiagSig: Int = Int.MIN_VALUE

    /** GLES preview tint — see [ReadoutAwbPreviewShaderGains]. */
    private val previewShaderWbRgb = AtomicReference(floatArrayOf(1f, 1f, 1f))
    @Volatile private var readoutWbShaderChangedListener: Runnable? = null
    private var loggedChromeUxReadout: Boolean = false
    /** One-shot per camera session for Milestone 9.12 ChromeUx ADB gates (devices with/without flash). */
    private var loggedChromeUxFlashHardware: Boolean = false
    private var readoutFallbackRunnable: Runnable? = null

    private var rawImageReader: ImageReader? = null
    private var rawPreviewFormat: Int = ImageFormat.RAW_SENSOR
    private var rawPreviewSize: android.util.Size? = null
    /** Hardware JPEG still target for RAW+JPEG dual capture (tonal companion). */
    private var jpegImageReader: ImageReader? = null
    /** Encode / DNG save lane — CAPTURE_ARCHITECTURE.md (`PNS.Reader`). */
    private val ioExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "PNS.Reader").apply { isDaemon = true }
        }

    /** LUT + companion JPEG after RAW/DNG — fixed pool (Milestone 2.2). */
    private val companionJpegExecutor: ExecutorService =
        Executors.newFixedThreadPool(2) { r ->
            Thread(r, "PNS.Jpeg").apply { isDaemon = true }
        }

    /** YUV histogram + highlight metering — CAPTURE_ARCHITECTURE.md (`PNS.Meter`). */
    private val meterExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "PNS.Meter").apply { isDaemon = true }
        }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureBusy = AtomicBoolean(false)

    /** Background AVIF/JXL encodes after HAL delivered the hardware JPEG ([captureIndependentTonalStill]). */
    private val pendingTonalEncodes = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * [maybeRestartBody] must not [closeCamera] while a still/bracket owns the session; otherwise
     * ultra_max ADB sequential RAW can stall with no `onCaptureCompleted` (M6 `dng50708IfdOk`).
     */
    private val pendingMaybeRestartAfterCapture = AtomicBoolean(false)

    /**
     * True only while ADB sequential RAW / bracket is executing **captures** (after session-ready
     * waits). Paired with [maybeRestartBody] defer when [device] is non-null so layout-driven
     * [maybeRestart] does not [closeCamera] between pump completion and [captureRawStill] (or
     * between burst shots when [captureBusy] briefly clears).
     */
    private val adbScriptedStillAutomationActive = AtomicBoolean(false)

    private val faceTracker = TrackerState()

    /** User tap on preview → AF/AE metering patch (sensor space); cleared when camera id changes. */
    private var tapMeteringRect: MeteringRectangle? = null

    /**
     * Primary face (buffer-mapped box) → AE/AF metering in active array when Eye-AF HUD is on.
     * Cleared when HUD off, camera closes, or user tap-focus overrides.
     */
    private var facePriorityMeteringRect: MeteringRectangle? = null

    /** Quantized signature to avoid rebuilding repeating request every ML frame. */
    private var lastFaceMeteringSig: Int = Int.MIN_VALUE

    /** When true, skip high-frequency FPS `Log.d` lines so scripted ADB runs retain early `PNS.AdbValidation` lines in logcat. */
    @Volatile
    var suppressPeriodicFpsLogs: Boolean = false

    /**
     * When true (sequential RAW / bracket automation only), face detect + tracker are silenced to cut log noise.
     * Dial-only automation keeps this **false** so Eye-AF / 3D tracking validation can run.
     */
    @Volatile
    var automationSuppressFacePipeline: Boolean = false

    /** Sprint **PO.2**: app in background — skip optional YUV analysis until [ON_RESUME]. */
    @Volatile
    var lifecycleBackgroundPaused: Boolean = false

    /** Scripted Super Macro vendor close-up request (`EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE`). */
    @Volatile
    var superMacroAdbProbe: Boolean = false

    @Volatile
    var requestedStillCaptureMode: StillCaptureMode = StillCaptureMode.Standard

    @Volatile
    var adbAutomationVideoDcg: Boolean = false

    @Volatile
    var adbAutomationVideoTenBit: Boolean = false

    @Volatile
    var adbAutomationVideoStabilization: Boolean = false

    @Volatile
    var adbAutomationVideoAv1: Boolean = false

  /** ADB / automation codec ordinal (`pns_preview_video_codec_ordinal`); wins over session chrome snapshot. */
    @Volatile
    var adbAutomationVideoCodecOrdinal: Int? = null

    /** Sprint **15.10** — ADB proof mode: emit `readoutChase` logs more frequently. */
    @Volatile
    var adbReadoutChaseProof: Boolean = false

    @Volatile
    var adbForceRawVideoLane: Boolean = false

    private var zslStillRing: ZslStillFrameRing? = null

    fun effectiveStillCaptureMode(): StillCaptureMode {
        val effective = StillCaptureModePolicy.effectiveForCapture(requestedStillCaptureMode)
        if (
            effective == StillCaptureMode.Standard &&
            readHudCapturePrefs().preCaptureBufferEnabled &&
            StillCaptureModePolicy.isZslStillImplemented()
        ) {
            return StillCaptureMode.ZslStill
        }
        return effective
    }

    fun applyStillCaptureModeForPipeline(mode: StillCaptureMode) {
        requestedStillCaptureMode = mode
        if (mode == StillCaptureMode.ZslStill && StillCaptureModePolicy.isZslStillImplemented()) {
            zslStillRing =
                ZslStillFrameRing(
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.zslStillRingCapacity(),
                )
        } else {
            zslStillRing?.clear(closeImages = true)
            zslStillRing = null
            detachZslRawRingListener()
        }
    }

    private fun wantsZslStillRing(): Boolean {
        if (!StillCaptureModePolicy.isZslStillImplemented()) return false
        if (readHudCapturePrefs().preCaptureBufferEnabled) return true
        return requestedStillCaptureMode == StillCaptureMode.ZslStill
    }

    /** Re-attach ZSL ring listener when CC.1 pre-capture buffer toggles in HUD. */
    fun refreshPreCaptureRingFromHud() {
        if (wantsZslStillRing()) {
            ensureZslRing()
            attachZslRawRingListener()
            Log.i(tag, "preCaptureBuffer enabled ringCapacity=${zslStillRing?.size() ?: 0}")
        } else if (requestedStillCaptureMode != StillCaptureMode.ZslStill) {
            zslStillRing?.clear(closeImages = true)
            zslStillRing = null
            detachZslRawRingListener()
        }
    }

    private fun ensureZslRing() {
        if (!wantsZslStillRing()) return
        if (zslStillRing == null) {
            zslStillRing =
                ZslStillFrameRing(
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.zslStillRingCapacity(),
                )
        }
    }

    private fun detachZslRawRingListener() {
        if (rawVideoController.isRecording) return
        rawImageReader?.setOnImageAvailableListener(null, null)
    }

    private fun attachRawVideoDrainListener() {
        val reader = rawImageReader ?: return
        val ringHandler = handler ?: return
        reader.setOnImageAvailableListener({ r ->
            if (!rawVideoController.isRecording) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            if (captureBusy.get()) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            rawVideoController.offerFrame(img)
        }, ringHandler)
        Log.i(tag, "rawVideo drain listener attached")
    }

    private fun detachRawVideoDrainListener(force: Boolean = false) {
        if (!force && rawVideoController.isRecording) return
        rawImageReader?.setOnImageAvailableListener(null, null)
        if (wantsZslStillRing()) {
            attachZslRawRingListener()
        }
    }

    private fun attachZslRawRingListener() {
        val reader = rawImageReader ?: return
        val ringHandler = handler ?: return
        if (!wantsZslStillRing()) {
            detachZslRawRingListener()
            return
        }
        ensureZslRing()
        reader.setOnImageAvailableListener({ r ->
            if (!wantsZslStillRing() || captureBusy.get()) {
                val dropped = runCatching { r.acquireLatestImage() }.getOrNull()
                runCatching { dropped?.close() }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val ring = zslStillRing ?: run {
                runCatching { img.close() }
                return@setOnImageAvailableListener
            }
            ring.offerImage(img)
        }, ringHandler)
    }

    private fun feedZslRingResult(result: TotalCaptureResult) {
        if (!wantsZslStillRing()) return
        ensureZslRing()
        zslStillRing?.offerResult(result)
    }

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

    /**
     * Some OEMs populate [CaptureResult.STATISTICS_FACES] in partial metadata but ship an empty
     * array in the matching [TotalCaptureResult]; we skip clearing on that final result when the
     * frame number matches the last partial that had faces.
     */
    private var lastPartialFacesFrameNumber: Long = -1L

    private var lastHighlightMeterAdbLogMs: Long = 0L

    private var commandDialMode: CommandDialMode = CommandDialMode.M

    @Volatile
    private var previewFlashMode: PreviewFlashMode = PreviewFlashMode.Auto
    private var previewFlashStrengthPercent: Int = HudSettings.PREVIEW_FLASH_STRENGTH_MAX

    private var yuvImageReader: ImageReader? = null

    /**
     * [processYuvForHighlight] posts [Image]s to [meterExecutor]; the ImageReader callback returns
     * immediately. Without this gate, stacked callbacks acquire frames faster than the executor
     * closes them → IllegalStateException (maxImages already acquired).
     */
    private val yuvAnalysisInFlight = AtomicBoolean(false)

    /**
     * Highlight (H): [HighlightMeter] EV → [CONTROL_AE_EXPOSURE_COMPENSATION] while **AE stays ON**.
     * Manual ISO/shutter was removed: frequent [CONTROL_AE_MODE_OFF] + SENSOR_* updates fought the ISP
     * and caused visible “breathing”; compensation nudges exposure without tearing down auto AE.
     */
    @Volatile
    private var lastAppliedHighlightComp: Int? = null

    private var lastHighlightProcessWallMs: Long = 0L

    private val highlightMeterMinIntervalMs: Long = 380L

    /** Min gap between AE-comp-driven [refreshRepeatingPreviewOnly] calls in Highlight (H) mode. */
    private val highlightAeRefreshMinGapMs: Long = 900L

    private var lastHighlightAeRefreshWallMs: Long = 0L

    /** Smooth histogram EV before mapping to an integer AE compensation index. */
    private var highlightMeterEvEma: Double = Double.NaN

    /**
     * Low-pass **darken engagement** from [HighlightMeter.suggestEvCorrectionBreakdown] so histogram
     * threshold noise does not flip [CONTROL_AE_EXPOSURE_COMPENSATION] frame-to-frame (breathing).
     * Asymmetric: faster when engagement **drops** (release stop-down), slower when it **rises**.
     */
    private var highlightDarkenEngageEma: Double = Double.NaN

    /**
     * Snap tiny oscillations to 0 EV unless already asking to darken — avoids killing moderate
     * negative corrections that protect highlights.
     */
    private val highlightEvStabilityZone: Double = 0.24

    /** Smoothed EV at or below this (negative) never passes through the stability snap to 0. */
    private val highlightMeterStabilityDarkenBypassEv: Double = 0.11

    /** Minimum |EV| before posting a negative (darken) compensation change. */
    private val highlightMeterEvDeadbandDarken: Double = 0.095

    /** Unused when [highlightMeterDarkenOnly] — Highlight (H) never brightens. */
    private val highlightMeterEvDeadbandBrighten: Double = 0.155

    /**
     * Highlight dial: protect **highlights / whites** only; do not pump brightness via positive EV.
     */
    private val highlightMeterDarkenOnly: Boolean = true

    /** Sprint **15.10** — locked-axis YUV chase (see [ReadoutExposureChase]). */
    @Volatile
    private var readoutIsoBand: ReadoutIsoBand = ReadoutIsoBand.FULL

    private var readoutChaseMedianEma: Double = Double.NaN

    @Volatile
    private var readoutChaseExposureNs: Long? = null

    @Volatile
    private var readoutChaseIso: Int? = null

    private var lastReadoutChaseProcessWallMs: Long = 0L

    private var lastReadoutChaseRefreshWallMs: Long = 0L

    private var lastReadoutChaseLogWallMs: Long = 0L

    private val readoutChaseHistMinIntervalMs: Long = 50L

    private val readoutChaseRefreshMinGapMs: Long = 150L

    private var hudFaceOverlayEnabled: Boolean = false

    @Volatile
    private var smileStillEnabled: Boolean = false

    @Volatile
    private var smileStillCaptureListener: (() -> Unit)? = null

    private var faceHudOverlayListener: ((FaceHudOverlayState) -> Unit)? = null

    /** Last Camera2 / tracker eye marks (buffer space). */
    private var faceHudLastEyes: List<EyeMark> = emptyList()

    /** Face rectangles from Camera2 statistics (buffer space). */
    private var faceHudLastCameraFaceBoxes: List<FaceTrackBoxBuffer> = emptyList()

    /** Raw ML Kit boxes + eyes (buffer space) for multi-face HUD when Camera2 stats are empty. */
    private var faceHudMlRawBoxes: List<FaceTrackBoxBuffer> = emptyList()
    private var faceHudMlRawEyes: List<EyeMark> = emptyList()

    /** Smoothed primary face for AF/AE weighting only (see [MlFaceBoxSmoother]). */
    private var faceHudMlSmoothedMeteringPrimary: FaceTrackBoxBuffer? = null

    private var lastMlFaceProcessWallMs: Long = 0L

    private var lastFaceGeometryDiagLogWallMs: Long = 0L
    private var lastSmileProcessWallMs: Long = 0L
    private var lastSmileDiagLogWallMs: Long = 0L
    private val smileMinIntervalMs: Long = 80L

    /** Cap only how often we *start* ML work (~120 Hz); HAL + ML Kit still bound real FPS. */
    private val mlFaceMinIntervalMs: Long = 8L

    /** After this many consecutive empty ML detections, require a longer interval between ML starts. */
    private val mlFaceEmptyBackoffAfterFrames: Int = 6

    /** ~30 Hz max ML starts when the scene has had no faces for a while (thermal / CPU). */
    private val mlFaceEmptyBackoffIntervalMs: Long = 33L

    private var mlConsecutiveEmptyMlDetections: Int = 0

    private var loggedMlFaceSample: Boolean = false

    /** Temporal smoothing for ML Kit boxes when Camera2 stats faces are empty. */
    private val mlFaceBoxSmoother = MlFaceBoxSmoother()

    private val histogramUiMinIntervalMs: Long = 150L

    @Volatile
    private var previewHistogramEnabled: Boolean = false

    @Volatile
    private var highlightClipZebraEnabled: Boolean = false

    private var lastZebraProcessWallMs: Long = 0L

    private val zebraMinIntervalMs: Long = 180L

    private var previewHistogramListener: ((IntArray?) -> Unit)? = null

    private var highlightClipZebraListener: ((HighlightClipZebraFrame?) -> Unit)? = null

    /** Sprint **14.4** — live ZXing decode on preview YUV when dial is [CommandDialMode.Qr]. */
    private var qrScanListener: ((text: String?, format: String?) -> Unit)? = null

    private var lastQrDecodeWallMs: Long = 0L

    fun setQrScanListener(listener: ((text: String?, format: String?) -> Unit)?) {
        qrScanListener = listener
    }

    private fun wantsQrScan(): Boolean =
        commandDialMode == CommandDialMode.Qr && !automationSuppressFacePipeline

    fun setPreviewHistogramListener(listener: ((IntArray?) -> Unit)?) {
        previewHistogramListener = listener
    }

    fun setHighlightClipZebraListener(listener: ((HighlightClipZebraFrame?) -> Unit)?) {
        highlightClipZebraListener = listener
    }

    /** When toggled, may rebuild the capture session to add/remove the YUV analysis surface. */
    fun setPreviewHistogramEnabled(enabled: Boolean) {
        if (previewHistogramEnabled == enabled) return
        previewHistogramEnabled = enabled
        if (!enabled) {
            handler?.post { previewHistogramListener?.invoke(null) }
        }
        maybeRestart()
    }

    /** When toggled, may rebuild the capture session (YUV analysis stream). */
    fun setHighlightClipZebraEnabled(enabled: Boolean) {
        if (highlightClipZebraEnabled == enabled) return
        highlightClipZebraEnabled = enabled
        if (!enabled) {
            handler?.post { highlightClipZebraListener?.invoke(null) }
        }
        maybeRestart()
    }

    /** Front-camera preview mirroring for overlays (finder UX). */
    fun previewMirrorHorizontally(): Boolean {
        val id = selectedCameraId ?: return false
        val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return false
        return chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    }

    /** Latest [SurfaceTexture.getTransformMatrix] from the GLES preview (column-major 4×4). */
    fun readPreviewSurfaceTransformMatrix(dest: FloatArray): Boolean =
        lutPreviewRendererForDual?.readSurfaceTransformMatrix(dest) == true

    private fun highlightAeTryVendorExtraModes(): Boolean =
        VendorHighlightAePrefs.isTryExtraModesEnabled(appContext) &&
            RootCapabilityStore.loadOrUnknown(appContext).grantsPrivileged

    private fun usesHardwareHighlightAe(chars: CameraCharacteristics): Boolean =
        commandDialMode == CommandDialMode.H &&
            desiredFps < 120 &&
            HighlightAeModeSupport.supportsHardwareHighlightForHMode(chars, highlightAeTryVendorExtraModes())

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
        // Never call setDefaultBufferSize(width,height) from TextureView layout hints here: Compose
        // can destroy/recreate the SurfaceTexture in the same frame; touching the ST from main
        // races teardown and logs "SurfaceTexture is abandoned" (M6 cold start). [maybeRestartBody]
        // sets the authoritative buffer size from Camera2 on [handler].
        // While [device] is non-null, [createSession] may be using [previewSurface] on [handler].
        // A duplicate TextureView `onSurfaceTextureAvailable` on the main thread must not call
        // [rebuildSurfaceIfPossible] (it releases the old [Surface]) or the camera thread hits
        // IllegalArgumentException: Surface was abandoned in OutputConfiguration (Sprint 5.3).
        if (device == null) {
            rebuildSurfaceIfPossible()
            maybeRestart()
        }
    }

    fun onSurfaceTextureSizeChanged(width: Int, height: Int) {
        Log.d(tag, "textureSizeChanged ${width}x${height}")
        reconcilePreviewBufferSizeFromCallbacks(width, height)
        // GLES [GLSurfaceView.onSurfaceChanged] reports **view** WxH on every finder layout
        // settle (e.g. 1411×1881 → 1411×1058). maybeRestart() here restarted Camera2 each
        // time and caused preview tearing / streaking. Stream size changes only via
        // [maybeRestartBody] + [setDefaultBufferSize] on the camera thread.
    }

    /**
     * Keep [currentSurfaceSize] aligned with [desiredSurfaceSize] once the camera stream is
     * negotiated. TextureView listener width/height are **view/buffer surface hints** and on some
     * OEMs diverge from [setDefaultBufferSize] — treating them as the camera buffer WxH breaks
     * [TexturePreviewFit] aspect math and distorts the chart vs RAW.
     */
    private fun reconcilePreviewBufferSizeFromCallbacks(
        @Suppress("UNUSED_PARAMETER") textureWidth: Int,
        @Suppress("UNUSED_PARAMETER") textureHeight: Int,
    ) {
        // GLES [GLSurfaceView] and legacy TextureView listeners pass **view** WxH, not the Camera2
        // stream. Never stash those as [currentSurfaceSize] — it made [TexturePreviewFit] / GLES
        // treat the finder size as the buffer and squash the preview vertically.
        val want = desiredSurfaceSize ?: return
        currentSurfaceSize = want
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
     * May be called from a background thread ([PixelCopy] waits on a latch).
     */
    fun grabPreviewFrameBitmap(glView: GLSurfaceView): Bitmap? {
        val vw = glView.width
        val vh = glView.height
        if (vw <= 0 || vh <= 0) {
            Log.w(tag, "grabPreviewFrameBitmap: bad view size ${vw}x${vh}")
            return null
        }
        val bmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())
        var copyResult = PixelCopy.ERROR_UNKNOWN
        var requestFailed = false
        mainHandler.post {
            try {
                PixelCopy.request(
                    glView,
                    bmp,
                    { result ->
                        copyResult = result
                        latch.countDown()
                    },
                    mainHandler,
                )
            } catch (ex: Exception) {
                Log.w(tag, "grabPreviewFrameBitmap PixelCopy.request failed: ${ex.message}")
                requestFailed = true
                latch.countDown()
            }
        }
        return try {
            if (!latch.await(2, TimeUnit.SECONDS) || requestFailed || copyResult != PixelCopy.SUCCESS) {
                Log.w(tag, "grabPreviewFrameBitmap: PixelCopy failed result=$copyResult failed=$requestFailed")
                bmp.recycle()
                null
            } else {
                PnsAdbLog.i(
                    appContext,
                    "calibrate preview frame grab ok ${bmp.width}x${bmp.height}",
                )
                bmp
            }
        } catch (ex: Exception) {
            Log.w(tag, "grabPreviewFrameBitmap failed: ${ex.message}")
            bmp.recycle()
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
            previewShaderWbRgb.set(floatArrayOf(1f, 1f, 1f))
            notifyReadoutWbShaderChanged()
            val phys = previewSurfacePhysicalCameraId
            val newId = selectedCameraId
            val keepTelePreviewPin =
                phys != null &&
                    newId != null &&
                    logicalParentForPhysicalCamera(cm, phys, cameraIds()) == newId
            if (!keepTelePreviewPin) {
                previewSurfacePhysicalCameraId = null
            }
        }
        this.selectedCameraId = selectedCameraId
        this.desiredFps = desiredFps
        if (changed) Log.d(tag, "setDesired cameraId=${selectedCameraId ?: "null"} fps=$desiredFps previewPhysical=${previewSurfacePhysicalCameraId ?: "none"}")
        if (camChanged && dualVideoActive) {
            dualFrontDelayedPreviewTry = false
            cancelDualFrontDelayedPreviewTry()
            closeDualFrontCamera()
            scheduleDualFrontDelayedPreviewTry()
        }
        if (changed) maybeRestart()
    }

    /** User-selected in-app video encode resolution from [PreviewChromePreferences]. */
    fun setInAppVideoEncodeSize(size: Size) {
        val next = size.takeIf { it.width > 0 && it.height > 0 }
        if (next == inAppVideoEncodeSizePref) return
        inAppVideoEncodeSizePref = next
        Log.i(
            "PNS.VideoEncode",
            "encodePrefSet ${next?.width ?: 0}x${next?.height ?: 0} fps=$desiredFps",
        )
        handler?.post {
            if (!inAppVideoRecordingArmed) {
                if (videoController.isRecorderPresent() && !videoController.isRecorderStarted()) {
                    Log.i(
                        tag,
                        "encodePrefSet: discarding idle prepared recorder (format/res change before record)",
                    )
                    videoController.tearDownForCloseCamera()
                }
                if (desiredFps >= 120) {
                    maybeRestartBody()
                }
            }
        }
    }

    /** Apply after [setDesired] / Compose [LaunchedEffect] so a new camera id does not wipe tele routing. */
    fun setPreviewSurfacePhysicalCameraId(id: String?) {
        if (previewSurfacePhysicalCameraId == id) return
        previewSurfacePhysicalCameraId = id
        Log.d(tag, "previewSurfacePhysicalCameraId=${id ?: "null"}")
        maybeRestart()
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
    fun previewFocusSelection(): PreviewFocusSelection = previewFocusSelection

    fun wantsMacroProgram(): Boolean =
        PreviewMacroProgram.wantsMacroProgram(commandDialMode, previewFocusSelection)

    fun previewFocusMenuSelections(): List<PreviewFocusSelection> {
        val camId = selectedCameraId ?: return listOf(PreviewFocusSelection.Auto)
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return listOf(PreviewFocusSelection.Auto)
        return PreviewFocusMode.menuSelections(PreviewFocusMode.availableAfModes(chars))
    }

    fun setPreviewFocusSelection(selection: PreviewFocusSelection) {
        if (previewFocusSelection == selection && selection != PreviewFocusSelection.ManualDistance) return
        previewFocusSelection = selection
        val camId = selectedCameraId
        val chars =
            camId?.let { runCatching { cm.getCameraCharacteristics(it) }.getOrNull() }
        when (selection) {
            PreviewFocusSelection.Auto -> {
                manualFocusDiopters = null
            }
            PreviewFocusSelection.ManualDistance -> {
                if (chars != null && manualFocusDiopters == null) {
                    manualFocusDiopters = ManualFocusDistance.defaultForLens(chars)
                }
                chars?.let { logManualFocusPeakingDiag(it, selectedCameraId ?: "?") }
            }
            is PreviewFocusSelection.HalAf -> {
                manualFocusDiopters = null
            }
        }
        Log.i(
            "PNS.ChromeUx",
            "focusMode=${PreviewFocusMode.chromeUxLogValue(selection, manualFocusDiopters)}",
        )
        refreshRepeatingPreviewOnly()
        maybeRestart()
    }

    fun setPreviewFocusManualDiopters(diopters: Float) {
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val range = ManualFocusDistance.focusRange(chars)
        if (!range.sliderEnabled) {
            Log.w(
                "PNS.FocusPeaking",
                "manualFocus rack ignored cameraId=$camId fixedAtInfinity=${range.fixedAtInfinity}",
            )
            return
        }
        previewFocusSelection = PreviewFocusSelection.ManualDistance
        manualFocusDiopters = ManualFocusDistance.clamp(diopters, range.maxDiopters)
        logManualFocusPeakingDiag(chars, camId, range)
        Log.i(
            "PNS.ChromeUx",
            "focusMode=${PreviewFocusMode.chromeUxLogValue(previewFocusSelection, manualFocusDiopters)} " +
                "rack=${"%.3f".format(manualFocusDiopters)}/${"%.3f".format(range.maxDiopters)}",
        )
        refreshRepeatingPreviewOnly()
    }

    fun setCommandDialMode(mode: CommandDialMode) {
        if (commandDialMode == mode) return
        commandDialMode = mode
        if (mode != CommandDialMode.Dual) {
            setDualVideoActive(false)
        }
        if (mode != CommandDialMode.M && previewFocusSelection != PreviewFocusSelection.ManualDistance) {
            manualFocusDiopters = null
        }
        resetHighlightMeterPipelineState()
        if (mode != CommandDialMode.Qr) {
            handler?.post { qrScanListener?.invoke(null, null) }
        }
        Log.d(tag, "setCommandDialMode mode=$mode")
        maybeRestart()
    }

    fun setDualVideoActive(active: Boolean) {
        if (dualVideoActive == active) return
        dualVideoActive = active
        if (!active) {
            dualFrontOpenPending = false
            dualFrontOpenForced = false
            dualFrontDelayedPreviewTry = false
            cancelDualFrontDelayedPreviewTry()
            closeDualFrontCamera()
            releaseDualFrontHandler()
            dualVideoEncoderSink.release()
            if (previewSurfacePhysicalCameraId == dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_WIDE &&
                selectedCameraId == dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_LOGICAL
            ) {
                previewSurfacePhysicalCameraId = null
            }
            Log.i(DualVideoRecordingController.TAG, "setDualVideoActive=false")
            maybeRestart()
            return
        }
        // Enabling dual only arms GL split + front cam — rebuilding the rear session here races
        // front SurfaceTexture setup (abandoned surface / 0 MediaCodec frames on CPH2655).
        val frontId = Camera2Facing.frontCameraId(cm, cameraIds())
        val resolved =
            DualVideoHalConcurrency.resolveRearForDual(cm, selectedCameraId, frontId)
        val rearForDual = resolved.rearId ?: selectedCameraId
        dualHalConcurrencyProbe = DualVideoHalConcurrency.probe(cm, rearForDual, frontId)
        if (rearForDual == dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_LOGICAL) {
            setPreviewSurfacePhysicalCameraId(dev.pointandshoot.fleet.OnePlus13FleetPolicy.CANONICAL_WIDE)
        }
        dualFrontDelayedPreviewTry = false
        closeDualFrontCamera()
        scheduleDualFrontDelayedPreviewTry()
        Log.i(
            DualVideoRecordingController.TAG,
            "setDualVideoActive=true (dual preview + record stacked) rear=$rearForDual concurrent=${resolved.pairAdvertisedConcurrent}",
        )
    }

    fun scheduleDualFrontDelayedPreviewTry() {
        cancelDualFrontDelayedPreviewTry()
        val probe = dualHalConcurrencyProbe ?: return
        if (DualVideoHalConcurrency.allowSimultaneousDualPreview(probe)) {
            dualFrontOpenPending = true
            scheduleDualFrontOpenAfterRearSettle()
            return
        }
        val hnd = handler ?: return
        val runnable =
            object : Runnable {
                private var attempts = 0

                override fun run() {
                    if (!dualVideoActive || dualFrontDelayedPreviewTry) return
                    val minRear = DualVideoHalConcurrency.minRearFramesBeforeFrontOpen(probe)
                    if (previewTextureFrameCount() < minRear && attempts < 80) {
                        attempts++
                        hnd.postDelayed(this, 100)
                        return
                    }
                    dualFrontDelayedPreviewTry = true
                    Log.i(
                        DualVideoRecordingController.TAG,
                        "dualFront delayed preview try rearFrames=${previewTextureFrameCount()}",
                    )
                    dualFrontOpenPending = true
                    val st = dualFrontSurfaceTexture
                    if (st != null) {
                        openDualFrontCameraLocked(st, dualFrontSurfaceW, dualFrontSurfaceH)
                    } else {
                        scheduleDualFrontOpenAfterRearSettle()
                    }
                }
            }
        dualFrontDelayedOpenRunnable = runnable
        hnd.postDelayed(runnable, 2_500L)
    }

    fun maybeArmDualStackedPreview() {
        if (!dualVideoActive || !peekDualFrontSessionReady()) return
        Log.i(DualVideoRecordingController.TAG, "dualFront preview ready — nudge render")
        glSurfaceHostForDual?.requestRender()
    }

    private fun cancelDualFrontDelayedPreviewTry() {
        dualFrontDelayedOpenRunnable?.let { mainHandler.removeCallbacks(it) }
        dualFrontDelayedOpenRunnable = null
    }

    private fun ensureDualFrontHandler(): Handler {
        val existing = dualFrontHandler
        if (existing != null) return existing
        val thread = HandlerThread("PNS.DualFront")
        thread.start()
        dualFrontThread = thread
        return Handler(thread.looper).also { dualFrontHandler = it }
    }

    private fun releaseDualFrontHandler() {
        dualFrontController?.close()
        dualFrontController = null
        dualFrontHandler = null
        dualFrontThread?.quitSafely()
        dualFrontThread = null
    }

    fun peekDualVideoActive(): Boolean = dualVideoActive

    fun peekDualFrontOpenForced(): Boolean = dualFrontOpenForced

    fun clearDualFrontOpenForced() {
        dualFrontOpenForced = false
    }

    fun dualConcurrentRearFrontSupported(): Boolean =
        DualVideoRecordingController.canRunConcurrentRearFront(
            cm,
            selectedCameraId,
            Camera2Facing.frontCameraId(cm, cameraIds()),
        )

    private fun dualFrontOpenAllowedNow(): Boolean {
        val probe = dualHalConcurrencyProbe ?: return dualVideoActive
        return dualVideoActive &&
            DualVideoHalConcurrency.allowFrontCameraOpen(
                probe = probe,
                recordingArmed = inAppVideoRecordingArmed || dualFrontOpenForced,
                delayedPreviewTry = dualFrontDelayedPreviewTry,
            )
    }

    fun onDualFrontSurfaceTextureReady(st: SurfaceTexture, w: Int, h: Int) {
        if (!dualVideoActive) {
            Log.d(DualVideoRecordingController.TAG, "dualFront: dual video not active, ignoring surface")
            return
        }
        dualFrontSurfaceTexture = st
        dualFrontSurfaceW = w
        dualFrontSurfaceH = h
        if (!dualFrontOpenAllowedNow()) return
        dualFrontOpenPending = true
        scheduleDualFrontOpenAfterRearSettle()
    }

    /** Close front and reopen after rear [CameraCaptureSession] is configured — avoids starving rear preview/encode. */
    private fun recycleDualFrontForRearSessionRebuild() {
        if (!dualVideoActive) return
        dualFrontController?.close()
        dualFrontOpenPending = dualFrontSurfaceTexture != null
        scheduleDualFrontOpenAfterRearSettle()
    }

    fun ensureDualFrontOpenForRecord() {
        if (!dualVideoActive) return
        dualFrontOpenForced = true
        dualFrontOpenPending = true
        scheduleDualFrontOpenAfterRearSettle()
    }

    private fun scheduleDualFrontOpenAfterRearSettle() {
        val hnd = handler ?: return
        var attempts = 0
        val settleRunnable =
            object : Runnable {
                override fun run() {
                    if (!dualVideoActive || !dualFrontOpenPending) return
                    val st = dualFrontSurfaceTexture ?: return
                    val minRear =
                        dualHalConcurrencyProbe?.let { DualVideoHalConcurrency.minRearFramesBeforeFrontOpen(it) }
                            ?: 8
                    val rearReady =
                        device != null &&
                            session != null &&
                            sessionCommittedGeneration == generation &&
                            previewTextureFrameCount() >= minRear
                    if (!rearReady && attempts < 200) {
                        attempts++
                        hnd.postDelayed(this, 50)
                        return
                    }
                    if (!rearReady) {
                        Log.w(
                            DualVideoRecordingController.TAG,
                            "dualFront open: rear not ready after wait " +
                                "device=${device != null} session=${session != null} " +
                                "sessCommit=$sessionCommittedGeneration gen=$generation " +
                                "rearFrames=${previewTextureFrameCount()}",
                        )
                    }
                    dualFrontOpenPending = false
                    openDualFrontCameraLocked(st, dualFrontSurfaceW, dualFrontSurfaceH)
                }
            }
        hnd.postDelayed(settleRunnable, 200)
    }

    private fun openDualFrontCameraLocked(st: SurfaceTexture, w: Int, h: Int) {
        if (!dualVideoActive) return
        if (!dualFrontOpenAllowedNow()) {
            dualFrontOpenPending = false
            return
        }
        if (dualFrontController?.sessionReady == true && dualFrontController?.hasValidFrames == true) {
            dualFrontOpenPending = false
            return
        }
        val hnd = handler ?: run {
            Log.e(DualVideoRecordingController.TAG, "dualFront: no handler available")
            return
        }
        val frontHandler = ensureDualFrontHandler()
        if (dualFrontController == null) {
            dualFrontController = DualVideoFrontCameraController(cm, frontHandler)
        }
        val frontId =
            Camera2Facing.frontCameraId(cm, cameraIds()) ?: run {
                Log.e(DualVideoRecordingController.TAG, "dualFront: no front camera id found")
                dualVideoActive = false
                return
            }
        val rearId = selectedCameraId
        if (!DualVideoRecordingController.canRunConcurrentRearFront(cm, rearId, frontId)) {
            Log.w(
                DualVideoRecordingController.TAG,
                "dualFront: concurrent pair not advertised rear=$rearId front=$frontId (trying anyway)",
            )
        }
        try {
            val pick = DualVideoFrontCameraController.pickFrontPreviewSize(cm, frontId)
            Log.i(
                DualVideoRecordingController.TAG,
                "dualFront open rearFrames=${previewTextureFrameCount()} " +
                    "cameraId=$frontId size=${pick.width}x${pick.height}",
            )
            st.setDefaultBufferSize(pick.width, pick.height)
            glSurfaceHostForDual?.queueEvent {
                lutPreviewRendererForDual?.setFrontBufferSize(pick.width, pick.height)
            }
            rearTextureFramesAtDualFrontOpen = previewTextureFrameCount()
            val surface = Surface(st)
            dualFrontController?.setPreviewSurface(surface)
            val matchedFps = DualVideoRecordingController.V1_TARGET_FPS
            val rearRange = pickNormalFpsRange(rearId ?: frontId, matchedFps)
            val frontFps = rearRange?.upper ?: matchedFps
            dualFrontController?.open(frontId, frontFps)
            Log.i(
                DualVideoRecordingController.TAG,
                "dualFront armed rearFrames=$rearTextureFramesAtDualFrontOpen " +
                    "frontSize=${pick.width}x${pick.height} matchedFps=$frontFps rearRange=$rearRange",
            )
            mainHandler.postDelayed({ maybeArmDualStackedPreview() }, 400)
            mainHandler.postDelayed({ checkDualVideoHealth() }, 15_000)
        } catch (e: Exception) {
            Log.e(DualVideoRecordingController.TAG, "dualFront setup failed: ${e.message}", e)
            dualVideoActive = false
        }
    }

    fun peekDualFrontSessionReady(): Boolean = dualFrontController?.sessionReady == true

    fun peekDualGlRecordArmed(): Boolean = dualGlRecordArmed

    fun markDualGlRecordArmed(armed: Boolean) {
        dualGlRecordArmed = armed
    }

    @Volatile var lutPreviewRendererForDual: LutCameraPreviewRenderer? = null

    fun closeDualFrontCamera() {
        if (dualVideoActive && inAppVideoRecordingArmed) {
            Log.d(
                DualVideoRecordingController.TAG,
                "closeDualFrontCamera skipped (dual record armed)",
            )
            return
        }
        Log.i(DualVideoRecordingController.TAG, "Closing dual front camera")
        dualFrontController?.close()
    }

    private fun useHfrInterleavedMcPreview(): Boolean =
        HfrInterleavedPreviewSupport.wantsInterleavedSession(
            desiredFps = desiredFps,
            wantsMediaCodecPath = videoController.wantsMediaCodecPath,
            dualVideoActive = dualVideoActive,
        )

    fun onHfrInterleavedComposeRecordState(recording: Boolean) {
        if (hfrInterleavedRecordActive == recording) return
        hfrInterleavedRecordActive = recording
        if (recording) {
            hfrInterleavedEncodeRetryDone = false
            clearHfrYuvMonitor()
        } else {
            cancelHfrInterleavedWatchdogs()
            clearHfrYuvMonitor()
            hfrInterleavedEncodeRetryDone = false
        }
    }

    private fun scheduleHfrInterleavedWatchdogs() {
        if (!useHfrInterleavedMcPreview()) return
        if (!hfrInterleavedRecordActive && !hfrEncoderOnlyRecordActive) return
        cancelHfrInterleavedWatchdogs()
        hfrEncodeWatchdogArmedAtMs = SystemClock.uptimeMillis()
        hfrInterleavedWatchdogToken++
        val token = hfrInterleavedWatchdogToken
        mainHandler.postDelayed({ if (token == hfrInterleavedWatchdogToken) hfrInterleavedEncodeWatchdog.run() }, 2_500L)
        mainHandler.postDelayed({ if (token == hfrInterleavedWatchdogToken) hfrInterleavedPreviewWatchdog.run() }, 2_000L)
    }

    private fun cancelHfrInterleavedWatchdogs() {
        hfrInterleavedWatchdogToken++
        hfrEncodeWatchdogArmedAtMs = 0L
        mainHandler.removeCallbacks(hfrInterleavedEncodeWatchdog)
        mainHandler.removeCallbacks(hfrInterleavedPreviewWatchdog)
    }

    fun peekWantsMediaCodecVideoRecord(): Boolean = videoController.wantsMediaCodecPath

    fun requestSessionRebuildHfr() {
        handler?.post { maybeRestart() }
    }

    @Volatile private var hfrEncoderOnlyRecordActive: Boolean = false
    @Volatile private var hfrMonitorYuvCaptureActive: Boolean = false
    @Volatile private var hfrMonitorTextureRotationDeg: Int = 0
    private var hfrMonitorRecordCameraId: String? = null
    private var hfrMonitorFinderCameraId: String? = null
    private var hfrMonitorController: HfrRecordMonitorCameraController? = null

    fun peekHfrEncoderOnlyMonitorActive(): Boolean = hfrEncoderOnlyRecordActive

    fun clearHfrYuvMonitor() {
        runCatching { hfrYuvMonitorImageReader?.close() }
        hfrYuvMonitorImageReader = null
    }

    private fun stopHfrRecordMonitor() {
        hfrEncoderOnlyRecordActive = false
        hfrMonitorYuvCaptureActive = false
        val h = handler
        if (h != null && android.os.Looper.myLooper() == h.looper) {
            stopHfrRecordMonitorOnHandler()
        } else {
            val mon = hfrMonitorController
            hfrMonitorController = null
            val ir = hfrYuvMonitorImageReader
            hfrYuvMonitorImageReader = null
            if (h != null) {
                h.post { tearDownHfrMonitorOnHandler(mon, ir) }
            } else {
                runCatching { ir?.close() }
            }
        }
        notifyHfrFinderMonitorGl(false)
    }

    private fun stopHfrRecordMonitorOnHandler() {
        check(handler != null && android.os.Looper.myLooper() == handler!!.looper)
        hfrEncoderOnlyRecordActive = false
        hfrMonitorYuvCaptureActive = false
        hfrMonitorRecordCameraId = null
        hfrMonitorFinderCameraId = null
        val mon = hfrMonitorController
        hfrMonitorController = null
        val ir = hfrYuvMonitorImageReader
        hfrYuvMonitorImageReader = null
        tearDownHfrMonitorOnHandler(mon, ir)
    }

    private fun tearDownHfrMonitorOnHandler(
        mon: HfrRecordMonitorCameraController?,
        ir: android.media.ImageReader?,
    ) {
        runCatching { ir?.setOnImageAvailableListener(null, null) }
        mon?.closeImmediateOnHandler()
        runCatching { ir?.close() }
    }

    private fun hfrMonitorTextureRotationDegrees(monitorId: String): Int {
        val chars = runCatching { cm.getCameraCharacteristics(monitorId) }.getOrNull() ?: return 0
        return mlInputImageRotationDegrees(chars)
    }

    /** Keep GLES drawing YUV monitor frames (not the starved record-camera SurfaceTexture). */
    private fun notifyHfrFinderMonitorGl(enabled: Boolean) {
        mainHandler.post {
            glSurfaceHostForDual?.queueEvent {
                lutPreviewRendererForDual?.setHfrYuvMonitorEnabled(enabled)
            }
            glSurfaceHostForDual?.requestRender()
        }
    }

    /** @return true when encoder-only HS + monitor camera are active; false → caller should fall back. */
    private fun startHfrRecordMonitor(): Boolean {
        val h = handler ?: return false
        val recordId = selectedCameraId ?: return false
        val monitorId =
            HfrRecordMonitorSupport.pickMonitorCameraId(cm, recordId, cameraIds()) ?: run {
                Log.w(HfrRecordMonitorSupport.TAG, "no monitor camera for recordId=$recordId")
                return false
            }
        if (hfrEncoderOnlyRecordActive &&
            hfrMonitorController != null &&
            hfrMonitorRecordCameraId == recordId &&
            hfrMonitorFinderCameraId == monitorId
        ) {
            return true
        }
        if (android.os.Looper.myLooper() == h.looper) {
            stopHfrRecordMonitorOnHandler()
        } else {
            stopHfrRecordMonitor()
        }
        if (!HfrRecordMonitorSupport.canRunConcurrent(cm, recordId, monitorId)) {
            Log.w(
                HfrRecordMonitorSupport.TAG,
                "concurrent set missing record=$recordId monitor=$monitorId — opening monitor anyway",
            )
        }
        hfrMonitorTextureRotationDeg = hfrMonitorTextureRotationDegrees(monitorId)
        val roles = BackCameraRoleResolver.resolve(cm, cameraIds())
        val applyRecordDigitalCrop = focalCropMode != null && desiredFps < 120
        val hfrMonitorTextureCrop =
            HfrMonitorPreviewCrop.computeTextureCrop(
                cm,
                roles,
                recordId,
                monitorId,
                focalCropMode,
                applyRecordDigitalCrop,
            )
        val size = HfrRecordMonitorSupport.pickMonitorPreviewSize(cm, monitorId)
        val ir =
            ImageReader.newInstance(
                size.width,
                size.height,
                ImageFormat.YUV_420_888,
                PerfBudget.Defaults.YUV_ANALYSIS_READER_MAX_IMAGES,
            )
        hfrYuvMonitorImageReader = ir
        hfrMonitorYuvCaptureActive = true
        ir.setOnImageAvailableListener(
            { reader ->
                if (!hfrMonitorYuvCaptureActive) {
                    runCatching { reader.acquireLatestImage()?.close() }
                    return@setOnImageAvailableListener
                }
                val image = runCatching { reader.acquireLatestImage() }.getOrNull()
                    ?: return@setOnImageAvailableListener
                try {
                    if (!hfrMonitorYuvCaptureActive) return@setOnImageAvailableListener
                    val frame =
                        HfrYuvImageCopier.copy(
                            image,
                            hfrMonitorTextureRotationDeg,
                            hfrMonitorTextureCrop,
                        ) ?: return@setOnImageAvailableListener
                    lutPreviewRendererForDual?.deliverHfrYuvFrame(frame)
                } finally {
                    image.close()
                }
            },
            h,
        )
        hfrMonitorController =
            HfrRecordMonitorCameraController(cm, h).also { mon ->
                mon.setPreviewSurface(ir.surface)
                mon.open(monitorId, size)
            }
        hfrEncoderOnlyRecordActive = true
        hfrMonitorRecordCameraId = recordId
        hfrMonitorFinderCameraId = monitorId
        val cropSpan = hfrMonitorTextureCrop.u1 - hfrMonitorTextureCrop.u0
        Log.i(
            HfrRecordMonitorSupport.TAG,
            "monitor finder active record=$recordId monitor=$monitorId ${size.width}x${size.height} " +
                "cropU=${"%.3f".format(hfrMonitorTextureCrop.u0)}-${"%.3f".format(hfrMonitorTextureCrop.u1)} " +
                "cropSpan=${"%.3f".format(cropSpan)} digitalCropOnRecord=$applyRecordDigitalCrop " +
                "focal=${focalCropMode?.name ?: "none"}",
        )
        PnsAdbLog.i(
            appContext,
            "hfrEncoderOnlyMonitor=true record=$recordId monitor=$monitorId " +
                "cropU=${hfrMonitorTextureCrop.u0}-${hfrMonitorTextureCrop.u1}",
        )
        notifyHfrFinderMonitorGl(true)
        return true
    }

    private var hfrYuvMonitorImageReader: ImageReader? = null
    
    private fun checkDualVideoHealth() {
        if (!dualVideoActive) return
        
        val controller = dualFrontController
        if (controller == null) {
            Log.w(DualVideoRecordingController.TAG, "dualFront health check: no controller")
            return
        }
        
        val isHealthy = controller.isFrameFlowHealthy()
        if (!isHealthy) {
            Log.w(
                DualVideoRecordingController.TAG,
                "dualFront health check: unhealthy sessionReady=${dualFrontController?.sessionReady} " +
                    "hasValidFrames=${dualFrontController?.hasValidFrames} (no auto-fallback — Sprint 15.5)",
            )
            mainHandler.postDelayed({ checkDualVideoHealth() }, 10_000)
        } else {
            Log.d(DualVideoRecordingController.TAG, "dualFront health check passed")
            maybeArmDualStackedPreview()
            mainHandler.postDelayed({ checkDualVideoHealth() }, 10_000)
        }
        val rearNow = previewTextureFrameCount()
        if (rearNow <= rearTextureFramesAtDualFrontOpen + 4L) {
            Log.w(
                DualVideoRecordingController.TAG,
                "dual rear frame counter dropped after front open " +
                    "rearFramesAtOpen=$rearTextureFramesAtDualFrontOpen now=$rearNow " +
                    "(non-concurrent HAL — not recycling; counter may reset on session churn)",
            )
        }
    }

    fun bindDualEncoderSurface(width: Int, height: Int) {
        val surf = videoController.getRecordingSurface() ?: return
        glSurfaceHostForDual?.queueEvent {
            dualVideoEncoderSink.setEncoderTarget(surf, width, height)
            if (!dualVideoEncoderSink.isEncoderSurfaceReady) {
                Log.w(DualVideoRecordingController.TAG, "dualEncoderSink bind failed on GL thread")
            }
        } ?: dualVideoEncoderSink.setEncoderTarget(surf, width, height)
    }

    fun peekDualEncoderSinkReady(): Boolean = dualVideoEncoderSink.isEncoderSurfaceReady

    /** Set from [PreviewMainViewport] so encoder bind can run on the GL thread. */
    @Volatile var glSurfaceHostForDual: GLSurfaceView? = null

    fun ensureManualFocusForDialM() {
        if (commandDialMode != CommandDialMode.M) return
        previewFocusSelection = PreviewFocusSelection.ManualDistance
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val range = ManualFocusDistance.focusRange(chars)
        ManualFocusDistance.logFocusRange(camId, chars)
        if (manualFocusDiopters == null) {
            manualFocusDiopters = ManualFocusDistance.defaultForLens(chars)
        } else {
            manualFocusDiopters = ManualFocusDistance.clamp(manualFocusDiopters!!, range.maxDiopters)
        }
        logManualFocusPeakingDiag(chars, camId, range)
        refreshRepeatingPreviewOnly()
    }

    fun clearManualFocusDistance() {
        if (previewFocusSelection == PreviewFocusSelection.ManualDistance) return
        manualFocusDiopters = null
    }

    fun nudgeManualFocusFromDrag(dragPixels: Float) {
        if (!wantsManualFocusDistance()) return
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val current = manualFocusDiopters ?: ManualFocusDistance.defaultForLens(chars)
        manualFocusDiopters = ManualFocusDistance.nudgeFromDrag(current, dragPixels, chars)
        logManualFocusPeakingDiag(chars)
        refreshRepeatingPreviewOnly()
    }

    private fun wantsManualFocusDistance(): Boolean =
        commandDialMode == CommandDialMode.M ||
            previewFocusSelection == PreviewFocusSelection.ManualDistance

    fun peekManualFocusActive(): Boolean = wantsManualFocusDistance() && manualFocusDiopters != null

    fun peekManualFocusDiopters(): Float? = manualFocusDiopters

    fun peekManualFocusMaxDiopters(): Float {
        val camId = selectedCameraId ?: return ManualFocusDistance.maxDioptersFromHalMinimumFocus(null)
        val chars =
            runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                ?: return ManualFocusDistance.maxDioptersFromHalMinimumFocus(null)
        return ManualFocusDistance.maxDiopters(chars)
    }

    fun peekManualFocusRange(): ManualFocusDistance.FocusRange? {
        val camId = selectedCameraId ?: return null
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return null
        return ManualFocusDistance.focusRange(chars)
    }

    fun setPreviewFlashMode(mode: PreviewFlashMode) {
        if (previewFlashMode == mode) return
        previewFlashMode = mode
        Log.d(tag, "setPreviewFlashMode mode=$mode")
        refreshRepeatingPreviewOnly()
    }

    fun setPreviewFlashStrengthPercent(percent: Int) {
        val next =
            percent.coerceIn(
                HudSettings.PREVIEW_FLASH_STRENGTH_MIN,
                HudSettings.PREVIEW_FLASH_STRENGTH_MAX,
            )
        if (previewFlashStrengthPercent == next) return
        previewFlashStrengthPercent = next
        Log.i(tag, "setPreviewFlashStrengthPercent percent=$next")
        refreshRepeatingPreviewOnly()
    }

    fun previewFlashStrengthPercent(): Int = previewFlashStrengthPercent

    /** Sprint **13V.17**: ML Kit smile probability may fire [smileStillCaptureListener]. */
    fun setSmileStillEnabled(enabled: Boolean) {
        if (smileStillEnabled == enabled) return
        smileStillEnabled = enabled
        if (!enabled) {
            SmileStillCapturePolicy.resetCooldown()
        } else {
            lastSmileProcessWallMs = 0L
            lastSmileDiagLogWallMs = 0L
        }
        Log.i(
            "PNS.SmileStill",
            "smileStillEnabled=$enabled photoOnly=true yuvOnNextSession=$enabled " +
                "(scans YUV while photo mode; toggle off in Eye AF menu)",
        )
        maybeRestart()
    }

    fun setSmileStillCaptureListener(listener: (() -> Unit)?) {
        smileStillCaptureListener = listener
    }

    /** BUILD_PLAN §4 Eye-AF: enable face detect + publish [EyeMark]s when HUD toggle is on. */
    fun setHudFaceOverlayEnabled(enabled: Boolean) {
        if (hudFaceOverlayEnabled == enabled) return
        hudFaceOverlayEnabled = enabled
        Log.d(tag, "setHudFaceOverlayEnabled enabled=$enabled")
        if (!enabled) {
            faceTracker.reset()
            lastTrackerLockedLogged = null
            clearFaceHudOverlayState()
        }
        // YUV analysis surface is required for ML Kit fallback when OEM stats faces are empty.
        maybeRestart()
    }

    fun setFaceHudOverlayListener(listener: ((FaceHudOverlayState) -> Unit)?) {
        faceHudOverlayListener = listener
    }

    /**
     * Negotiated Camera2 preview stream size ([desiredSurfaceSize]), falling back to the current
     * preview buffer. UI center-crop mapping must match [LutCameraPreviewRenderer] geometry
     * (same math as [TexturePreviewFit]), driven by [desiredSurfaceSize] — using only
     * [currentSurfaceSize] caused horizontal stretch when they
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

    fun readoutAeCoupling(): ReadoutAeCoupling =
        ReadoutAeCoupling.fromOverrides(manualIsoOverride, manualExposureNsOverride)

    fun readoutMenuSnapshot(): ReadoutMenuSnapshot {
        val camId = selectedCameraId ?: return ReadoutMenuSnapshot.EMPTY
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            ?: return ReadoutMenuSnapshot.EMPTY
        return ReadoutMenuSnapshot(
            isoChoices = ReadoutExposureCatalog.isoChoices(chars, readoutIsoBand),
            exposureChoices = ReadoutExposureCatalog.exposureChoices(chars),
            awbChoices = ReadoutExposureCatalog.awbChoices(chars),
            isoBand = readoutIsoBand,
        )
    }

    fun setReadoutIsoBand(band: ReadoutIsoBand) {
        if (readoutIsoBand == band) return
        readoutIsoBand = band
        refreshRepeatingPreviewOnly()
    }

    fun cycleReadoutIsoBand() {
        val bands = ReadoutIsoBand.entries
        val idx = bands.indexOf(readoutIsoBand).coerceAtLeast(0)
        val next = bands[(idx + 1) % bands.size]
        setReadoutIsoBand(next)
        Log.i(tag, "readoutIsoBand=${next.menuLabel}")
    }

    /** Sprint **15.11** — lock shutter from angle preset; ISO stays auto when only SS is set. */
    fun applyVideoShutterAnglePreset(angle: VideoShutterAngle, fps: Int) {
        val ns = angle.exposureNsForFps(fps)
        if (ns == null) {
            if (manualExposureNsOverride != null) setReadoutManualShutter(null)
            return
        }
        setReadoutManualShutter(ns)
        Log.i(tag, "readoutManual videoShutterAngle=${angle.name} ssNs=$ns fps=$fps")
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
        if (iso != null && manualExposureNsOverride == null) {
            readoutChaseExposureNs =
                previewMetadata.get().exposureNs ?: readoutChaseExposureNs
        }
        if (iso == null && manualExposureNsOverride == null) {
            readoutChaseExposureNs = null
            readoutChaseIso = null
            readoutChaseMedianEma = Double.NaN
        }
        refreshRepeatingPreviewOnly()
    }

    /** Adjust shutter only; keeps the current manual ISO selection (or auto). */
    fun setReadoutManualShutter(exposureNs: Long?) {
        manualExposureNsOverride = exposureNs
        if (exposureNs != null && manualIsoOverride == null) {
            readoutChaseIso = previewMetadata.get().iso ?: readoutChaseIso
        }
        if (manualIsoOverride == null && manualExposureNsOverride == null) {
            readoutChaseExposureNs = null
            readoutChaseIso = null
            readoutChaseMedianEma = Double.NaN
        }
        refreshRepeatingPreviewOnly()
    }

    fun setReadoutManualAwbMode(mode: Int?) {
        if (manualAwbModeOverride == mode) return
        manualAwbModeOverride = mode
        previewShaderWbRgb.set(ReadoutAwbPreviewShaderGains.rgbForMode(mode))
        notifyReadoutWbShaderChanged()
        refreshRepeatingPreviewOnly()
    }

    /**
     * Samples the latest YUV analysis frame (center ROI) and locks preview WB via shader gains
     * plus [CaptureRequest.CONTROL_AWB_MODE_OFF]. Requires an active YUV reader (face HUD,
     * histogram, zebra, or highlight metering).
     */
    fun applyGrayCardWhiteBalance(onResult: (String?) -> Unit) {
        val h = handler
        if (h == null) {
            mainHandler.post { onResult("Camera thread not ready") }
            return
        }
        h.post {
            val ir = yuvImageReader
            if (ir == null) {
                mainHandler.post {
                    onResult(
                        "Enable YUV analysis: Face overlay, Histogram, Zebra, or Highlight (H) metering.",
                    )
                }
                return@post
            }
            val img = ir.acquireLatestImage()
            if (img == null) {
                mainHandler.post { onResult("No YUV frame yet — wait for live preview.") }
                return@post
            }
            try {
                val gains = GrayCardWhiteBalance.estimateRgbGainsFromYuv420888OrNull(img)
                if (gains == null) {
                    mainHandler.post { onResult("Could not sample chroma from this frame.") }
                    return@post
                }
                manualAwbModeOverride = CaptureRequest.CONTROL_AWB_MODE_OFF
                previewShaderWbRgb.set(gains)
                notifyReadoutWbShaderChanged()
                refreshRepeatingPreviewOnly()
                mainHandler.post { onResult(null) }
            } finally {
                runCatching { img.close() }
            }
        }
    }

    /** Latest readout-WB RGB multipliers for the external-OES preview shader (GL thread safe). */
    fun previewShaderWbRgbForGl(): FloatArray = previewShaderWbRgb.get()

    /** Sprint 15.0: apply saved chart profile to live preview + manual shutter nudge. */
    fun applyChartCalibrationProfile(profile: CalibrationProfile, exposureStops: Double) {
        manualAwbModeOverride = CaptureRequest.CONTROL_AWB_MODE_OFF
        previewShaderWbRgb.set(CalibrationWorkflow.previewShaderWbFromProfile(profile))
        notifyReadoutWbShaderChanged()
        if (exposureStops != 0.0) {
            val base =
                manualExposureNsOverride
                    ?: previewMetadata.get().exposureNs
            if (base != null && base > 0L) {
                val scaled =
                    (base.toDouble() * 2.0.pow(exposureStops))
                        .toLong()
                        .coerceIn(1_000L, 30_000_000_000L)
                setReadoutManualShutter(scaled)
            }
        }
        refreshRepeatingPreviewOnly()
    }

    fun setReadoutWbShaderChangedListener(listener: Runnable?) {
        readoutWbShaderChangedListener = listener
    }

    private fun notifyReadoutWbShaderChanged() {
        val r = readoutWbShaderChangedListener ?: return
        mainHandler.post(r)
    }

    fun setStillEmbedLocationInFiles(enabled: Boolean) {
        stillEmbedLocationInFiles = enabled
    }

    private fun locationForStillMetadata(): Location? =
        if (stillEmbedLocationInFiles) CaptureLocationBridge.snapshot() else null

    /** RAW DNG path requires non-HFR session with [ImageReader] attached (BUILD_PLAN §4). */
    fun canCaptureRawStill(): Boolean {
        val selected = selectedCameraId?.takeIf { it.isNotBlank() } ?: return false
        return imagingProfileForStreams !is ImagingProfile.JpegOnly &&
            rawImageReader != null &&
            session != null &&
            device != null &&
            device?.id == selected &&
            desiredFps < 120 &&
            sessionCommittedGeneration == generation &&
            !captureSessionAsyncConfigurePending &&
            !cameraDeviceOpenPending
    }

    /** Independent tonal still (hardware JPEG → JXL/AVIF/JPEG file). */
    fun canCaptureIndependentTonalStill(): Boolean {
        val selected = selectedCameraId?.takeIf { it.isNotBlank() } ?: return false
        return wantsIndependentTonalStill &&
            jpegImageReader != null &&
            session != null &&
            device != null &&
            device?.id == selected &&
            desiredFps < 120 &&
            sessionCommittedGeneration == generation &&
            !captureSessionAsyncConfigurePending &&
            !cameraDeviceOpenPending
    }

    /** Hardware JPEG-only still ([ImagingProfile.JpegOnly]); no RAW surface. */
    fun canCaptureJpegHardwareStill(): Boolean =
        imagingProfileForStreams is ImagingProfile.JpegOnly && canCaptureIndependentTonalStill()

    /** Single still: RAW DNG and/or independent tonal. */
    fun canCaptureStill(): Boolean = canCaptureRawStill() || canCaptureIndependentTonalStill()

    /**
     * User-visible blocker when [plan] cannot run on the current session (IMG tiers vs attached surfaces).
     * Call after [setComposedCapturePlan].
     */
    fun composedCaptureBlockedReason(plan: ComposedCapturePlan): String? {
        if (captureBusy.get()) {
            return "Capture already in progress — wait a moment."
        }
        if (selectedCameraId.isNullOrBlank()) {
            return "No camera selected."
        }
        if (device == null || session == null) {
            return "Camera not ready — wait for preview to start."
        }
        if (desiredFps >= 120) {
            return "Still capture needs preview at 119 fps or below (not 120 fps HFR)."
        }
        if (sessionCommittedGeneration != generation) {
            return "Camera session is updating — wait a moment and try again."
        }
        if (plan.raw != null) {
            if (imagingProfileForStreams is ImagingProfile.JpegOnly || rawImageReader == null) {
                return "RAW still session not ready — wait a moment and try again."
            }
        }
        if (plan.tonal != null && jpegImageReader == null) {
            return "JPEG still session not ready — wait a moment and try again."
        }
        if (plan.raw == null && plan.tonal == null) {
            return "Enable RAW and/or JPEG in the IMG menu."
        }
        return null
    }

    fun isAfShutterGateActive(): Boolean = afShutterGateActive

    fun setAfShutterGateUiListener(listener: ((Boolean) -> Unit)?) {
        afShutterGateUiListener.set(listener)
        listener?.invoke(afShutterGateActive)
    }

    private fun notifyAfShutterGateUi(active: Boolean) {
        afShutterGateActive = active
        afShutterGateUiListener.get()?.invoke(active)
    }

    private fun isPreviewAfReadyForShutterGate(): Boolean {
        val af = lastPreviewControlAfState ?: return false
        return af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
            af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
    }

    /**
     * When HUD **waitForAfFocusBeforeStill** is on, runs [runCaptureOnMain] after AF precapture
     * converges (or timeout). Intended for main-thread callers from preview chrome; poll runs on [handler].
     */
    fun runAfterAfShutterGateIfNeeded(
        onTimeoutOnMain: () -> Unit,
        runCaptureOnMain: () -> Unit,
    ) {
        fun dispatchCapture() {
            if (Looper.myLooper() == mainHandler.looper) {
                runCaptureOnMain()
            } else {
                mainHandler.post { runCaptureOnMain() }
            }
        }

        val prefs = readHudCapturePrefs()
        val manualSensor = manualIsoOverride != null || manualExposureNsOverride != null
        val camId = selectedCameraId
        val chars =
            if (!camId.isNullOrBlank()) {
                runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            } else {
                null
            }
        val flashSkipsAf =
            chars != null &&
                PreviewFlashPolicy.stillFlashSkipsAfFreeze(
                    previewFlashMode,
                    manualSensor,
                    commandDialMode,
                    chars,
                )
        val wantGate =
            prefs.waitForAfFocusBeforeStill &&
                !adbScriptedStillAutomationActive.get() &&
                !manualSensor &&
                commandDialMode != CommandDialMode.S &&
                commandDialMode != CommandDialMode.BKT &&
                !flashSkipsAf &&
                canCaptureStill()
        if (!wantGate) {
            dispatchCapture()
            return
        }
        if (isPreviewAfReadyForShutterGate()) {
            dispatchCapture()
            return
        }

        val sess = session
        val h = handler
        val constrained =
            sess != null &&
                runCatching { sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true) }
                    .getOrDefault(false)
        if (constrained || sess == null || h == null) {
            Log.i(
                "PNS.AfShutterGate",
                "skip constrained=$constrained noSession=${sess == null} noHandler=${h == null}",
            )
            dispatchCapture()
            return
        }

        notifyAfShutterGateUi(true)
        mainHandler.post { lastStatus = "Focusing…" }
        Log.i(
            "PNS.AfShutterGate",
            "begin precapture lastAf=$lastPreviewControlAfState",
        )
        fireTapFocusAfAeTriggers()

        val deadline = SystemClock.elapsedRealtime() + AF_SHUTTER_GATE_TIMEOUT_MS
        val poll =
            object : Runnable {
                override fun run() {
                    if (session == null || device == null) {
                        Log.w("PNS.AfShutterGate", "abort sessionGone lastAf=$lastPreviewControlAfState")
                        notifyAfShutterGateUi(false)
                        mainHandler.post { lastStatus = "Preview ready" }
                        return
                    }
                    if (isPreviewAfReadyForShutterGate()) {
                        Log.i("PNS.AfShutterGate", "ready af=$lastPreviewControlAfState")
                        notifyAfShutterGateUi(false)
                        dispatchCapture()
                        return
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        Log.w("PNS.AfShutterGate", "timeout lastAf=$lastPreviewControlAfState")
                        notifyAfShutterGateUi(false)
                        mainHandler.post {
                            lastStatus = "Preview ready"
                            onTimeoutOnMain()
                        }
                        return
                    }
                    h.postDelayed(this, AF_SHUTTER_GATE_POLL_MS)
                }
            }
        h.postDelayed(poll, AF_SHUTTER_GATE_POLL_MS)
    }

    /**
     * When still capture is not ready, explains why (ADB automation / logcat).
     * For [ImagingProfile.JpegOnly] uses the hardware JPEG reader path; otherwise RAW.
     */
    fun rawStillNotReadyReason(): String? {
        if (imagingProfileForStreams is ImagingProfile.JpegOnly) {
            if (canCaptureJpegHardwareStill()) return null
            val parts = mutableListOf<String>()
            if (jpegImageReader == null) parts += "no JPEG ImageReader"
            if (session == null) parts += "no capture session"
            if (device == null) parts += "no CameraDevice"
            if (desiredFps >= 120) parts += "HFR path active (desiredFps=$desiredFps)"
            if (selectedCameraId.isNullOrBlank()) parts += "no cameraId"
            if (device != null && device?.id != selectedCameraId) {
                parts += "device id mismatch (open=${device?.id} selected=$selectedCameraId)"
            }
            if (session != null && sessionCommittedGeneration != generation) {
                parts += "session not committed (committedGen=$sessionCommittedGeneration currentGen=$generation)"
            }
            if (captureSessionAsyncConfigurePending) parts += "session configure pending"
            if (cameraDeviceOpenPending) parts += "camera open pending"
            return parts.joinToString("; ").ifBlank { "unknown blocker" }
        }
        if (canCaptureRawStill()) return null
        val parts = mutableListOf<String>()
        if (rawImageReader == null) parts += "no RAW ImageReader"
        if (session == null) parts += "no capture session"
        if (device == null) parts += "no CameraDevice"
        if (desiredFps >= 120) parts += "HFR path active (desiredFps=$desiredFps)"
        if (selectedCameraId.isNullOrBlank()) parts += "no cameraId"
        if (device != null && device?.id != selectedCameraId) {
            parts += "device id mismatch (open=${device?.id} selected=$selectedCameraId)"
        }
        if (session != null && sessionCommittedGeneration != generation) {
            parts += "session not committed (committedGen=$sessionCommittedGeneration currentGen=$generation)"
        }
        if (captureSessionAsyncConfigurePending) parts += "session configure pending"
        if (cameraDeviceOpenPending) parts += "camera open pending"
        return parts.joinToString("; ").ifBlank { "unknown blocker" }
    }

    private fun previewTargetFpsForSession(): Int {
        if (videoController.isRecorderPresent() && !videoController.wantsMediaCodecPath) {
            return minOf(desiredFps, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
        }
        return desiredFps
    }

    /** ADB intent / controller field — must win over [PreviewChromePreferences] session snapshot. */
    private fun effectiveAdbVideoCodecOrdinal(): Int? {
        adbAutomationVideoCodecOrdinal?.let { return it }
        val activity = appContext.findHostActivity() ?: return null
        val ord = activity.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL, -1) ?: -1
        return ord.takeIf { it >= 0 }
    }

    /**
     * Pick in-app video encoder format (Sprint **13.4** DCG session + HDR10 encode when requested).
     */
    fun resolveInAppVideoFormat(size: android.util.Size, targetFps: Int): VideoFormat {
        val camId = selectedCameraId
        val fps =
            if (targetFps >= 120) {
                targetFps.coerceIn(15, 480)
            } else {
                targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
            }
        val chars =
            camId?.let { runCatching { cm.getCameraCharacteristics(it) }.getOrNull() }
        val supportsDcg = chars?.let { DcgModeSupport.supportsDcgMode(it) } ?: false
        val prefs = readHudCapturePrefs()
        val wantDcg =
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = prefs.enableResearchDcgHDR,
                adbPreviewVideoDcg = adbAutomationVideoDcg,
            )
        val chromeLoaded = PreviewChromePreferences.load(appContext)
        val adbCodecOrd = effectiveAdbVideoCodecOrdinal()
        val chrome =
            adbCodecOrd?.let { ord ->
                chromeLoaded.copy(inAppVideoCodecOrdinal = ord)
            } ?: chromeLoaded
        val supportsAv1 = MediaCodecCapabilityProbe.supportsAv1Encoder()
        val hsMap =
            chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val picked =
            InAppVideoFormatSelection.pickForRecording(
                recordSize = size,
                targetFps = fps,
                supportsDcg = supportsDcg,
                wantDcg = wantDcg,
                adbAutomationVideoTenBit = adbAutomationVideoTenBit,
                chrome = chrome,
                supportsAv1 = supportsAv1,
                adbForceAv1 = adbAutomationVideoAv1,
                highSpeedMap = hsMap,
            )
        Log.i(
            tag,
            "inAppVideoFormat label=${picked.getLabel()} codec=${picked.codec} dcg=${picked.isDcg} " +
                "tenBit=${picked.isTenBit} fps=${picked.frameRate} av1Probe=$supportsAv1 wantDcg=$wantDcg",
        )
        if (wantDcg && picked.isDcg) {
            PnsAdbLog.i(appContext, "inAppVideoFormat=DCG fps=${picked.frameRate}")
        }
        if (picked.codec == VideoCodec.AV1) {
            PnsAdbLog.i(appContext, "inAppVideoFormat=AV1 fps=${picked.frameRate}")
        }
        return picked
    }

    /**
     * Apply video recording shell with HFR support (Sprint 12.2/12.4).
     * Delegates to [videoController] with two-phase flow:
     * 1. Prepare recorder (get surface)
     * 2. Rebuild session with recording surface
     * 3. Start recorder after session settled
     *
     * When wantHighSpeed=true and device supports it, enables HFR recording path.
     */
    fun applyInAppVideoRecordingShell(
        wantRecord: Boolean,
        profile: ImagingProfile,
        onUi: (InAppVideoRecordingUiEvent) -> Unit,
        wantHighSpeed: Boolean = false,
    ) {
        cancelDeferredMcStopPreviewRebuild()
        // Map UI events from VideoRecordingController to PreviewController events
        val onEvent: (VideoRecordingController.Event) -> Unit = { event ->
            when (event) {
                is VideoRecordingController.Event.StartFailed -> {
                    inAppVideoRecordingArmed = false
                    cancelDeferredMcStopPreviewRebuild()
                    mainHandler.post { onUi(InAppVideoRecordingUiEvent.StartFailed) }
                }
                is VideoRecordingController.Event.Stopped -> {
                    finishDeferredMcStopPreviewRebuildIfNeeded()
                    mainHandler.post { onUi(InAppVideoRecordingUiEvent.Stopped(event.uri, event.audioEnabled)) }
                }
            }
        }

        // Must run on handler thread for synchronous result
        val h = handler
        if (h == null) {
            if (wantRecord) {
                mainHandler.post { onUi(InAppVideoRecordingUiEvent.StartFailed) }
            }
            return
        }

        // Check if device supports high-speed video for the current camera
        val supportsHighSpeed = runCatching {
            val cameraId = selectedCameraId ?: return@runCatching false
            val chars = cm.getCameraCharacteristics(cameraId)
            val map: StreamConfigurationMap? = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            InAppVideoRecordingSupport.supportsHighSpeedVideoRecording(map)
        }.getOrDefault(false)

        h.post {
            val stopNeedsPreviewSessionRebuild =
                !wantRecord &&
                    (
                        videoController.isRecorderPresent() ||
                            videoController.isRecorderStarted() ||
                            inAppVideoRecordingArmed
                        )
            if (!wantRecord) {
                inAppVideoRecordingArmed = false
                deferMcStartUntilPreviewFrame = false
            }
            val recordSize =
                if (dualVideoActive) {
                    DualVideoRecordingController.compositeRecordSize()
                } else {
                    resolveInAppVideoRecordSize()
                }
            val sessionSize = desiredSurfaceSize ?: currentSurfaceSize
            Log.i(
                "PNS.VideoEncode",
                "videoRecordShell dual=$dualVideoActive session=${sessionSize?.width ?: 0}x${sessionSize?.height ?: 0} " +
                    "encodePref=${inAppVideoEncodeSizePref?.width ?: 0}x${inAppVideoEncodeSizePref?.height ?: 0} " +
                    "record=${recordSize.width}x${recordSize.height} fps=$desiredFps " +
                    "sessionMatchesRecord=${sessionSize?.width == recordSize.width && sessionSize?.height == recordSize.height}",
            )
            val videoFormat = resolveInAppVideoFormat(recordSize, desiredFps)
            val orientHint =
                if (dualVideoActive) {
                    DualVideoRecordingController.COMPOSITE_ORIENTATION_HINT_DEGREES
                } else {
                    selectedCameraId?.let { videoOrientationHintDegrees(it) } ?: 0
                }
            val result = videoController.applyShell(
                wantRecord = wantRecord,
                profile = profile,
                desiredFps = desiredFps,
                size = recordSize,
                orientationHintDegrees = orientHint,
                wantHighSpeed = wantHighSpeed && !dualVideoActive,
                supportsHighSpeed = supportsHighSpeed,
                videoFormat = videoFormat,
                onEvent = onEvent,
                forceMediaCodecGlComposite = dualVideoActive,
            )

            when (result) {
                is VideoRecordingController.PrepareResult.Ready -> {
                    if (wantRecord) {
                        inAppVideoRecordingArmed = true
                        Log.i("PNS.Cam", "Video prepared, marking session rebuild needed (armed)")
                    }
                    videoRecordingSessionRebuildPending = true
                    maybeRestartBody()
                }
                is VideoRecordingController.PrepareResult.Rejected -> {
                    inAppVideoRecordingArmed = false
                }
                VideoRecordingController.PrepareResult.NoAction -> Unit
            }
            if (stopNeedsPreviewSessionRebuild) {
                stopHfrRecordMonitor()
                videoRecordingSessionRebuildPending = false
                val deferRebuild =
                    desiredFps >= VideoRecordingController.HFR_THRESHOLD_FPS &&
                        videoController.wantsMediaCodecPath
                if (deferRebuild) {
                    deferPreviewRebuildUntilMcStopped = true
                    scheduleDeferredMcStopPreviewRebuildSafety()
                    Log.i(
                        "PNS.Cam",
                        "Video stopped — defer preview rebuild until MediaCodec muxer finalizes",
                    )
                } else {
                    Log.i("PNS.Cam", "Video stopped — rebuilding preview-only session")
                    maybeRestartBody()
                }
            }
        }
    }

    private fun finishDeferredMcStopPreviewRebuildIfNeeded() {
        if (!deferPreviewRebuildUntilMcStopped) return
        deferPreviewRebuildUntilMcStopped = false
        cancelDeferredMcStopPreviewRebuild()
        Log.i("PNS.Cam", "MediaCodec finalized — rebuilding preview-only session")
        handler?.post { maybeRestartBody() } ?: maybeRestartBody()
    }

    private fun cancelDeferredMcStopPreviewRebuild() {
        deferredMcStopRebuildRunnable?.let { mainHandler.removeCallbacks(it) }
        deferredMcStopRebuildRunnable = null
        deferPreviewRebuildUntilMcStopped = false
    }

    private fun scheduleDeferredMcStopPreviewRebuildSafety() {
        deferredMcStopRebuildRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable =
            Runnable {
                if (!deferPreviewRebuildUntilMcStopped) return@Runnable
                Log.w(
                    "PNS.Cam",
                    "deferred HFR preview rebuild timeout — forcing session rebuild",
                )
                finishDeferredMcStopPreviewRebuildIfNeeded()
            }
        deferredMcStopRebuildRunnable = runnable
        mainHandler.postDelayed(runnable, 12_000L)
    }

    /** Get recording surface for session configuration. */
    fun getInAppVideoRecordingSurface(): Surface? = videoController.getRecordingSurface()

    /** Start recorder after preview settles. */
    fun maybeStartInAppVideoRecorder() = videoController.maybeStartRecorder()

    /** Tear down recording for camera close. */
    private fun tearDownInAppVideoRecordingForCloseCamera() {
        videoController.tearDownForCloseCamera()
    }

    private fun capturePipelineBaseContext(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        m["gen"] = generation.toString()
        m["sessCommitGen"] = sessionCommittedGeneration.toString()
        m["fps"] = desiredFps.toString()
        m["dial"] = commandDialMode.name
        m["cam"] = selectedCameraId ?: "-"
        m["rawIr"] = (rawImageReader != null).toString()
        m["yuvIr"] = (yuvImageReader != null).toString()
        m["sess"] = (session != null).toString()
        m["dev"] = (device != null).toString()
        m["cfgPending"] = captureSessionAsyncConfigurePending.toString()
        m["openPending"] = cameraDeviceOpenPending.toString()
        return m
    }

    private fun recordCapturePipelineEvent(
        kind: String,
        message: String,
        extra: Map<String, String> = emptyMap(),
        flushToFile: Boolean = false,
    ) {
        val ctx = capturePipelineBaseContext()
        ctx.putAll(extra)
        PnsCapturePipelineDiagnostics.record(kind, message, ctx)
        if (flushToFile) {
            PnsCapturePipelineDiagnostics.flushToFilesDir(appContext)
        }
    }

    private fun cameraDeviceErrorLabel(code: Int): String =
        when (code) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
            else -> "ERROR_UNKNOWN_$code"
        }

    /**
     * BKT dial: bracket when at least one still output path is ready (RAW DNG, hardware JPEG, or both).
     * Follows IMG tiers + session surfaces — not forced RAW-only.
     */
    fun canCaptureBracketBurst(): Boolean =
        commandDialMode == CommandDialMode.BKT && (canCaptureRawStill() || canCaptureJpegHardwareStill())

    private fun jpegImageToByteArray(image: Image): ByteArray {
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }

    private fun rotateBitmapForJpegCompanion(src: Bitmap, orientationDegrees: Int): Bitmap {
        val deg = ((orientationDegrees % 360) + 360) % 360
        if (deg == 0) return src
        val m =
            Matrix().apply {
                postRotate(deg.toFloat())
            }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Decode hardware JPEG, rotate by [orientationDegrees] (same value as [Dng12Saver] /
     * [RawCaptureSupport.orientationClockwiseDegForDng]) so the sRGB companion matches the DNG
     * upright framing at any device hold, optionally apply [StillRgbLut], re-compress, MediaStore,
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
        orientationDegrees: Int,
        filenameSuffix: String? = null,
    ): Uri? {
        val jpegBytes = jpegImageToByteArray(jpegImage)
        val decoded =
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: run {
                    Log.w(tag, "companion JPEG decode failed")
                    return null
                }
        val oriented = rotateBitmapForJpegCompanion(decoded, orientationDegrees)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        val w = oriented.width
        val h = oriented.height
        if (w <= 0 || h <= 0) {
            oriented.recycle()
            return null
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
        return try {
            handle =
                CaptureStorage.openOutput(
                    appContext.applicationContext,
                    profile,
                    CaptureStorage.CaptureKind.JpegSdr,
                    useLocationBridge = false,
                    filenameSuffix = filenameSuffix,
                )
            val jpegQuality = readHudCapturePrefs().softwareJpegCompanionQuality.coerceIn(70, 100)
            if (!outBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, handle.output)) {
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
            jpegUri
        } catch (t: Throwable) {
            Log.w(tag, "companion JPEG save failed", t)
            runCatching { handle?.discard() }
            null
        } finally {
            outBmp.recycle()
        }
    }

    /**
     * IMG matrix entry: independent RAW DNG and/or tonal still (see [captureComposedStill]).
     */
    fun captureComposedStill(
        appContext: Context,
        plan: ComposedCapturePlan,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        adbValidationShotLabel: String? = null,
        onTonalReady: ((Uri?) -> Unit)? = null,
        onResult: (Result<RawStillSaveSuccess>) -> Unit,
    ) {
        setComposedCapturePlan(plan)
        composedCaptureBlockedReason(plan)?.let { blocked ->
            mainHandler.post { onResult(Result.failure(IllegalStateException(blocked))) }
            return
        }
        onStillShutterFired(haptics)
        val raw = plan.raw
        val tonal = plan.tonal
        when {
            raw == null && tonal != null ->
                captureIndependentTonalStill(
                    appContext,
                    haptics,
                    surfaceRotation,
                    tonalBundle = tonal,
                    stillsLut = stillsLut,
                    adbValidationShotLabel = adbValidationShotLabel,
                    onTonalReady = onTonalReady,
                    onResult = onResult,
                )
            raw != null && tonal == null ->
                captureRawStill(
                    appContext,
                    haptics,
                    surfaceRotation,
                    dngSoftwareDescription,
                    stillsLut,
                    adbValidationShotLabel,
                    onResult = onResult,
                )
            raw != null && tonal != null ->
                captureRawStill(
                    appContext,
                    haptics,
                    surfaceRotation,
                    dngSoftwareDescription,
                    stillsLut,
                    adbValidationShotLabel,
                    deferReadoutHapticUntilTonal = true,
                ) { rawResult ->
                    rawResult.fold(
                        onSuccess = { rawOut ->
                            captureIndependentTonalStill(
                                appContext,
                                haptics,
                                surfaceRotation,
                                tonalBundle = tonal,
                                stillsLut = stillsLut,
                                adbValidationShotLabel = adbValidationShotLabel,
                                onTonalReady = onTonalReady,
                            ) { tonalResult ->
                                tonalResult.fold(
                                    onSuccess = { tonalOut ->
                                        onResult(
                                            Result.success(
                                                RawStillSaveSuccess(
                                                    dngUriString = rawOut.dngUriString,
                                                    tonalUriString = tonalOut.tonalUriString,
                                                ),
                                            ),
                                        )
                                    },
                                    onFailure = { e ->
                                        Log.w(
                                            tag,
                                            "tonal still after DNG failed: ${e.message}; DNG kept",
                                        )
                                        onResult(Result.success(rawOut))
                                    },
                                )
                            }
                        },
                        onFailure = { onResult(Result.failure(it)) },
                    )
                }
            else ->
                mainHandler.post {
                    onResult(Result.failure(IllegalStateException("IMG menu: enable RAW and/or JPEG tier")))
                }
        }
    }

    /**
     * Sprint **CC.1** — sequential composed stills at [intervalMs] (each shot uses [captureComposedStill]).
     */
    fun captureComposedStillBurst(
        appContext: Context,
        plan: ComposedCapturePlan,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        shotCount: Int,
        intervalMs: Long,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        adbValidationShotLabel: String? = null,
        onTonalReady: ((Uri?) -> Unit)? = null,
        onProgress: ((Int, Int) -> Unit)? = null,
        onResult: (Result<Int>) -> Unit,
    ) {
        val count = shotCount.coerceIn(1, 30)
        val gap = intervalMs.coerceAtLeast(50L)
        val bgHandler = handler
        var saved = 0
        fun shoot(index: Int) {
            val label =
                adbValidationShotLabel?.let { "$it ${index + 1}/$count" } ?: "${index + 1}/$count"
            onProgress?.let { mainHandler.post { it(index + 1, count) } }
            captureComposedStill(
                appContext = appContext,
                plan = plan,
                haptics = haptics,
                surfaceRotation = surfaceRotation,
                dngSoftwareDescription = dngSoftwareDescription,
                stillsLut = stillsLut,
                adbValidationShotLabel = label,
                onTonalReady = if (index == count - 1) onTonalReady else null,
                onResult = { result ->
                    result.fold(
                        onSuccess = {
                            saved++
                            Log.i(
                                CaptureStillLog.TAG,
                                "captureBurst still $label ok=true saved=$saved/$count",
                            )
                            if (adbValidationShotLabel != null) {
                                PnsAdbLog.i(appContext, "captureBurst $label ok=true")
                            }
                            if (index + 1 < count) {
                                bgHandler?.postDelayed({ shoot(index + 1) }, gap)
                                    ?: mainHandler.postDelayed({ shoot(index + 1) }, gap)
                            } else {
                                mainHandler.post { onResult(Result.success(saved)) }
                            }
                        },
                        onFailure = { t ->
                            Log.w(
                                CaptureStillLog.TAG,
                                "captureBurst still $label ok=false err=${t.message}",
                            )
                            if (adbValidationShotLabel != null) {
                                PnsAdbLog.i(
                                    appContext,
                                    "captureBurst $label ok=false err=${t.message}",
                                )
                            }
                            mainHandler.post {
                                onResult(
                                    Result.failure(
                                        IllegalStateException(
                                            "Burst stopped at ${index + 1}/$count: ${t.message}",
                                            t,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                },
            )
        }
        Log.i(CaptureStillLog.TAG, "captureBurst begin count=$count gapMs=$gap")
        if (adbValidationShotLabel != null) {
            PnsAdbLog.i(appContext, "captureBurst begin count=$count gapMs=$gap")
        }
        shoot(0)
    }

    /**
     * Single RAW → DNG via [Dng12Saver] + [CaptureStorage]. Never attaches a JPEG surface to this
     * request — tonal output uses [captureIndependentTonalStill].
     */
    /**
     * Sprint **13.8c** — AE bracket burst of DNGs (no in-app merge); shares [captureBracketBurst] engine.
     */
    fun captureHdrStillBurst(
        appContext: Context,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        adbValidationShotLabel: String? = null,
        onResult: (Result<RawStillSaveSuccess>) -> Unit,
    ) {
        val pattern = dev.pointandshoot.fleet.OnePlus13FleetPolicy.hdrStillBracketPattern()
        val evStep = dev.pointandshoot.fleet.OnePlus13FleetPolicy.hdrStillEvStep()
        val tHdrBeginNs = SystemClock.elapsedRealtimeNanos()
        Log.i(
            CaptureStillLog.TAG,
            "hdr still begin label=${adbValidationShotLabel ?: "-"} pattern=$pattern evStep=$evStep " +
                "stops=${pattern.shotCount}",
        )
        captureBracketBurst(
            appContext = appContext,
            haptics = haptics,
            surfaceRotation = surfaceRotation,
            pattern = pattern,
            dngSoftwareDescription = dngSoftwareDescription,
            stillsLut = stillsLut,
            purpose = BracketBurstPurpose.HdrStill,
            adbValidationShotLabel = adbValidationShotLabel,
        ) { bracketResult ->
            bracketResult.fold(
                onSuccess = { msg ->
                    val uris = msg.lines().filter { it.isNotBlank() }
                    val frameCount = uris.size
                    Log.i(
                        CaptureStillLog.TAG,
                        "hdr still saved frames=$frameCount label=${adbValidationShotLabel ?: "-"}",
                    )
                    StillCaptureTimingLog.logHdrBracketSaved(
                        frameCount = frameCount,
                        tBeginNs = tHdrBeginNs,
                        label = adbValidationShotLabel,
                    )
                    if (adbValidationShotLabel != null) {
                        PnsAdbLog.i(
                            appContext,
                            "captureHdrStill $adbValidationShotLabel ok=true frames=$frameCount",
                        )
                    }
                    val lastUri = uris.lastOrNull() ?: msg
                    onResult(Result.success(RawStillSaveSuccess(dngUriString = lastUri)))
                },
                onFailure = { t ->
                    Log.w(
                        CaptureStillLog.TAG,
                        "hdr still ok=false err=${t.message ?: t::class.java.simpleName} " +
                            "label=${adbValidationShotLabel ?: "-"}",
                        t,
                    )
                    if (adbValidationShotLabel != null) {
                        PnsAdbLog.i(
                            appContext,
                            "captureHdrStill $adbValidationShotLabel ok=false err=${t.message}",
                        )
                    }
                    onResult(Result.failure(t))
                },
            )
        }
    }

    fun captureRawStill(
        appContext: Context,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        /** When set (e.g. `3/10`), logs `PNS.AdbValidation` lines for scripted runs. */
        adbValidationShotLabel: String? = null,
        /** Sprint **15.12** — defer readout haptic until tonal HAL completes (RAW+tonal dual path). */
        deferReadoutHapticUntilTonal: Boolean = false,
        onResult: (Result<RawStillSaveSuccess>) -> Unit,
    ) {
        if (effectiveStillCaptureMode() == StillCaptureMode.HdrStill) {
            onStillShutterFired(haptics)
            captureHdrStillBurst(
                appContext,
                haptics,
                surfaceRotation,
                dngSoftwareDescription,
                stillsLut,
                adbValidationShotLabel,
                onResult,
            )
            return
        }
        onStillShutterFired(haptics)
        val shotTag = adbValidationShotLabel
        if (!captureBusy.compareAndSet(false, true)) {
            Log.w(CaptureStillLog.TAG, "captureRawStill ok=false err=capture_busy label=${shotTag ?: "-"}")
            recordCapturePipelineEvent(
                "RAW_STILL_REJECT",
                "capture_busy",
                mapOf("label" to (shotTag ?: "-")),
            )
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(appContext, "captureRawStill $shotTag ok=false err=capture_busy")
                }
                onResult(Result.failure(IllegalStateException("Capture already in progress")))
            }
            return
        }
        val cam = device
        val sess = session
        val reader = rawImageReader
        val previewSurf = previewSurface
        val camId = selectedCameraId
        if (cam == null || sess == null || reader == null || previewSurf == null || camId.isNullOrBlank()) {
            releaseCaptureBusy()
            Log.w(
                CaptureStillLog.TAG,
                "captureRawStill ok=false err=camera_or_raw_not_ready label=${shotTag ?: "-"} " +
                    "reason=${rawStillNotReadyReason() ?: "ready"}",
            )
            recordCapturePipelineEvent(
                "RAW_STILL_REJECT",
                "camera_or_raw_not_ready",
                mapOf(
                    "label" to (shotTag ?: "-"),
                    "reason" to (rawStillNotReadyReason() ?: "ready"),
                ),
                flushToFile = true,
            )
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(
                        appContext,
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
            releaseCaptureBusy()
            Log.w(CaptureStillLog.TAG, "captureRawStill ok=false err=no_camera_handler label=${shotTag ?: "-"}")
            recordCapturePipelineEvent(
                "RAW_STILL_REJECT",
                "no_camera_handler",
                mapOf("label" to (shotTag ?: "-")),
                flushToFile = true,
            )
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(appContext, "captureRawStill $shotTag ok=false err=no_camera_handler")
                }
                onResult(Result.failure(IllegalStateException("No camera handler")))
            }
            return
        }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        if (chars == null) {
            releaseCaptureBusy()
            Log.w(CaptureStillLog.TAG, "captureRawStill ok=false err=no_characteristics label=${shotTag ?: "-"}")
            recordCapturePipelineEvent(
                "RAW_STILL_REJECT",
                "no_characteristics",
                mapOf("label" to (shotTag ?: "-")),
                flushToFile = true,
            )
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(appContext, "captureRawStill $shotTag ok=false err=no_characteristics")
                }
                onResult(Result.failure(IllegalStateException("No characteristics")))
            }
            return
        }

        val manualSensorStill = manualIsoOverride != null || manualExposureNsOverride != null
        val proShotPureLeafStill =
            OnePlus13FleetPolicy.useProShotPureDngSave() &&
                StillCaptureIqPolicy.isLeafBackCharacteristics(chars)
        val locForStillRequest = locationForStillMetadata()
        val neutralRawStillPipeline =
            !proShotPureLeafStill &&
                RawCaptureSupport.useNeutralColorPipelineForRawStill(
                    cm,
                    cameraIds(),
                    chars,
                    camId,
                    previewSurfacePhysicalCameraId,
                    focalCropMode,
                )
        fun buildRawStillCaptureRequest(
            proShotPreviewResult: TotalCaptureResult?,
            latchProShotManualExposure: Boolean,
            proShotExposureLatch: RawStillProcessingHints.ProShotExposureLatch? = null,
        ): CaptureRequest =
            cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(previewSurf)
                addTarget(reader.surface)
                applyScalerCropAndMetering(
                    this,
                    chars,
                    camId,
                    if (manualSensorStill || proShotPureLeafStill) {
                        null
                    } else {
                        aeHighlightCompensationValue()
                    },
                )
                applyReadoutManualExposureAndWb(this, chars, camId)
                PreviewFlashPolicy.applyStillFlashKeys(
                    this,
                    chars,
                    previewFlashMode,
                    manualSensorStill,
                    commandDialMode,
                    previewFlashStrengthPercent,
                )
                applyStillAfFreezeAndFaceParity(this, chars, previewFlashMode, manualSensorStill)
                PreviewAeAntibanding.applyToRequest(this, chars)
                PreviewStabilization.applyToRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = null,
                    manualSensor = manualSensorStill,
                    isStillCapture = true,
                    disableOisForStill = readHudCapturePrefs().disableOisForStillCapture,
                )
                if (!proShotPureLeafStill) {
                    PreviewPostRawSensitivity.applyIfCompatible(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        manualIsoOverride,
                        manualExposureNsOverride,
                    )
                }
                RawStillProcessingHints.applyLinearRawFriendlyProcessing(this, chars)
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    proShotPreviewResult,
                    latchManualExposureFromPreview = latchProShotManualExposure,
                    exposureLatch = proShotExposureLatch,
                )
                StillCaptureIqPolicy.applyToStillCaptureRequest(
                    this,
                    chars,
                    dev.pointandshoot.fleet.FleetCameraProfiles.profileForCameraId(appContext, camId),
                )
                dev.pointandshoot.fleet.Op13LeafStillColorCorrection.applyToStillCaptureRequest(
                    this,
                    chars,
                    camId,
                    proShotPreviewResult,
                )
                if (
                    commandDialMode == CommandDialMode.H &&
                    !manualSensorStill &&
                    adbValidationShotLabel == null &&
                    !proShotPureLeafStill
                ) {
                    RawStillProcessingHints.applyAeLockIfAvailable(this, chars, lock = true)
                }
                PreviewStillCaptureHints.applyJpegOrientationIfSupported(this, chars, surfaceRotation)
                PreviewStillCaptureHints.applyJpegGpsIfSupported(this, chars, locForStillRequest)
                PreviewStillCaptureHints.applyZslIfCompatible(
                    this,
                    chars,
                    wantZsl = false,
                    manualSensorStill = manualSensorStill,
                )
                if (
                    !DngSaveBisectState.skipJpegProcessingHintsOnRawStill &&
                    !proShotPureLeafStill
                ) {
                    PreviewJpegProcessingHints.applyToCaptureRequest(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        skipColorCorrection =
                            manualAwbAlreadySetsColorCorrection() || neutralRawStillPipeline,
                    )
                }
            }.build()
        // RAW still: include preview surface. Scripted ADB (`shotTag` set) may skip [stopRepeating] so the
        // HAL keeps repeating while delivering RAW on some logical-camera stacks (e.g. CPH2655).
        val still =
            buildRawStillCaptureRequest(
                lastPreviewTotalCaptureResult,
                latchProShotManualExposure = false,
            )

        lastStatus =
            buildString {
                append("Still capture RAW")
                shotTag?.let { append(" ").append(it) }
                append("…")
            }

        val pendingRaw = java.util.concurrent.atomic.AtomicReference<Image?>(null)
        val pendingResult = java.util.concurrent.atomic.AtomicReference<TotalCaptureResult?>(null)
        val processed = AtomicBoolean(false)
        var stillWatchdog: Runnable? = null
        fun cancelStillWatchdog() {
            val w = stillWatchdog ?: return
            bgHandler.removeCallbacks(w)
            stillWatchdog = null
        }

        fun fail(t: Throwable) {
            cancelStillWatchdog()
            if (!processed.compareAndSet(false, true)) return
            reader.setOnImageAvailableListener(null, null)
            runCatching { pendingRaw.getAndSet(null)?.close() }
            pendingResult.set(null)
            bgHandler.post { resumePreviewRepeatingIfPossible() }
            lastStatus = "Still capture failed: ${t.message?.take(48) ?: t::class.java.simpleName}"
            releaseCaptureBusy()
            recordCapturePipelineEvent(
                "RAW_STILL_FAIL",
                t.message ?: t::class.java.simpleName,
                mapOf("label" to (shotTag ?: "-")),
                flushToFile = true,
            )
            Log.w(
                CaptureStillLog.TAG,
                "captureRawStill ok=false err=${t.message ?: t::class.java.simpleName} label=${shotTag ?: "-"}",
                t,
            )
            if (shotTag != null) {
                PnsAdbLog.i(
                    appContext,
                    "captureRawStill $shotTag ok=false err=${t.message}",
                )
            }
            mainHandler.post { onResult(Result.failure(t)) }
        }

        stillWatchdog = Runnable {
            if (processed.get()) return@Runnable
            fail(IllegalStateException("RAW still timed out (HAL did not complete capture)"))
        }

        val boundaryTimings = StillCaptureBoundaryDiag.Timings()
        var previewSnapAtStop: StillCaptureBoundaryDiag.Snapshot? = null
        val tRequestNs = java.util.concurrent.atomic.AtomicLong(0L)
        val requestedMode = requestedStillCaptureMode
        val effectiveMode = effectiveStillCaptureMode()
        if (requestedMode != effectiveMode) {
            Log.w(
                CaptureStillLog.TAG,
                "stillMode=$effectiveMode requestedMode=$requestedMode (not implemented; using Standard path)",
            )
        }

        fun runSave(
            rawImg: Image,
            result: TotalCaptureResult,
            tRawAvailableNs: Long,
            stillRequestForDiag: CaptureRequest?,
            zslFromRing: Boolean,
        ) {
            ioExecutor.execute {
                mainHandler.post { lastStatus = "Saving still (DNG)…" }
                var handle: CaptureStorage.Handle? = null
                try {
                    val dngResolved =
                        DngMetadataResolver.resolveForDngSave(
                            cm,
                            camId,
                            chars,
                            result,
                            previewSurfacePhysicalCameraId,
                            allowPhysicalTotalResultPairing = false,
                        )
                    val (dngChars, dngResult) =
                        DngMetadataResolver.pairForDngCreator(
                            cm,
                            camId,
                            chars,
                            result,
                            previewSurfacePhysicalCameraId,
                            allowPhysicalTotalResultPairing = false,
                        )
                    Log.i(
                        CaptureStillLog.TAG,
                        "dng save diag stillMode=$effectiveMode requestedMode=$requestedMode " +
                            "zslFromRing=$zslFromRing " +
                            "stillBackend=${dev.pointandshoot.fleet.StillDngBackendPolicy.active().name} " +
                            "${dngResolved.toDiagSummary()} " +
                            "iso=${dngResult.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} " +
                            "rawFmt=${rawImg.format} rawWxH=${rawImg.width}x${rawImg.height}",
                    )
                    val orient =
                        RawCaptureSupport.orientationClockwiseDegForDng(dngChars, surfaceRotation)
                    val loc = locationForStillMetadata()
                    handle =
                        CaptureStorage.openOutput(
                            appContext.applicationContext,
                            imagingProfileForStreams,
                            stillCaptureBundle.toDngCaptureKind(),
                            useLocationBridge = false,
                        )
                    val wideCalChars =
                        wideLeafCalibrationCharacteristicsForDngSave(cm, camId)
                    Dng12Saver(dngChars, imagingProfileForStreams).save(
                        rawImg,
                        dngResult,
                        handle.output,
                        orientationDegrees = orient,
                        location = loc,
                        softwareDescription = dngSoftwareDescription,
                        uniqueCameraModel = dngUniqueCameraModelForSave(camId, stillsLut),
                        sessionCameraId = camId,
                        wideCalibrationCharacteristics = wideCalChars,
                        adbValidationContext = appContext,
                    )
                    val rawFormatLabel = StillPostReadoutExtract.rawFormatLabel(rawImg.format)
                    rawImg.close()
                    val uri = handle.uri.toString()
                    val dngDisplayName = handle.displayName
                    val dngUri = handle.uri
                    handle.close()
                    handle = null
                    if (shouldApplyStillMetadataToDng(camId)) {
                        StillCaptureMetadata.applyToDngUri(
                            appContext.applicationContext,
                            dngUri,
                            dngChars,
                            dngResult,
                            location = loc,
                        )
                    } else {
                        Log.i(
                            CaptureStillLog.TAG,
                            "dng skip StillCaptureMetadata.applyToDngUri cam=$camId " +
                                "bisect=${DngSaveBisectState.skipStillMetadataApply}",
                        )
                    }
                    writeCalibrationSidecarIfNeeded(appContext, imagingProfileForStreams, dngDisplayName)
                    val tDngSavedNs = SystemClock.elapsedRealtimeNanos()
                    StillCaptureTimingLog.logDngSaved(
                        stillMode = effectiveMode,
                        requestedMode = requestedMode,
                        tRequestNs = tRequestNs.get(),
                        tRawAvailableNs = tRawAvailableNs,
                        tDngSavedNs = tDngSavedNs,
                        label = shotTag,
                    )
                    if (shotTag != null) {
                        PnsAdbLog.i(
                            appContext,
                            "captureRawStill $shotTag ok=true saved=$dngDisplayName",
                        )
                    }
                    mainHandler.post {
                        publishLastStillPostReadout(
                            StillPostReadoutExtract.from(
                                result,
                                rawFormatLabel,
                                sessionPreviewDynamicRangeShort,
                            ),
                        )
                        lastStatus = "Preview running (normal)"
                        onResult(
                            Result.success(
                                RawStillSaveSuccess(dngUriString = uri),
                            ),
                        )
                        if (wantsZslStillRing()) {
                            attachZslRawRingListener()
                        }
                    }
                    releaseCaptureBusy()
                } catch (t: Throwable) {
                    Log.w(
                        CaptureStillLog.TAG,
                        "captureRawStill save ok=false err=${t.message ?: t::class.java.simpleName} label=${shotTag ?: "-"}",
                        t,
                    )
                    recordCapturePipelineEvent(
                        "RAW_STILL_SAVE_FAIL",
                        t.message ?: t::class.java.simpleName,
                        mapOf("label" to (shotTag ?: "-")),
                        flushToFile = true,
                    )
                    if (shotTag != null) {
                        PnsAdbLog.i(
                            appContext,
                            "captureRawStill $shotTag ok=false err=${t.message}",
                        )
                    }
                    runCatching { rawImg.close() }
                    runCatching { handle?.discard() }
                    mainHandler.post {
                        lastStatus =
                            "Still capture failed: ${t.message?.take(48) ?: t::class.java.simpleName}"
                        onResult(Result.failure(t))
                        if (wantsZslStillRing()) {
                            attachZslRawRingListener()
                        }
                    }
                    releaseCaptureBusy()
                }
            }
        }

        if (effectiveMode == StillCaptureMode.ZslStill) {
            detachZslRawRingListener()
            val zslSlot = zslStillRing?.takeBestForStill()
            if (zslSlot != null) {
                Log.i(
                    CaptureStillLog.TAG,
                    "zsl still ring hit seq=${zslSlot.sequence} ringSize=${zslStillRing?.size() ?: 0}",
                )
                tRequestNs.set(SystemClock.elapsedRealtimeNanos())
                resumePreviewRepeatingIfPossible()
                runSave(
                    rawImg = zslSlot.image,
                    result = zslSlot.result,
                    tRawAvailableNs = SystemClock.elapsedRealtimeNanos(),
                    stillRequestForDiag = null,
                    zslFromRing = true,
                )
                return
            }
            Log.i(
                CaptureStillLog.TAG,
                "zsl still ring miss ringSize=${zslStillRing?.size() ?: 0} " +
                    "complete=${zslStillRing?.completeCount() ?: 0} fallback=standard_capture",
            )
        }

        fun maybeProcess() {
            if (pendingRaw.get() == null) return
            if (pendingResult.get() == null) return
            if (!processed.compareAndSet(false, true)) return
            cancelStillWatchdog()
            reader.setOnImageAvailableListener(null, null)
            // [stopRepeating] ran before capture — restart preview as soon as still buffers are latched.
            resumePreviewRepeatingIfPossible()
            boundaryTimings.tAfterResumeRepeatingNs = SystemClock.elapsedRealtimeNanos()
            val rawImg = pendingRaw.getAndSet(null)!!
            val result = pendingResult.getAndSet(null)!!
            val tRawAvailableNs = SystemClock.elapsedRealtimeNanos()
            if (stillBoundaryDiagEnabled()) {
                StillCaptureBoundaryDiag.logBoundary(
                    kind = "raw_only",
                    label = shotTag,
                    previewAtStop = previewSnapAtStop,
                    stillResult = result,
                    stillRequest = still,
                    timings = boundaryTimings,
                )
            }
            runSave(
                rawImg = rawImg,
                result = result,
                tRawAvailableNs = tRawAvailableNs,
                stillRequestForDiag = still,
                zslFromRing = false,
            )
        }

        reader.setOnImageAvailableListener({ r ->
            if (processed.get()) {
                val dropped = runCatching { r.acquireLatestImage() }.getOrNull()
                if (dropped != null) {
                    runCatching { dropped.close() }
                    Log.w("PNS.Reader", "drop oldest queue=post-process channel=raw-still")
                }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireNextImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            val prev = pendingRaw.getAndSet(img)
            if (prev != null) {
                runCatching { prev.close() }
                Log.w("PNS.Reader", "drop oldest queue=superseded channel=raw-still")
            }
            maybeProcess()
        }, bgHandler)

        val captureRunnable = Runnable {
            try {
                previewSnapAtStop =
                    if (stillBoundaryDiagEnabled()) lastPreviewBoundarySnapshot else null
                val proShotExposureLatchBeforeStop =
                    if (proShotPureLeafStill) {
                        RawStillProcessingHints.snapshotProShotExposure(lastPreviewTotalCaptureResult)
                    } else {
                        null
                    }
                if (proShotExposureLatchBeforeStop != null) {
                    Log.i(
                        "PNS.ProShotStill",
                        "exposure latch before stopRepeating iso=${proShotExposureLatchBeforeStop.iso} " +
                            "expNs=${proShotExposureLatchBeforeStop.expNs}",
                    )
                }
                val skipStopForProShotLeaf =
                    proShotPureLeafStill &&
                        OnePlus13FleetPolicy.proShotLeafStillSkipsStopRepeating(camId)
                if (!skipStopForProShotLeaf) {
                    boundaryTimings.tStopRepeatingNs = SystemClock.elapsedRealtimeNanos()
                    // Without [stopRepeating], several OEM stacks keep repeating preview+YUV+JPEG surfaces busy
                    // and never deliver the RAW ImageReader frame before our post-complete gate (`No RAW buffer`).
                    runCatching { sess.stopRepeating() }
                        .exceptionOrNull()
                        ?.let { Log.w(tag, "captureRawStill stopRepeating: ${it.message}") }
                } else {
                    Log.i("PNS.ProShotStill", "leaf still: skip stopRepeating (ProShot-style)")
                }
                val fireStillCapture =
                    Runnable {
                    try {
                        tRequestNs.set(SystemClock.elapsedRealtimeNanos())
                        boundaryTimings.tFireStillCaptureNs = tRequestNs.get()
                        val stillToCapture =
                            if (proShotPureLeafStill) {
                                buildRawStillCaptureRequest(
                                    lastPreviewTotalCaptureResult,
                                    latchProShotManualExposure = false,
                                )
                            } else {
                                still
                            }
                        sess.capture(
                            stillToCapture,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult,
                                ) {
                                    boundaryTimings.tOnCaptureCompletedNs = SystemClock.elapsedRealtimeNanos()
                                    onStillCaptureReadoutComplete(haptics, deferReadoutHapticUntilTonal)
                                    pendingResult.set(result)
                                    maybeProcess()
                                    val postCompleteWaitMs =
                                        when (stillCaptureBundle.rawMode) {
                                            RawMode.UncompressedRaw12Dng -> RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12
                                            else -> RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT
                                        }
                                    bgHandler.postDelayed({
                                        if (!processed.get() && pendingRaw.get() == null) {
                                            fail(IllegalStateException("No RAW buffer"))
                                        }
                                    }, postCompleteWaitMs)
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
                        val halWatchdogMs =
                            when (stillCaptureBundle.rawMode) {
                                RawMode.UncompressedRaw12Dng ->
                                    RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_RAW12
                                else -> RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_DEFAULT
                            }
                        bgHandler.postDelayed(checkNotNull(stillWatchdog), halWatchdogMs)
                    } catch (t: Throwable) {
                        fail(t)
                    }
                    }
                val runStillAfterOptionalAfSettle =
                    Runnable {
                        val configurePreviewForSettle: CaptureRequest.Builder.() -> Unit = {
                            applyScalerCropAndMetering(
                                this,
                                chars,
                                camId,
                                if (manualSensorStill || proShotPureLeafStill) {
                                    null
                                } else {
                                    aeHighlightCompensationValue()
                                },
                            )
                            applyReadoutManualExposureAndWb(this, chars, camId)
                            PreviewFlashPolicy.applyPreviewFlashHardwareKeys(
                                this,
                                chars,
                                previewFlashMode,
                                commandDialMode,
                                manualSensorStill,
                                previewFlashStrengthPercent,
                            )
                            PreviewAeAntibanding.applyToRequest(this, chars)
                            applyFaceDetectMode(this, chars)
                            if (proShotPureLeafStill) {
                                StillCaptureIqPolicy.applyProShotStillPipeline(this, chars)
                            }
                        }
                        if (
                            proShotPureLeafStill &&
                                OnePlus13FleetPolicy.useProShotStillPrecapture()
                        ) {
                            ProShotStillPrecapture.runAfterStopRepeating(
                                session = sess,
                                camera = cam,
                                previewSurface = previewSurf,
                                chars = chars,
                                configurePreviewLikeStill = configurePreviewForSettle,
                                bgHandler = bgHandler,
                                onComplete = { precaptureResult ->
                                    val fireAfterPrecapture = Runnable {
                                    val proShotLatch =
                                        precaptureResult?.let {
                                            RawStillProcessingHints.snapshotProShotExposure(it)
                                        }?.let { snap ->
                                            OnePlus13FleetPolicy.adjustProShotExposureLatch(
                                                camId,
                                                snap,
                                                chars,
                                            )
                                        }
                                    val stillToCapture =
                                        buildRawStillCaptureRequest(
                                            precaptureResult ?: lastPreviewTotalCaptureResult,
                                            latchProShotManualExposure =
                                                proShotLatch != null &&
                                                    OnePlus13FleetPolicy.proShotLatchManualExposureOnStill(
                                                        camId,
                                                    ),
                                            proShotExposureLatch = proShotLatch,
                                        )
                                    tRequestNs.set(SystemClock.elapsedRealtimeNanos())
                                    boundaryTimings.tFireStillCaptureNs = tRequestNs.get()
                                    sess.capture(
                                        stillToCapture,
                                        object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult,
                                            ) {
                                                boundaryTimings.tOnCaptureCompletedNs =
                                                    SystemClock.elapsedRealtimeNanos()
                                                onStillCaptureReadoutComplete(haptics, deferReadoutHapticUntilTonal)
                                                pendingResult.set(result)
                                                maybeProcess()
                                                val postCompleteWaitMs =
                                                    when (stillCaptureBundle.rawMode) {
                                                        RawMode.UncompressedRaw12Dng ->
                                                            RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12
                                                        else -> RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT
                                                    }
                                                bgHandler.postDelayed({
                                                    if (!processed.get() && pendingRaw.get() == null) {
                                                        fail(IllegalStateException("No RAW buffer"))
                                                    }
                                                }, postCompleteWaitMs)
                                            }

                                            override fun onCaptureFailed(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                failure: CaptureFailure,
                                            ) {
                                                fail(
                                                    RuntimeException(
                                                        "capture failed reason=${failure.reason}",
                                                    ),
                                                )
                                            }
                                        },
                                        bgHandler,
                                    )
                                    val halWatchdogMs =
                                        when (stillCaptureBundle.rawMode) {
                                            RawMode.UncompressedRaw12Dng ->
                                                RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_RAW12
                                            else -> RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_DEFAULT
                                        }
                                    bgHandler.postDelayed(checkNotNull(stillWatchdog), halWatchdogMs)
                                    }
                                    if (PRO_SHOT_STILL_AFTER_PRECAPTURE_DELAY_MS > 0L) {
                                        bgHandler.postDelayed(
                                            fireAfterPrecapture,
                                            PRO_SHOT_STILL_AFTER_PRECAPTURE_DELAY_MS,
                                        )
                                    } else {
                                        fireAfterPrecapture.run()
                                    }
                                },
                            )
                            return@Runnable
                        }
                        val prefs = readHudCapturePrefs()
                        val wantAfSettle =
                            prefs.enableOpenCameraStyleAfSettleBeforeStill &&
                                shotTag == null &&
                                previewFlashMode == PreviewFlashMode.Off &&
                                !manualSensorStill &&
                                !PreviewFlashPolicy.stillFlashSkipsAfFreeze(
                                    previewFlashMode,
                                    manualSensorStill,
                                    commandDialMode,
                                    chars,
                                )
                        if (wantAfSettle) {
                            StillCaptureOpenCameraAfSettle.runIfEnabled(
                                enabled = true,
                                session = sess,
                                camera = cam,
                                previewSurface = previewSurf,
                                chars = chars,
                                configurePreviewLikeStill = configurePreviewForSettle,
                                bgHandler = bgHandler,
                                onComplete = { fireStillCapture.run() },
                            )
                        } else {
                            fireStillCapture.run()
                        }
                    }
                val afterStopDebounceMs =
                    if (skipStopForProShotLeaf) {
                        0L
                    } else if (shotTag != null) {
                        // Scripted ADB: give the HAL longer after stopRepeating before firing still (OEM settle).
                        maxOf(
                            RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS,
                            RAW_STILL_SCRIPTED_MIN_POST_STOP_DEBOUNCE_MS,
                        )
                    } else {
                        RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS
                    }
                if (shotTag != null && afterStopDebounceMs > RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS) {
                    PnsAdbLog.i(appContext, "captureRawStill afterStopRepeatingDebounceMs=$afterStopDebounceMs label=$shotTag")
                }
                if (afterStopDebounceMs > 0L) {
                    bgHandler.postDelayed(runStillAfterOptionalAfSettle, afterStopDebounceMs)
                } else {
                    runStillAfterOptionalAfSettle.run()
                }
            } catch (t: Throwable) {
                fail(t)
            }
        }
        if (Looper.myLooper() == bgHandler.looper) {
            captureRunnable.run()
        } else {
            bgHandler.post(captureRunnable)
        }
    }

    /**
     * Hardware JPEG still only ([ImagingProfile.JpegOnly]) — delegates to [captureIndependentTonalStill].
     */
    fun captureJpegHardwareStill(
        appContext: Context,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        stillsLut: LutCatalog = LutCatalog.None,
        adbValidationShotLabel: String? = null,
        onCompanionJpegReady: ((Uri?) -> Unit)? = null,
        onResult: (Result<RawStillSaveSuccess>) -> Unit,
    ) {
        require(imagingProfileForStreams is ImagingProfile.JpegOnly) {
            "captureJpegHardwareStill requires JPEG-only stream profile"
        }
        val tonalBundle =
            composedCapturePlan.tonal
                ?: run {
                    mainHandler.post {
                        onResult(
                            Result.failure(
                                IllegalStateException("No tonal tier in IMG plan (sync capture plan)"),
                            ),
                        )
                    }
                    return
                }
        captureIndependentTonalStill(
            appContext,
            haptics,
            surfaceRotation,
            tonalBundle = tonalBundle,
            stillsLut = stillsLut,
            adbValidationShotLabel = adbValidationShotLabel,
            onTonalReady = onCompanionJpegReady,
            onResult = onResult,
        )
    }

    /**
     * Separate hardware JPEG still → independent AVIF/JXL (or downgrade JPEG) per IMG **-JPEG-** tier.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun captureIndependentTonalStill(
        appContext: Context,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        tonalBundle: StillCaptureBundle,
        stillsLut: LutCatalog = LutCatalog.None,
        adbValidationShotLabel: String? = null,
        onTonalReady: ((Uri?) -> Unit)? = null,
        onResult: (Result<RawStillSaveSuccess>) -> Unit,
    ) {
        val shotTag = adbValidationShotLabel
        if (!captureBusy.compareAndSet(false, true)) {
            Log.w(CaptureStillLog.TAG, "captureIndependentTonalStill ok=false err=capture_busy label=${shotTag ?: "-"}")
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(appContext, "captureIndependentTonalStill $shotTag ok=false err=capture_busy")
                }
                onResult(Result.failure(IllegalStateException("Capture already in progress")))
            }
            return
        }
        val cam = device
        val sess = session
        val jReader = jpegImageReader
        val previewSurf = previewSurface
        val camId = selectedCameraId
        val jpegPathIncomplete =
            cam == null || sess == null || jReader == null || previewSurf == null
        if (jpegPathIncomplete || camId.isNullOrBlank()) {
            releaseCaptureBusy()
            Log.w(
                CaptureStillLog.TAG,
                "captureIndependentTonalStill ok=false err=camera_or_jpeg_not_ready label=${shotTag ?: "-"} " +
                    "reason=${rawStillNotReadyReason() ?: "ready"}",
            )
            mainHandler.post {
                if (shotTag != null) {
                    PnsAdbLog.i(
                        appContext,
                        "captureIndependentTonalStill $shotTag ok=false err=camera_or_jpeg_not_ready",
                    )
                }
                onResult(
                    Result.failure(
                        IllegalStateException("Camera not ready or tonal JPEG path unavailable (use preview ≤119 fps)"),
                    ),
                )
            }
            return
        }
        val bgHandler = handler
        if (bgHandler == null) {
            releaseCaptureBusy()
            mainHandler.post { onResult(Result.failure(IllegalStateException("No camera handler"))) }
            return
        }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        if (chars == null) {
            releaseCaptureBusy()
            mainHandler.post { onResult(Result.failure(IllegalStateException("No characteristics"))) }
            return
        }
        val manualSensorStill = manualIsoOverride != null || manualExposureNsOverride != null
        val locForStillRequest = locationForStillMetadata()
        val still =
            cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(previewSurf)
                addTarget(jReader.surface)
                applyScalerCropAndMetering(
                    this,
                    chars,
                    camId,
                    if (manualSensorStill) null else aeHighlightCompensationValue(),
                )
                applyReadoutManualExposureAndWb(this, chars, camId)
                PreviewFlashPolicy.applyStillFlashKeys(
                    this,
                    chars,
                    previewFlashMode,
                    manualSensorStill,
                    commandDialMode,
                    previewFlashStrengthPercent,
                )
                applyStillAfFreezeAndFaceParity(this, chars, previewFlashMode, manualSensorStill)
                PreviewAeAntibanding.applyToRequest(this, chars)
                PreviewStabilization.applyToRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = null,
                    manualSensor = manualSensorStill,
                    isStillCapture = true,
                    disableOisForStill = readHudCapturePrefs().disableOisForStillCapture,
                )
                PreviewPostRawSensitivity.applyIfCompatible(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    manualIsoOverride,
                    manualExposureNsOverride,
                )
                RawStillProcessingHints.applyLinearRawFriendlyProcessing(this, chars)
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    lastPreviewTotalCaptureResult,
                )
                StillCaptureIqPolicy.applyToStillCaptureRequest(
                    this,
                    chars,
                    dev.pointandshoot.fleet.FleetCameraProfiles.profileForCameraId(appContext, camId),
                )
                dev.pointandshoot.fleet.Op13LeafStillColorCorrection.applyToStillCaptureRequest(
                    this,
                    chars,
                    camId,
                    lastPreviewTotalCaptureResult,
                )
                if (commandDialMode == CommandDialMode.H && !manualSensorStill && adbValidationShotLabel == null) {
                    RawStillProcessingHints.applyAeLockIfAvailable(this, chars, lock = true)
                }
                PreviewStillCaptureHints.applyJpegOrientationIfSupported(this, chars, surfaceRotation)
                PreviewStillCaptureHints.applyJpegGpsIfSupported(this, chars, locForStillRequest)
                PreviewStillCaptureHints.applyZslIfCompatible(
                    this,
                    chars,
                    wantZsl = false,
                    manualSensorStill = manualSensorStill,
                )
                PreviewJpegProcessingHints.applyToCaptureRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    skipColorCorrection = manualAwbAlreadySetsColorCorrection(),
                )
            }.build()

        lastStatus = buildString {
            append("Still capture JPEG-only")
            shotTag?.let { append(" ").append(it) }
            append("…")
        }

        val pendingJpeg = java.util.concurrent.atomic.AtomicReference<Image?>(null)
        val pendingResult = java.util.concurrent.atomic.AtomicReference<TotalCaptureResult?>(null)
        val processed = AtomicBoolean(false)
        var stillWatchdog: Runnable? = null
        fun cancelStillWatchdog() {
            val w = stillWatchdog ?: return
            bgHandler.removeCallbacks(w)
            stillWatchdog = null
        }

        fun fail(t: Throwable) {
            cancelStillWatchdog()
            if (!processed.compareAndSet(false, true)) return
            jReader.setOnImageAvailableListener(null, null)
            runCatching { pendingJpeg.getAndSet(null)?.close() }
            pendingResult.set(null)
            bgHandler.post { resumePreviewRepeatingIfPossible() }
            lastStatus = "JPEG still capture failed: ${t.message?.take(48) ?: t::class.java.simpleName}"
            releaseCaptureBusy()
            Log.w(
                CaptureStillLog.TAG,
                "captureJpegHardwareStill ok=false err=${t.message ?: t::class.java.simpleName} label=${shotTag ?: "-"}",
                t,
            )
            if (shotTag != null) {
                PnsAdbLog.i(appContext, "captureJpegHardwareStill $shotTag ok=false err=${t.message}")
            }
            mainHandler.post { onResult(Result.failure(t)) }
        }

        stillWatchdog = Runnable {
            if (processed.get()) return@Runnable
            fail(IllegalStateException("JPEG still timed out (HAL did not complete capture)"))
        }

        val boundaryTimings = StillCaptureBoundaryDiag.Timings()
        var previewSnapAtStop: StillCaptureBoundaryDiag.Snapshot? = null

        fun maybeProcess() {
            if (pendingJpeg.get() == null) return
            if (pendingResult.get() == null) return
            if (!processed.compareAndSet(false, true)) return
            cancelStillWatchdog()
            jReader.setOnImageAvailableListener(null, null)
            resumePreviewRepeatingIfPossible()
            boundaryTimings.tAfterResumeRepeatingNs = SystemClock.elapsedRealtimeNanos()
            val jpegImg = pendingJpeg.getAndSet(null)!!
            val result = pendingResult.getAndSet(null)!!
            if (stillBoundaryDiagEnabled()) {
                StillCaptureBoundaryDiag.logBoundary(
                    kind = "jpeg_hardware",
                    label = shotTag,
                    previewAtStop = previewSnapAtStop,
                    stillResult = result,
                    stillRequest = still,
                    timings = boundaryTimings,
                )
            }
            val jpegBytes =
                try {
                    IndependentTonalStillSaver.copyJpegImageToByteArray(jpegImg)
                } catch (t: Throwable) {
                    fail(t)
                    return
                }
            runCatching { jpegImg.close() }
            pendingTonalEncodes.incrementAndGet()
            releaseCaptureBusy()
            mainHandler.post { lastStatus = "Processing tonal still…" }
            val orient = RawCaptureSupport.orientationClockwiseDegForDng(chars, surfaceRotation)
            val storageProfile = storageProfileFromBundle(tonalBundle)
            val softwareJpegQuality = readHudCapturePrefs().softwareJpegCompanionQuality
            companionJpegExecutor.execute {
                try {
                    val outcome =
                        IndependentTonalStillSaver.saveFromHardwareJpegBytes(
                            appContext = appContext,
                            storageProfile = storageProfile,
                            tonalBundle = tonalBundle,
                            jpegBytes = jpegBytes,
                            stillsLut = stillsLut,
                            characteristics = chars,
                            captureResult = result,
                            orientationDegrees = orient,
                            softwareJpegQuality = softwareJpegQuality,
                        )
                    val uri = checkNotNull(outcome.uri) { "Tonal still save failed" }
                    val displayName = outcome.displayName ?: uri.toString()
                    outcome.downgradeMessage?.let { msg ->
                        Log.i(CaptureStillLog.TAG, "tonal still downgrade: $msg saved=$displayName")
                    }
                    if (shotTag != null) {
                        PnsAdbLog.i(
                            appContext,
                            "captureIndependentTonalStill $shotTag ok=true saved=$displayName",
                        )
                    }
                    mainHandler.post {
                        publishLastStillPostReadout(
                            StillPostReadoutExtract.from(
                                result,
                                tonalBundle.tonalContainer.fileExtension.uppercase(),
                                sessionPreviewDynamicRangeShort,
                            ),
                        )
                        lastStatus = "Preview running (normal)"
                        val uriString = uri.toString()
                        onResult(
                            Result.success(
                                RawStillSaveSuccess(
                                    dngUriString = uriString,
                                    tonalUriString = uriString,
                                ),
                            ),
                        )
                        onTonalReady?.invoke(uri)
                    }
                } catch (t: Throwable) {
                    Log.w(CaptureStillLog.TAG, "captureIndependentTonalStill save failed", t)
                    if (shotTag != null) {
                        PnsAdbLog.i(appContext, "captureIndependentTonalStill $shotTag ok=false err=${t.message}")
                    }
                    mainHandler.post {
                        lastStatus =
                            "JPEG still failed: ${t.message?.take(48) ?: t::class.java.simpleName}"
                        onResult(Result.failure(t))
                    }
                } finally {
                    pendingTonalEncodes.decrementAndGet()
                }
            }
        }

        jReader.setOnImageAvailableListener({ r ->
            if (processed.get()) {
                val dropped = runCatching { r.acquireLatestImage() }.getOrNull()
                if (dropped != null) {
                    runCatching { dropped.close() }
                    Log.w("PNS.Reader", "drop oldest queue=post-process channel=jpeg-still-only")
                }
                return@setOnImageAvailableListener
            }
            val img = runCatching { r.acquireNextImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            val prev = pendingJpeg.getAndSet(img)
            if (prev != null) {
                runCatching { prev.close() }
                Log.w("PNS.Reader", "drop oldest queue=superseded channel=jpeg-still-only")
            }
            maybeProcess()
        }, bgHandler)

        val captureRunnable = Runnable {
            try {
                previewSnapAtStop =
                    if (stillBoundaryDiagEnabled()) lastPreviewBoundarySnapshot else null
                boundaryTimings.tStopRepeatingNs = SystemClock.elapsedRealtimeNanos()
                runCatching { sess.stopRepeating() }
                    .exceptionOrNull()
                    ?.let { Log.w(tag, "captureJpegHardwareStill stopRepeating: ${it.message}") }
                val fireStillCapture = Runnable {
                    try {
                        boundaryTimings.tFireStillCaptureNs = SystemClock.elapsedRealtimeNanos()
                        sess.capture(
                            still,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult,
                                ) {
                                    boundaryTimings.tOnCaptureCompletedNs = SystemClock.elapsedRealtimeNanos()
                                    onStillCaptureReadoutComplete(haptics)
                                    pendingResult.set(result)
                                    maybeProcess()
                                    val postCompleteWaitMs = RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT
                                    bgHandler.postDelayed({
                                        if (!processed.get()) {
                                            if (pendingJpeg.get() == null) {
                                                fail(IllegalStateException("No JPEG buffer"))
                                            }
                                        }
                                    }, postCompleteWaitMs)
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
                        val halWatchdogMs = RAW_STILL_HAL_COMPLETION_WATCHDOG_MS_DEFAULT
                        bgHandler.postDelayed(checkNotNull(stillWatchdog), halWatchdogMs)
                    } catch (t: Throwable) {
                        fail(t)
                    }
                }
                val afterStopDebounceMs =
                    if (shotTag != null) {
                        maxOf(
                            RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS,
                            RAW_STILL_SCRIPTED_MIN_POST_STOP_DEBOUNCE_MS,
                        )
                    } else {
                        RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS
                    }
                if (afterStopDebounceMs > 0L) {
                    bgHandler.postDelayed(fireStillCapture, afterStopDebounceMs)
                } else {
                    fireStillCapture.run()
                }
            } catch (t: Throwable) {
                fail(t)
            }
        }
        if (Looper.myLooper() == bgHandler.looper) {
            captureRunnable.run()
        } else {
            bgHandler.post(captureRunnable)
        }
    }

    /**
     * Waits until all work already queued on [ioExecutor] and [companionJpegExecutor] completes.
     * [CAPTURE_ARCHITECTURE.md] bracket rule — bounded wait before BKT sequential captures.
     */
    private fun awaitReaderExecutorDrain(timeoutMs: Long): Boolean {
        val fReader = ioExecutor.submit { }
        val fJpeg = companionJpegExecutor.submit { }
        val slice = (timeoutMs / 2).coerceAtLeast(1L)
        return try {
            fReader.get(slice, TimeUnit.MILLISECONDS)
            fJpeg.get(slice, TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            fReader.cancel(true)
            fJpeg.cancel(true)
            Log.w("PNS.Reader", "encode lane drain timed out after ${timeoutMs}ms")
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            fReader.cancel(true)
            fJpeg.cancel(true)
            false
        } catch (t: Throwable) {
            fReader.cancel(true)
            fJpeg.cancel(true)
            Log.w("PNS.Reader", "encode lane drain failed: ${t.message}")
            false
        }
    }

    /**
     * Best-effort discard of orphaned still images in readers (listeners typically null between shots).
     * Pairs with [awaitReaderExecutorDrain] before a bracket.
     */
    private fun discardQueuedStillImagesBeforeBracket() {
        runCatching {
            rawImageReader?.let { r ->
                while (true) {
                    val img = r.acquireLatestImage() ?: break
                    img.close()
                    Log.w("PNS.Reader", "drop oldest queue=pre-bracket-drain channel=raw")
                }
            }
        }
        runCatching {
            jpegImageReader?.let { r ->
                while (true) {
                    val img = r.acquireLatestImage() ?: break
                    img.close()
                    Log.w("PNS.Reader", "drop oldest queue=pre-bracket-drain channel=jpeg")
                }
            }
        }
    }

    /**
     * Exposure bracket while the dial is **BKT**. Writes per stop from IMG tiers:
     * **RAW** → DNG only; **JPEG** (companion on or JPEG-only RAW Off) → hardware JPEG per stop;
     * **both** → DNG + JPEG with `bktNofM-` suffixes. Uses [captureBusy] (exclusive with single still).
     */
    fun captureBracketBurst(
        appContext: Context,
        haptics: CaptureHaptics,
        surfaceRotation: Int,
        pattern: BracketPattern,
        dngSoftwareDescription: String? = null,
        stillsLut: LutCatalog = LutCatalog.None,
        purpose: BracketBurstPurpose = BracketBurstPurpose.BktDial,
        adbValidationShotLabel: String? = null,
        onResult: (Result<String>) -> Unit,
    ) {
        val finished = AtomicBoolean(false)
        fun finishFailure(t: Throwable) {
            if (!finished.compareAndSet(false, true)) return
            releaseCaptureBusy()
            mainHandler.post { onResult(Result.failure(t)) }
        }
        fun finishSuccess(message: String) {
            if (!finished.compareAndSet(false, true)) return
            releaseCaptureBusy()
            mainHandler.post { onResult(Result.success(message)) }
        }

        if (!captureBusy.compareAndSet(false, true)) {
            mainHandler.post {
                onResult(Result.failure(IllegalStateException("Capture already in progress")))
            }
            return
        }
        if (purpose == BracketBurstPurpose.BktDial && commandDialMode != CommandDialMode.BKT) {
            finishFailure(IllegalStateException("Set command dial to BKT"))
            return
        }

        val cam = device
        val sess = session
        val reader = rawImageReader
        val jReader = jpegImageReader
        val previewSurf = previewSurface
        val camId = selectedCameraId
        val bracketWritesRaw =
            stillCaptureBundle.rawMode != RawMode.None && reader != null
        val bracketWritesTonalInBurst =
            wantsIndependentTonalStill && jReader != null && !bracketWritesRaw
        if (bracketWritesRaw && wantsIndependentTonalStill) {
            Log.i(
                tag,
                "captureBracketBurst: independent tonal tier skipped in BKT (RAW stops only; use H still for DNG+tonal)",
            )
        }
        if (cam == null || sess == null || previewSurf == null || camId.isNullOrBlank()) {
            finishFailure(IllegalStateException("Camera not ready (use preview ≤119 fps)"))
            return
        }
        if (!bracketWritesRaw && !bracketWritesTonalInBurst) {
            finishFailure(
                IllegalStateException(
                    "Bracket needs RAW and/or JPEG output — check IMG tiers and preview ≤119 fps",
                ),
            )
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

        if (!awaitReaderExecutorDrain(PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS)) {
            PnsAdbLog.i(
                appContext,
                "captureBracketBurst pattern=$pattern ok=false err=encode_lane_busy",
            )
            finishFailure(IllegalStateException("Engine busy - retry"))
            return
        }
        discardQueuedStillImagesBeforeBracket()

        val needJpeg = bracketWritesTonalInBurst
        val resolvedPattern =
            when (purpose) {
                BracketBurstPurpose.BktDial -> pattern
                BracketBurstPurpose.HdrStill ->
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.hdrStillBracketPattern()
            }
        val evStep =
            when (purpose) {
                BracketBurstPurpose.BktDial -> 1.0
                BracketBurstPurpose.HdrStill ->
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.hdrStillEvStep()
            }
        val stillModeForDiag =
            when (purpose) {
                BracketBurstPurpose.BktDial -> StillCaptureMode.Standard
                BracketBurstPurpose.HdrStill -> StillCaptureMode.HdrStill
            }
        val filenameStem =
            when (purpose) {
                BracketBurstPurpose.BktDial -> "bkt"
                BracketBurstPurpose.HdrStill -> "hdr"
            }
        Log.i(
            tag,
            "captureBracketBurst purpose=$purpose pattern=$resolvedPattern evStep=$evStep " +
                "raw=$bracketWritesRaw tonalBurst=$needJpeg",
        )
        val groupingId =
            when (purpose) {
                BracketBurstPurpose.BktDial -> BracketPlan.newGroupingId()
                BracketBurstPurpose.HdrStill ->
                    "hdr-" + java.util.UUID.randomUUID().toString().take(12)
            }
        val plan = BracketPlan.build(resolvedPattern, evStep, groupingId = groupingId)
        val aeInts = BracketScheduler.aeStepsFor(plan, step, range)
        val savedUris = mutableListOf<String>()
        val manualSensorBracket = manualIsoOverride != null || manualExposureNsOverride != null
        val ultraBracket = stillCaptureBundle.rawMode == RawMode.UncompressedRaw12Dng
        val capsArr = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val useCaptureBurstPath =
            bracketWritesRaw &&
                BracketBurstSupport.mayUseSingleCaptureBurst(
                    availableCapabilities = capsArr,
                    shotCount = aeInts.size,
                    readerMaxImages = PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES,
                    manualSensorBracket = manualSensorBracket,
                )

        fun buildBracketStillRequest(aeComp: Int): CaptureRequest =
            cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(previewSurf)
                if (bracketWritesRaw) {
                    addTarget(checkNotNull(reader).surface)
                }
                if (needJpeg) {
                    addTarget(checkNotNull(jReader).surface)
                }
                applyScalerCropAndMetering(this, chars, camId, null)
                applyReadoutManualExposureAndWb(this, chars, camId)
                PreviewFlashPolicy.applyStillFlashKeys(
                    this,
                    chars,
                    PreviewFlashMode.Off,
                    manualSensorBracket,
                    commandDialMode,
                )
                applyStillAfFreezeAndFaceParity(this, chars, PreviewFlashMode.Off, manualSensorBracket)
                PreviewAeAntibanding.applyToRequest(this, chars)
                PreviewStabilization.applyToRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = null,
                    manualSensor = manualSensorBracket,
                    isStillCapture = true,
                    disableOisForStill = readHudCapturePrefs().disableOisForStillCapture,
                )
                PreviewPostRawSensitivity.applyIfCompatible(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    manualIsoOverride,
                    manualExposureNsOverride,
                )
                RawStillProcessingHints.applyLinearRawFriendlyProcessing(this, chars)
                RawStillProcessingHints.applyProShotPreviewExposureFromResult(
                    this,
                    chars,
                    camId,
                    lastPreviewTotalCaptureResult,
                )
                if (bracketWritesRaw) {
                    StillCaptureIqPolicy.applyToStillCaptureRequest(
                        this,
                        chars,
                        dev.pointandshoot.fleet.FleetCameraProfiles.profileForCameraId(appContext, camId),
                    )
                    dev.pointandshoot.fleet.Op13LeafStillColorCorrection.applyToStillCaptureRequest(
                        this,
                        chars,
                        camId,
                        lastPreviewTotalCaptureResult,
                    )
                }
                val proShotPureLeafBracket =
                    OnePlus13FleetPolicy.useProShotPureDngSave() &&
                        StillCaptureIqPolicy.isLeafBackCharacteristics(chars)
                val neutralBracketPipeline =
                    !proShotPureLeafBracket &&
                        RawCaptureSupport.useNeutralColorPipelineForRawStill(
                            cm,
                            cameraIds(),
                            chars,
                            camId,
                            previewSurfacePhysicalCameraId,
                            focalCropMode,
                        )
                if (
                    !DngSaveBisectState.skipJpegProcessingHintsOnRawStill &&
                    !proShotPureLeafBracket
                ) {
                    PreviewJpegProcessingHints.applyToCaptureRequest(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        skipColorCorrection =
                            manualAwbAlreadySetsColorCorrection() || neutralBracketPipeline,
                    )
                }
                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeComp)
                PreviewStillCaptureHints.applyJpegOrientationIfSupported(this, chars, surfaceRotation)
                PreviewStillCaptureHints.applyJpegGpsIfSupported(
                    this,
                    chars,
                    locationForStillMetadata(),
                )
                PreviewStillCaptureHints.applyZslIfCompatible(
                    this,
                    chars,
                    wantZsl = false,
                    manualSensorStill = manualSensorBracket,
                )
            }.build()

        fun runBracketSingleCaptureBurst() {
            fun burstFinishFailure(t: Throwable) {
                reader?.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                if (ultraBracket) {
                    bgHandler.post { resumePreviewRepeatingIfPossible() }
                }
                finishFailure(t)
            }

            reader?.setOnImageAvailableListener(null, null)
            jReader?.setOnImageAvailableListener(null, null)
            val requests = aeInts.map { buildBracketStillRequest(it) }
            val savesRemaining = java.util.concurrent.atomic.AtomicInteger(aeInts.size)
            val completionIndex = java.util.concurrent.atomic.AtomicInteger(0)

            val burstRunnable = Runnable {
                try {
                    if (ultraBracket) {
                        runCatching { sess.stopRepeating() }
                            .exceptionOrNull()
                            ?.let { Log.w(tag, "captureBracketBurst Ultra-Max stopRepeating: ${it.message}") }
                    }
                    Log.i(
                        tag,
                        "captureBracketBurst pattern=$pattern mode=captureBurst stops=${aeInts.size}",
                    )
                    sess.captureBurst(
                        requests,
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult,
                            ) {
                                if (finished.get()) return
                                val idx = completionIndex.getAndIncrement()
                                if (idx >= aeInts.size) return
                                onStillCaptureReadoutComplete(haptics)
                                mainHandler.post {
                                    lastStatus = "Bracket shot ${idx + 1}/${aeInts.size}…"
                                }
                                val rawImg =
                                    if (bracketWritesRaw) {
                                        runCatching { reader!!.acquireNextImage() }.getOrNull()
                                    } else {
                                        null
                                    }
                                if (bracketWritesRaw && rawImg == null) {
                                    burstFinishFailure(
                                        IllegalStateException(
                                            "No RAW buffer at bracket stop ${idx + 1} (burst)",
                                        ),
                                    )
                                    return
                                }
                                val jpegImg =
                                    if (needJpeg) {
                                        runCatching { jReader!!.acquireNextImage() }.getOrNull()
                                    } else {
                                        null
                                    }
                                if (needJpeg && jpegImg == null) {
                                    runCatching { rawImg?.close() }
                                    burstFinishFailure(
                                        IllegalStateException(
                                            "No JPEG buffer at bracket stop ${idx + 1} (burst)",
                                        ),
                                    )
                                    return
                                }
                                if (idx == aeInts.lastIndex && ultraBracket) {
                                    resumePreviewRepeatingIfPossible()
                                }
                                ioExecutor.execute {
                                    var handle: CaptureStorage.Handle? = null
                                    if (finished.get()) {
                                        runCatching { rawImg?.close() }
                                        runCatching { jpegImg?.close() }
                                        return@execute
                                    }
                                    try {
                                        val suffix =
                                            "${filenameStem}${idx + 1}of${aeInts.size}-${plan.groupingId}"
                                        val loc = locationForStillMetadata()
                                        val metaChars: CameraCharacteristics
                                        val metaResult: TotalCaptureResult
                                        if (bracketWritesRaw) {
                                            val dngResolved =
                                                DngMetadataResolver.resolveForDngSave(
                                                    cm,
                                                    camId,
                                                    chars,
                                                    result,
                                                    previewSurfacePhysicalCameraId,
                                                    allowPhysicalTotalResultPairing = false,
                                                )
                                            val paired =
                                                DngMetadataResolver.pairForDngCreator(
                                                    cm,
                                                    camId,
                                                    chars,
                                                    result,
                                                    previewSurfacePhysicalCameraId,
                                                    allowPhysicalTotalResultPairing = false,
                                                )
                                            metaChars = paired.first
                                            metaResult = paired.second
                                            val rawImgN = checkNotNull(rawImg)
                                            Log.i(
                                                CaptureStillLog.TAG,
                                                "dng save diag stillMode=$stillModeForDiag requestedMode=$stillModeForDiag " +
                                                    "bracketStop=${idx + 1}/${aeInts.size} purpose=$purpose " +
                                                    "stillBackend=${dev.pointandshoot.fleet.StillDngBackendPolicy.active().name} " +
                                                    "${dngResolved.toDiagSummary()} " +
                                                    "iso=${metaResult.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} " +
                                                    "rawFmt=${rawImgN.format} rawWxH=${rawImgN.width}x${rawImgN.height}",
                                            )
                                            val orient =
                                                RawCaptureSupport.orientationClockwiseDegForDng(
                                                    metaChars,
                                                    surfaceRotation,
                                                )
                                            handle =
                                                CaptureStorage.openOutput(
                                                    appContext.applicationContext,
                                                    imagingProfileForStreams,
                                                    stillCaptureBundle.toDngCaptureKind(),
                                                    useLocationBridge = false,
                                                    filenameSuffix = suffix,
                                                )
                                            val wideCalCharsBracket =
                                                wideLeafCalibrationCharacteristicsForDngSave(cm, camId)
                                            Dng12Saver(metaChars, imagingProfileForStreams).save(
                                                rawImgN,
                                                metaResult,
                                                handle.output,
                                                orientationDegrees = orient,
                                                location = loc,
                                                softwareDescription = dngSoftwareDescription,
                                                uniqueCameraModel =
                                                    dngUniqueCameraModelForSave(camId, stillsLut),
                                                sessionCameraId = camId,
                                                wideCalibrationCharacteristics = wideCalCharsBracket,
                                                adbValidationContext = appContext,
                                            )
                                            rawImgN.close()
                                            val uri = handle.uri.toString()
                                            val dngDisplayName = handle.displayName
                                            val dngUri = handle.uri
                                            handle.close()
                                            handle = null
                                            if (shouldApplyStillMetadataToDng(camId)) {
                                                StillCaptureMetadata.applyToDngUri(
                                                    appContext.applicationContext,
                                                    dngUri,
                                                    metaChars,
                                                    metaResult,
                                                    location = loc,
                                                )
                                            }
                                            writeCalibrationSidecarIfNeeded(
                                                appContext,
                                                imagingProfileForStreams,
                                                dngDisplayName,
                                            )
                                            synchronized(savedUris) { savedUris.add(uri) }
                                        } else {
                                            metaChars = chars
                                            metaResult = result
                                        }
                                        if (jpegImg != null) {
                                            val orient =
                                                RawCaptureSupport.orientationClockwiseDegForDng(
                                                    metaChars,
                                                    surfaceRotation,
                                                )
                                            try {
                                                val jpegUri =
                                                    runCatching {
                                                        saveHardwareJpegCompanion(
                                                            appContext,
                                                            imagingProfileForStreams,
                                                            jpegImg,
                                                            stillsLut,
                                                            metaChars,
                                                            metaResult,
                                                            orientationDegrees = orient,
                                                            filenameSuffix = suffix,
                                                        )
                                                    }.onFailure { Log.w(tag, "bracket JPEG failed", it) }
                                                        .getOrNull()
                                                if (jpegUri != null && !bracketWritesRaw) {
                                                    synchronized(savedUris) {
                                                        savedUris.add(jpegUri.toString())
                                                    }
                                                }
                                            } finally {
                                                jpegImg.close()
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        runCatching { rawImg?.close() }
                                        runCatching { jpegImg?.close() }
                                        runCatching { handle?.discard() }
                                        burstFinishFailure(t)
                                        return@execute
                                    }
                                    if (savesRemaining.decrementAndGet() == 0) {
                                        reader?.setOnImageAvailableListener(null, null)
                                        jReader?.setOnImageAvailableListener(null, null)
                                        val joined = savedUris.joinToString("\n")
                                        if (purpose == BracketBurstPurpose.HdrStill) {
                                            Log.i(
                                                CaptureStillLog.TAG,
                                                "hdr still bracket complete frames=${savedUris.size} " +
                                                    "label=${adbValidationShotLabel ?: "-"}",
                                            )
                                        }
                                        finishSuccess(joined)
                                    }
                                }
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure,
                            ) {
                                burstFinishFailure(RuntimeException("captureBurst failed reason=${failure.reason}"))
                            }

                            override fun onCaptureSequenceAborted(
                                session: CameraCaptureSession,
                                sequenceId: Int,
                            ) {
                                burstFinishFailure(IllegalStateException("Burst sequence aborted seq=$sequenceId"))
                            }
                        },
                        bgHandler,
                    )
                } catch (t: Throwable) {
                    burstFinishFailure(t)
                }
            }
            if (Looper.myLooper() == bgHandler.looper) {
                burstRunnable.run()
            } else {
                bgHandler.post(burstRunnable)
            }
        }

        fun scheduleShot(idx: Int) {
            if (idx >= aeInts.size) {
                reader?.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                finishSuccess(savedUris.joinToString("\n"))
                return
            }
            mainHandler.post {
                lastStatus = "Bracket shot ${idx + 1}/${aeInts.size}…"
            }
            val still = buildBracketStillRequest(aeInts[idx])
            val pendingRaw = java.util.concurrent.atomic.AtomicReference<Image?>(null)
            val pendingJpeg = java.util.concurrent.atomic.AtomicReference<Image?>(null)
            val pendingResult = java.util.concurrent.atomic.AtomicReference<TotalCaptureResult?>(null)
            val processed = AtomicBoolean(false)

            fun shotFail(t: Throwable) {
                if (!processed.compareAndSet(false, true)) return
                reader?.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                runCatching { pendingRaw.getAndSet(null)?.close() }
                runCatching { pendingJpeg.getAndSet(null)?.close() }
                pendingResult.set(null)
                if (ultraBracket) {
                    bgHandler.post { resumePreviewRepeatingIfPossible() }
                }
                finishFailure(t)
            }

            fun shotMaybeProcess() {
                if (bracketWritesRaw && pendingRaw.get() == null) return
                if (needJpeg && pendingJpeg.get() == null) return
                if (pendingResult.get() == null) return
                if (!processed.compareAndSet(false, true)) return
                reader?.setOnImageAvailableListener(null, null)
                jReader?.setOnImageAvailableListener(null, null)
                if (ultraBracket) {
                    resumePreviewRepeatingIfPossible()
                }
                val rawImg = if (bracketWritesRaw) pendingRaw.getAndSet(null)!! else null
                val jpegImg = if (needJpeg) pendingJpeg.getAndSet(null)!! else null
                val result = pendingResult.getAndSet(null)!!
                ioExecutor.execute {
                    var handle: CaptureStorage.Handle? = null
                    try {
                        val suffix =
                            "${filenameStem}${idx + 1}of${aeInts.size}-${plan.groupingId}"
                        val loc = locationForStillMetadata()
                        val metaChars: CameraCharacteristics
                        val metaResult: TotalCaptureResult
                        if (bracketWritesRaw) {
                            val rawImgN = checkNotNull(rawImg)
                            val dngResolved =
                                DngMetadataResolver.resolveForDngSave(
                                    cm,
                                    camId,
                                    chars,
                                    result,
                                    previewSurfacePhysicalCameraId,
                                    allowPhysicalTotalResultPairing = false,
                                )
                            val paired =
                                DngMetadataResolver.pairForDngCreator(
                                    cm,
                                    camId,
                                    chars,
                                    result,
                                    previewSurfacePhysicalCameraId,
                                    allowPhysicalTotalResultPairing = false,
                                )
                            metaChars = paired.first
                            metaResult = paired.second
                            Log.i(
                                CaptureStillLog.TAG,
                                "dng save diag stillMode=$stillModeForDiag requestedMode=$stillModeForDiag " +
                                    "bracketStop=${idx + 1}/${aeInts.size} purpose=$purpose " +
                                    "stillBackend=${dev.pointandshoot.fleet.StillDngBackendPolicy.active().name} " +
                                    "${dngResolved.toDiagSummary()} " +
                                    "iso=${metaResult.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} " +
                                    "rawFmt=${rawImgN.format} rawWxH=${rawImgN.width}x${rawImgN.height}",
                            )
                            val orient =
                                RawCaptureSupport.orientationClockwiseDegForDng(metaChars, surfaceRotation)
                            handle =
                                CaptureStorage.openOutput(
                                    appContext.applicationContext,
                                    imagingProfileForStreams,
                                    stillCaptureBundle.toDngCaptureKind(),
                                    useLocationBridge = false,
                                    filenameSuffix = suffix,
                                )
                            val wideCalCharsBracket2 =
                                wideLeafCalibrationCharacteristicsForDngSave(cm, camId)
                            Dng12Saver(metaChars, imagingProfileForStreams).save(
                                rawImgN,
                                metaResult,
                                handle.output,
                                orientationDegrees = orient,
                                location = loc,
                                softwareDescription = dngSoftwareDescription,
                                uniqueCameraModel = dngUniqueCameraModelForSave(camId, stillsLut),
                                sessionCameraId = camId,
                                wideCalibrationCharacteristics = wideCalCharsBracket2,
                                adbValidationContext = appContext,
                            )
                            rawImgN.close()
                            val uri = handle.uri.toString()
                            val dngDisplayName = handle.displayName
                            val dngUri = handle.uri
                            handle.close()
                            handle = null
                            if (shouldApplyStillMetadataToDng(camId)) {
                                StillCaptureMetadata.applyToDngUri(
                                    appContext.applicationContext,
                                    dngUri,
                                    metaChars,
                                    metaResult,
                                    location = loc,
                                )
                            }
                            writeCalibrationSidecarIfNeeded(
                                appContext,
                                imagingProfileForStreams,
                                dngDisplayName,
                            )
                            synchronized(savedUris) { savedUris.add(uri) }
                        } else {
                            metaChars = chars
                            metaResult = result
                        }
                        if (jpegImg != null) {
                            val orient =
                                RawCaptureSupport.orientationClockwiseDegForDng(metaChars, surfaceRotation)
                            try {
                                val jpegUri =
                                    runCatching {
                                        saveHardwareJpegCompanion(
                                            appContext,
                                            imagingProfileForStreams,
                                            jpegImg,
                                            stillsLut,
                                            metaChars,
                                            metaResult,
                                            orientationDegrees = orient,
                                            filenameSuffix = suffix,
                                        )
                                    }.onFailure { Log.w(tag, "bracket JPEG failed", it) }
                                        .getOrNull()
                                if (jpegUri != null && !bracketWritesRaw) {
                                    synchronized(savedUris) { savedUris.add(jpegUri.toString()) }
                                }
                            } finally {
                                jpegImg.close()
                            }
                        }
                    } catch (t: Throwable) {
                        runCatching { rawImg?.close() }
                        runCatching { jpegImg?.close() }
                        runCatching { handle?.discard() }
                        finishFailure(t)
                        return@execute
                    }
                    bgHandler.post { scheduleShot(idx + 1) }
                }
            }

            if (bracketWritesRaw) {
            reader!!.setOnImageAvailableListener({ r ->
                if (processed.get()) {
                    val dropped = runCatching { r.acquireLatestImage() }.getOrNull()
                    if (dropped != null) {
                        runCatching { dropped.close() }
                        Log.w("PNS.Reader", "drop oldest queue=post-process channel=raw-bracket")
                    }
                    return@setOnImageAvailableListener
                }
                val img = runCatching { r.acquireNextImage() }.getOrNull()
                    ?: return@setOnImageAvailableListener
                val prev = pendingRaw.getAndSet(img)
                if (prev != null) {
                    runCatching { prev.close() }
                    Log.w("PNS.Reader", "drop oldest queue=superseded channel=raw-bracket")
                }
                shotMaybeProcess()
            }, bgHandler)
            }

            if (needJpeg) {
                jReader!!.setOnImageAvailableListener({ r ->
                    if (processed.get()) {
                        val dropped = runCatching { r.acquireLatestImage() }.getOrNull()
                        if (dropped != null) {
                            runCatching { dropped.close() }
                            Log.w("PNS.Reader", "drop oldest queue=post-process channel=jpeg-bracket")
                        }
                        return@setOnImageAvailableListener
                    }
                    val img = runCatching { r.acquireNextImage() }.getOrNull()
                        ?: return@setOnImageAvailableListener
                    val prev = pendingJpeg.getAndSet(img)
                    if (prev != null) {
                        runCatching { prev.close() }
                        Log.w("PNS.Reader", "drop oldest queue=superseded channel=jpeg-bracket")
                    }
                    shotMaybeProcess()
                }, bgHandler)
            }

            val captureRunnable = Runnable {
                try {
                    if (ultraBracket) {
                        runCatching { sess.stopRepeating() }
                            .exceptionOrNull()
                            ?.let { Log.w(tag, "captureBracketBurst Ultra-Max stopRepeating: ${it.message}") }
                    }
                    sess.capture(
                        still,
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult,
                            ) {
                                onStillCaptureReadoutComplete(haptics)
                                pendingResult.set(result)
                                shotMaybeProcess()
                                val postCompleteWaitMs =
                                    when (stillCaptureBundle.rawMode) {
                                        RawMode.UncompressedRaw12Dng -> RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12
                                        else -> BRACKET_POST_COMPLETE_WAIT_MS_DEFAULT
                                    }
                                bgHandler.postDelayed({
                                    if (!processed.get()) {
                                        when {
                                            bracketWritesRaw && pendingRaw.get() == null ->
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
                                }, postCompleteWaitMs)
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
            if (Looper.myLooper() == bgHandler.looper) {
                captureRunnable.run()
            } else {
                bgHandler.post(captureRunnable)
            }
        }

        bgHandler.post {
            if (useCaptureBurstPath) {
                runBracketSingleCaptureBurst()
            } else {
                scheduleShot(0)
            }
        }
    }

    fun status(): String = lastStatus

    /**
     * Short readout-strip hint while a still or bracket capture is in flight ([captureBusy]).
     * Returns null when idle or when [lastStatus] is not a user-facing capture line.
     */
    fun readoutCapturePipelineHint(): String? {
        if (pendingTonalEncodes.get() > 0) {
            return "Processing tonal still…"
        }
        if (!captureBusy.get()) return null
        val s = lastStatus
        return when {
            s.startsWith("Still capture", ignoreCase = true) -> s
            s.startsWith("Saving still", ignoreCase = true) -> s
            s.startsWith("Processing tonal", ignoreCase = true) -> s
            s.startsWith("Bracket shot", ignoreCase = true) -> s
            else -> null
        }
    }

    /** Preview frames delivered to [TextureView] since last [closeCamera]; ADB RAW settle gate. */
    fun peekInAppVideoRecorderPresent(): Boolean = videoController.isRecorderPresent()

    /** ADB / gates — preview pipeline FPS EMA from capture timestamps. */
    fun peekPreviewSmoothedFps(): Double = smoothedFps

    fun peekInAppVideoRecorderStarted(): Boolean =
        videoController.isRecorderStarted() && videoController.isMuxerReadyForRecord()

    fun peekInAppVideoMcEncoderRecording(): Boolean = videoController.isMcEncoderRecording()

    /** ADB in-app video: MC HFR may need several seconds after [maybeStartInAppVideoRecorder] for the first encoded frame. */
    fun peekInAppVideoAutomationRecordReady(): Boolean {
        if (videoController.isStartFailureHold()) return false
        if (!videoController.isRecorderPresent()) return false
        val encoderLive =
            videoController.isRecorderStarted() || videoController.isMcEncoderRecording()
        if (!encoderLive) return false
        return videoController.isMuxerReadyForRecord() ||
            (dualVideoActive && videoController.getRecordingSurface()?.isValid == true)
    }

    /** Sprint **14.2** — live audio meters in [PreviewTopStatusBar] while in-app video records. */
    fun peekInAppVideoAudioAmplitude(): Int = videoController.peekAudioAmplitude()

    fun hintInAppVideoMediaCodecPath(wants: Boolean) {
        videoController.hintMediaCodecPath(wants)
    }

    fun peekInAppVideoShellStartFailureHold(): Boolean = videoController.isStartFailureHold()

    fun peekRawVideoRecordingActive(): Boolean = rawVideoController.isRecording

    fun wantsRawVideoLane(): Boolean {
        if (adbForceRawVideoLane) return true
        val prefs = readHudCapturePrefs()
        if (prefs.videoEncodeLane != VideoEncodeLane.Raw) return false
        return rawVideoController.fleetSupportsRawVideo(selectedCameraId)
    }

    fun applyRawVideoRecordingShell(
        wantRecord: Boolean,
        profile: ImagingProfile,
        onStopped: (android.net.Uri?) -> Unit = {},
    ) {
        val h = handler
        if (h == null) {
            if (!wantRecord) onStopped(null)
            return
        }
        h.post {
            if (!wantRecord) {
                detachRawVideoDrainListener(force = true)
                val uri = rawVideoController.stopRecording()
                refreshRepeatingPreviewOnly()
                mainHandler.post { onStopped(uri) }
                return@post
            }
            val reader = rawImageReader
            val cam = selectedCameraId
            val size = rawPreviewSize
            if (reader == null || cam.isNullOrBlank() || size == null) {
                Log.w(tag, "rawVideo start blocked reader=$reader cam=$cam size=$size")
                mainHandler.post { onStopped(null) }
                return@post
            }
            if (!rawVideoController.startRecording(cam, profile, size.width, size.height, rawPreviewFormat)) {
                mainHandler.post { onStopped(null) }
                return@post
            }
            attachRawVideoDrainListener()
            refreshRepeatingPreviewOnly()
        }
    }

    fun previewCameraHandlerReady(): Boolean = handler != null

    fun previewTextureFrameCount(): Long = framesFromTexture

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

    fun previewMeterIso(): Int? = previewMetadata.get().iso

    fun previewMeterExposureNs(): Long? = previewMetadata.get().exposureNs

    /** WB value for the readout chip: manual override when set, otherwise last preview result. */
    fun previewReadoutAwbMode(): Int? =
        manualAwbModeOverride ?: previewMetadata.get().awbMode

    fun previewMeterLogicalPhysicalId(): String? = lastLogicalMultiCameraPhysicalId

    /** True when preview session includes a hardware JPEG surface for independent tonal stills. */
    fun previewUsesJpegCompanion(): Boolean = jpegImageReader != null

    private var wantsIndependentTonalStill: Boolean = false

    @Volatile
    private var composedCapturePlan: ComposedCapturePlan =
        ComposedCapturePlan(
            raw = legacyStillBundle(ImagingProfile.StandardPro),
            tonal = null,
        )

    /**
     * RAW [ImageReader] format pick (see [RawStreamPreference]); ADB matrix can override before session create.
     */
    @Volatile private var rawStreamPreference: RawStreamPreference = RawStreamPreference.Default

    /**
     * Drives whether [createSession] attaches a hardware JPEG [ImageReader] alongside RAW when
     * [preferredJpegCompanion] is on and the map lists JPEG outputs (IMG **-JPEG-** not Off).
     * RAW12 + JPEG was unstable on some **CPH2655-class** stacks; session create retries still apply
     * if the HAL rejects the dual surface — do not blanket-omit companion when the user requests it.
     */
    /** Folder / stream class key; [stillCaptureBundle] carries independent RAW vs HDR truth. */
    @Volatile private var imagingProfileForStreams: ImagingProfile = ImagingProfile.StandardPro

    @Volatile private var stillCaptureBundle: StillCaptureBundle = legacyStillBundle(ImagingProfile.StandardPro)

    fun stillCaptureBundle(): StillCaptureBundle = stillCaptureBundle

    fun composedCapturePlan(): ComposedCapturePlan = composedCapturePlan

    /** IMG matrix: independent RAW and/or tonal bundles; restarts when stream outputs change. */
    fun setComposedCapturePlan(plan: ComposedCapturePlan) {
        wantsIndependentTonalStill = plan.tonal != null
        val streamBundle = plan.raw ?: checkNotNull(plan.tonal) { "forbidden off+off" }
        val storage =
            when {
                plan.raw != null -> storageProfileFromBundle(plan.raw)
                else -> ImagingProfile.JpegOnly
            }
        if (composedCapturePlan == plan && stillCaptureBundle == streamBundle && imagingProfileForStreams == storage) {
            return
        }
        composedCapturePlan = plan
        stillCaptureBundle = streamBundle
        imagingProfileForStreams = storage
        Log.d(
            tag,
            "setComposedCapturePlan raw=${plan.raw?.rawMode} tonal=${plan.tonal?.tonalContainer} " +
                "wantTonalSurface=$wantsIndependentTonalStill storage=${storage.id}",
        )
        maybeRestart()
    }

    /** Updates RAW/HDR packaging and restarts the session when stream shape may change. */
    fun setStillCaptureBundle(bundle: StillCaptureBundle) {
        val storage = storageProfileFromBundle(bundle)
        if (stillCaptureBundle == bundle && imagingProfileForStreams == storage) return
        stillCaptureBundle = bundle
        imagingProfileForStreams = storage
        Log.d(
            tag,
            "setStillCaptureBundle raw=${bundle.rawMode} tonal=${bundle.tonalContainer} storage=${storage.id}",
        )
        maybeRestart()
    }

    /** Mirrors HUD imaging profile for stream configuration; restarts when stream set may change. */
    fun setImagingProfileForStreams(profile: ImagingProfile) {
        setStillCaptureBundle(legacyStillBundle(profile))
    }

    /** Mirrors [PreviewChromePreferences.stillCaptureJpegCompanion] (independent tonal, not RAW companion). */
    fun setPreferredJpegCompanion(want: Boolean) {
        if (wantsIndependentTonalStill == want) return
        wantsIndependentTonalStill = want
        Log.d(tag, "setPreferredJpegCompanion want=$want (independent tonal surface)")
        maybeRestart()
    }

    /** ADB / matrix: which advertised RAW tier to attach ([RawStreamPreference]). */
    fun setRawStreamPreference(preference: RawStreamPreference) {
        if (rawStreamPreference == preference) return
        rawStreamPreference = preference
        Log.d(tag, "setRawStreamPreference preference=$preference")
        maybeRestart()
    }

    /** After returning from another activity (e.g. gallery viewer), rebuild the capture session. */
    fun kickPreviewPipelineRestart() {
        maybeRestart()
    }

    private fun notifyCaptureAutomationIdle() {
        if (!captureBusy.get() &&
            !adbScriptedStillAutomationActive.get() &&
            pendingMaybeRestartAfterCapture.compareAndSet(true, false)
        ) {
            maybeRestart()
        }
    }

    /** Clears [captureBusy] and replays one coalesced [maybeRestart] if restarts were deferred mid-still. */
    private fun releaseCaptureBusy() {
        captureBusy.set(false)
        notifyCaptureAutomationIdle()
    }

    /**
     * Bracket / sequential RAW ADB automation: hold through pre-capture waits so [maybeRestartBody]
     * does not [closeCamera] mid-handshake (texture sizing vs Ultra-Max session).
     */
    fun markAdbScriptedStillAutomationActive(active: Boolean) {
        adbScriptedStillAutomationActive.set(active)
        if (!active) {
            notifyCaptureAutomationIdle()
        }
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

    /**
     * Drop stale session/readers after [CameraCaptureSession.StateCallback.onConfigureFailed] so ADB still
     * automation does not capture against a session that never included the current RAW surface.
     */
    private fun handleCaptureSessionConfigureFailed(
        failedSession: CameraCaptureSession,
        configureGen: Long,
        path: String,
    ) {
        runCatching { failedSession.close() }
        if (configureGen != generation) return
        runCatching { session?.close() }
        session = null
        sessionCommittedGeneration = -1L
        runCatching { rawImageReader?.close() }
        rawImageReader = null
        runCatching { jpegImageReader?.close() }
        jpegImageReader = null
        runCatching { yuvImageReader?.close() }
        yuvImageReader = null
        videoRecordingSessionRebuildPending = false
        sessionPreviewDynamicRangeShort = null
        superMacroSessionConfigured = false
        lastStatus = "Session configure failed ($path)"
        val hw = handler
        if (hw != null && device != null && !selectedCameraId.isNullOrBlank()) {
            hw.postDelayed({ maybeRestartBody() }, 250L)
        }
    }

    /**
     * @param teardownPreparedMediaRecorder When false, omits [tearDownInAppVideoRecordingForCloseCamera] so a
     * prepared video recorder survives [generation] bumps while [maybeRestartBody] rebuilds the capture
     * session. Full teardown paths ([stop], texture destroyed, errors) pass true (default).
     */
    private fun closeCamera(teardownPreparedMediaRecorder: Boolean = true) {
        if (!dualVideoActive) {
            closeDualFrontCamera()
            dualVideoEncoderSink.release()
        } else {
            Log.d(
                DualVideoRecordingController.TAG,
                "closeCamera: keeping dual front (dualVideoActive, teardown=$teardownPreparedMediaRecorder)",
            )
        }
        if (teardownPreparedMediaRecorder) {
            stopHfrRecordMonitor()
            hfrInterleavedRecordActive = false
            inAppVideoRecordingArmed = false
        }
        PreviewLogicalPhysicalDebugBridge.clear()
        captureSessionAsyncConfigurePending = false
        cameraDeviceOpenPending = false
        maybeRestartSessionPendingDeferrals = 0
        if (teardownPreparedMediaRecorder) {
            tearDownInAppVideoRecordingForCloseCamera()
        }
        generation++
        faceTracker.reset()
        lastTeleRouteAdbKey = null
        lastTrackerLockedLogged = null
        tapMeteringRect = null
        loggedFaceDetectCaps = false
        loggedFaceStatisticsSample = false
        loggedAdbTrackerPipelineReady = false
        loggedMlFaceSample = false
        clearFaceHudOverlayState()
        loggedSuperMacroProbeWrongCam = false
        loggedSuperMacroProbeUw = false
        superMacroSessionConfigured = false
        sessionPreviewDynamicRangeShort = null
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { rawImageReader?.close() }
        rawImageReader = null
        runCatching { jpegImageReader?.close() }
        jpegImageReader = null
        runCatching { yuvImageReader?.close() }
        yuvImageReader = null
        resetHighlightMeterPipelineState()
        lastHighlightProcessWallMs = 0L
        lastHighlightMeterAdbLogMs = 0L
        session = null
        sessionCommittedGeneration = -1L
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
        previewMetadata.set(PreviewMetadata(null, null, null))
        lastLogicalMultiCameraPhysicalId = null
        loggedChromeUxReadout = false
        loggedChromeUxFlashHardware = false
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

    /**
     * Best-effort wait for [companionJpegExecutor] to finish queued work (activity [ON_PAUSE]).
     * Does not shut the pool down — [PreviewEngineScreen] lifecycle keeps the controller alive.
     */
    fun drainCompanionJpegExecutor(timeoutMs: Long) {
        val f = companionJpegExecutor.submit { }
        try {
            f.get(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            f.cancel(true)
            Log.w(tag, "drainCompanionJpegExecutor timed out after ${timeoutMs}ms")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            f.cancel(true)
        } catch (t: Throwable) {
            f.cancel(true)
            Log.w(tag, "drainCompanionJpegExecutor failed: ${t.message}")
        }
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
        h.removeCallbacks(maybeRestartDebouncedRunnable)
        h.postDelayed(maybeRestartDebouncedRunnable, MAYBE_RESTART_DEBOUNCE_MS)
    }

    private fun maybeRestartBody() {
        val deferScriptedSurfaceRestart =
            adbScriptedStillAutomationActive.get() && device != null
        if (captureBusy.get() || deferScriptedSurfaceRestart) {
            pendingMaybeRestartAfterCapture.set(true)
            Log.d(
                tag,
                "maybeRestartBody: defer (capture busy=${captureBusy.get()} " +
                    "scriptedSurfaceHold=${deferScriptedSurfaceRestart})",
            )
            return
        }
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
        val recordForNegotiation =
            when {
                dualVideoActive && inAppVideoRecordingArmed ->
                    DualVideoRecordingController.compositeRecordSize()
                inAppVideoRecordingArmed && desiredFps < 120 ->
                    resolveInAppVideoRecordSize()
                else -> null
            }
        val previewAlignedToRecord =
            recordForNegotiation?.let { rec ->
                InAppVideoRecordingSupport.pickPreviewSizeAlignedToRecord(map, rec)
            }
        if (recordForNegotiation != null) {
            val halMr =
                InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(
                    map,
                    recordForNegotiation.width,
                    recordForNegotiation.height,
                )
            val halSt =
                InAppVideoRecordingSupport.supportsSurfaceTextureOutputSize(
                    map,
                    recordForNegotiation.width,
                    recordForNegotiation.height,
                )
            Log.i(
                "PNS.VideoEncode",
                "eightKNegotiation record=${recordForNegotiation.width}x${recordForNegotiation.height} " +
                    "halMr=$halMr halSt=$halSt previewAligned=" +
                    "${previewAlignedToRecord?.width ?: 0}x${previewAlignedToRecord?.height ?: 0}",
            )
        }
        val wantedSurfaceSize =
            when {
                desiredFps >= 120 -> desiredHighSpeedSize
                previewAlignedToRecord != null -> previewAlignedToRecord
                else -> pickNormalPreviewSize(map, activeArray, chars) ?: emergencyPreview
            }
        desiredSurfaceSize = wantedSurfaceSize

        // Tear down the camera session before replacing the TextureView Surface. Releasing the old
        // Surface while createCaptureSession is still configuring causes IllegalArgumentException:
        // "Surface was abandoned" (OutputConfiguration).
        if (captureSessionAsyncConfigurePending || cameraDeviceOpenPending) {
            val hw = handler ?: return
            if (maybeRestartSessionPendingDeferrals < MAYBE_RESTART_SESSION_PENDING_DEFERRAL_CAP) {
                maybeRestartSessionPendingDeferrals++
                Log.d(
                    tag,
                    "maybeRestartBody: defer closeCamera " +
                        "(session configure or camera open pending) " +
                        "deferral=$maybeRestartSessionPendingDeferrals",
                )
                hw.postDelayed({ maybeRestartBody() }, MAYBE_RESTART_SESSION_PENDING_RESCHEDULE_MS)
                return
            }
            Log.w(tag, "maybeRestartBody: forcing closeCamera after configure-pending deferral cap")
            maybeRestartSessionPendingDeferrals = 0
        }
        val preservePreparedMediaRecorder =
            videoController.isRecorderPresent() &&
                (device == null || device?.id == camId)
        closeCamera(teardownPreparedMediaRecorder = !preservePreparedMediaRecorder)
        val h = handler ?: return
        val ws = wantedSurfaceSize
        // [SurfaceTexture.setDefaultBufferSize] can race GL/TextureView teardown when invoked from
        // the camera thread immediately after [closeCamera] while Compose is still swapping the
        // underlying BufferQueue (cold start / camera-id seed). Run sizing on the main looper,
        // then continue open on [handler] so the ST reference matches what the view published.
        if (ws != null) {
            mainHandler.post {
                val stTex = previewSurfaceTexture
                if (stTex == null) {
                    Log.w(tag, "maybeRestart: no SurfaceTexture on main after close; deferring")
                    h.postDelayed({ maybeRestartBody() }, SURFACE_TEXTURE_BUFFER_RETRY_DELAY_MS)
                    return@post
                }
                val sizedOk =
                    runCatching {
                        stTex.setDefaultBufferSize(ws.width, ws.height)
                        true
                    }.getOrDefault(false)
                if (!sizedOk) {
                    Log.w(tag, "setDefaultBufferSize failed on main after close; deferring restart")
                    h.postDelayed({ maybeRestartBody() }, SURFACE_TEXTURE_BUFFER_RETRY_DELAY_MS)
                    return@post
                }
                h.post {
                    currentSurfaceSize = ws
                    rebuildSurfaceIfPossible()
                    Log.d(tag, "setDefaultBufferSize ${ws.width}x${ws.height} (main-thread sized)")
                    Log.i(
                        "PNS.VideoEncode",
                        "sessionBufferSet ${ws.width}x${ws.height} encodePref=" +
                            "${inAppVideoEncodeSizePref?.width ?: 0}x${inAppVideoEncodeSizePref?.height ?: 0} " +
                            "fps=$desiredFps",
                    )
                    Log.d(tag, "openAndStart (after close) cameraId=$camId fps=$desiredFps")
                    openAndStart(camId)
                }
            }
            return
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
        if (cameraDeviceOpenPending) {
            Log.d(tag, "openAndStart deferred: camera open pending (want=$camId)")
            h.postDelayed({ maybeRestartBody() }, MAYBE_RESTART_SESSION_PENDING_RESCHEDULE_MS)
            return
        }
        lastStatus = "Opening cameraId=$camId"
        Log.d(tag, "openCamera cameraId=$camId fps=$desiredFps")
        val gen = generation
        cameraDeviceOpenPending = true
        try {
            cm.openCamera(
                camId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            if (gen != generation) {
                                Log.w(tag, "onOpened ignored (stale gen=$gen current=$generation)")
                                runCatching { camera.close() }
                                return
                            }
                            device = camera
                            lastStatus = "Opened cameraId=$camId; creating session (fps=$desiredFps)"
                            Log.d(tag, "onOpened cameraId=$camId; creating session")
                            createSession(camera, camId)
                        } finally {
                            cameraDeviceOpenPending = false
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        try {
                            lastStatus = "Disconnected cameraId=$camId"
                            Log.w(tag, "onDisconnected cameraId=$camId")
                            recordCapturePipelineEvent(
                                "CAMERA_DISCONNECTED",
                                "onDisconnected",
                                mapOf("camId" to camId),
                            )
                            runCatching { camera.close() }
                            device = null
                        } finally {
                            cameraDeviceOpenPending = false
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        try {
                            lastStatus = "Camera error cameraId=$camId error=$error"
                            Log.e(tag, "onError cameraId=$camId error=$error")
                            recordCapturePipelineEvent(
                                "CAMERA_DEVICE_ERROR",
                                cameraDeviceErrorLabel(error),
                                mapOf("camId" to camId, "code" to error.toString()),
                                flushToFile = true,
                            )
                            runCatching { camera.close() }
                            device = null
                        } finally {
                            cameraDeviceOpenPending = false
                        }
                    }
                },
                h,
            )
        } catch (e: SecurityException) {
            cameraDeviceOpenPending = false
            lastStatus = "Missing CAMERA permission"
            recordCapturePipelineEvent("OPEN_CAMERA", "SecurityException", emptyMap(), flushToFile = true)
        } catch (e: CameraAccessException) {
            cameraDeviceOpenPending = false
            lastStatus = "CameraAccessException: ${e.reason}"
            recordCapturePipelineEvent(
                "OPEN_CAMERA",
                "CameraAccessException",
                mapOf("reason" to "${e.reason}"),
                flushToFile = true,
            )
        } catch (t: Throwable) {
            cameraDeviceOpenPending = false
            lastStatus = "Open failed: ${t::class.java.simpleName}"
            recordCapturePipelineEvent(
                "OPEN_CAMERA",
                t.message ?: t::class.java.simpleName,
                mapOf("type" to t::class.java.simpleName),
                flushToFile = true,
            )
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
        if (!wantsMacroProgram() && !superMacroAdbProbe) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val uw =
            runCatching {
                BackCameraRoleResolver.resolve(cm, cameraIds()).ultraWide
            }.getOrNull() ?: return false
        return uw == camId
    }

    private fun configureJpegCompanionReader(
        mapForStreams: StreamConfigurationMap?,
        surfaces: MutableList<Surface>,
    ) {
        val jpegSize = mapForStreams?.let { RawCaptureSupport.pickLargestJpegSize(it) }
        val jpegOnlySession = imagingProfileForStreams is ImagingProfile.JpegOnly
        val attachJpegSurface =
            jpegOnlySession || (wantsIndependentTonalStill && jpegSize != null)
        if (!attachJpegSurface) {
            jpegImageReader = null
            when {
                imagingProfileForStreams is ImagingProfile.JpegOnly ->
                    Log.w(tag, "JPEG-only session: no JPEG output sizes from map")
                !wantsIndependentTonalStill ->
                    Log.d(tag, "Tonal JPEG surface off — RAW-only or no -JPEG- tier")
                else -> Log.w(tag, "No JPEG output sizes — tonal still unavailable")
            }
            return
        }
        val sz = jpegSize!!
        val reader =
            ImageReader.newInstance(
                sz.width,
                sz.height,
                ImageFormat.JPEG,
                PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES,
            )
        jpegImageReader = reader
        surfaces.add(reader.surface)
        Log.d(tag, "JPEG ImageReader ${sz.width}x${sz.height}")
    }

    /**
     * REGULAR-session vendor template: **EnableHDRDCGMode** (13.4) and/or **EnableAFBracketing** (10.6).
     */
    private fun buildVendorSessionParametersTemplate(camera: CameraDevice, camId: String): CaptureRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val ch = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return null
        val prefs = readHudCapturePrefs()
        val attachDcg =
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = prefs.enableResearchDcgHDR,
                adbPreviewVideoDcg = adbAutomationVideoDcg,
            ) && !wantsRawVideoLane()
        val template =
            DcgSessionParameters.buildSessionParametersTemplate(
                camera = camera,
                characteristics = ch,
                camId = camId,
                prefs = prefs,
                previewFpsRange = pickNormalFpsRange(camId, previewTargetFpsForSession()),
                attachDcg = attachDcg,
                attachAfBracketing = prefs.enableResearchAfBracketing,
            )
        if (template != null && attachDcg) {
            PnsAdbLog.i(appContext, "dcgSessionTemplate=EnableHDRDCGMode cam=$camId")
        }
        return template
    }

    /**
     * REGULAR preview session with stream-use-case hints (API 33+) and optional preview dynamic range,
     * with HAL-friendly fallbacks (no stream hints / no HDR profile / no research session parameters).
     */
    private fun createRegularCaptureSessionWithRetries(
        camera: CameraDevice,
        surfaces: List<Surface>,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        streamHints: Boolean,
        chosenPreviewDr: Long?,
        sessionParametersTemplate: CaptureRequest?,
        previewPhysicalCameraId: String?,
        physicalPinnedSurfaceIndices: Set<Int>? = null,
    ): Throwable? {
        // Try [OutputConfiguration.setPhysicalCameraId] on the preview stream whenever a tele pin is
        // requested. Some OEMs reject multi-output + pin (retry without pin below); others need the
        // pin because [availableCaptureRequestKeys] omits LOGICAL active-physical (CPH2655-class).
        var pinPhys = previewPhysicalCameraId?.takeIf { it.isNotBlank() }
        var pinSurfaceIndices = physicalPinnedSurfaceIndices
        if (pinPhys != null && surfaces.size > 1) {
            Log.w(
                tag,
                "preview OutputConfiguration physical pin with surfaces=${surfaces.size} " +
                    "indices=${pinSurfaceIndices ?: setOf(0)} (HAL may require retry without pin)",
            )
        }

        fun tryOnce(hints: Boolean, previewDr: Long?, sessPar: CaptureRequest?): Throwable? =
            runCatching {
                camera.createCaptureSessionRegularOutputs(
                    surfaces,
                    handler,
                    callback,
                    streamUseCaseHints = hints,
                    previewDynamicRangeProfile = previewDr,
                    sessionParametersTemplate = sessPar,
                    previewPhysicalCameraId = pinPhys,
                    physicalPinnedSurfaceIndices = pinSurfaceIndices,
                )
                sessionPreviewDynamicRangeShort =
                    previewDr?.let { dr -> PreviewDynamicRangeLabels.shortLabel(dr) }
            }.exceptionOrNull()
        var createErr = tryOnce(streamHints, chosenPreviewDr, sessionParametersTemplate)
        if (createErr != null && pinPhys != null) {
            Log.w(
                tag,
                "createCaptureSession retry without preview physical pin (was $pinPhys): " +
                    "${createErr::class.java.simpleName}: ${createErr.message}",
            )
            pinPhys = null
            pinSurfaceIndices = null
            previewSurfacePhysicalCameraId = null
            createErr = tryOnce(streamHints, chosenPreviewDr, sessionParametersTemplate)
        }
        if (createErr != null && sessionParametersTemplate != null) {
            Log.w(
                tag,
                "createCaptureSession retry without research session parameters " +
                    "(${createErr::class.java.simpleName}: ${createErr.message})",
            )
            createErr = tryOnce(streamHints, chosenPreviewDr, null)
        }
        if (createErr != null && chosenPreviewDr != null) {
            Log.w(
                tag,
                "createCaptureSession retry without HDR dynamic range " +
                    "(${createErr::class.java.simpleName}: ${createErr.message})",
            )
            createErr = tryOnce(streamHints, null, null)
        }
        if (createErr != null && streamHints) {
            Log.w(
                tag,
                "createCaptureSession (stream hints) threw ${createErr::class.java.simpleName}: " +
                    "${createErr.message}; retry without hints",
            )
            createErr = tryOnce(false, chosenPreviewDr, null)
            if (createErr != null && chosenPreviewDr != null) {
                createErr = tryOnce(false, null, null)
            }
        }
        return createErr
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
        sessionPreviewDynamicRangeShort = null
        val map = runCatching { cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) }.getOrNull()
        val target = pickHighSpeedTarget(map, desiredFps)

        // Constrained HS + aligned record/preview sizes ([resolveInAppVideoRecordSize], [desiredHighSpeedSize])
        // are required for true HFR frame delivery; regular sessions cap AE near 60 fps.
        val useHighSpeed =
            target != null &&
                desiredFps >= 120
        Log.d(tag, "createSession camId=$camId desiredFps=$desiredFps useHighSpeed=$useHighSpeed target=${target?.first?.width}x${target?.first?.height} ${target?.second}")

        runCatching { rawImageReader?.close() }
        rawImageReader = null
        runCatching { jpegImageReader?.close() }
        jpegImageReader = null
        runCatching { yuvImageReader?.close() }
        yuvImageReader = null

        val surfaces = mutableListOf(surf)
        if (!dualVideoActive) {
            videoController.getRecordingSurface()?.takeIf { it.isValid }?.let { surfaces.add(it) }
        }
        if (!useHighSpeed && !dualVideoActive) {
            if (!videoController.isRecorderPresent()) {
            val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            val mapForStreams =
                chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawPick =
                RawCaptureSupport.pickRawOutputForPreviewSession(
                    cm,
                    cameraIds(),
                    camId,
                    chars,
                    previewSurfacePhysicalCameraId,
                    rawStreamPreference,
                    focalCropMode = focalCropMode,
                    usePhysicalChildRawStreamMapForLogicalSession = false,
                )
            MotionCamInspiredStillPolicy.logSessionContext(camId)
            val jpegOnlySession = imagingProfileForStreams is ImagingProfile.JpegOnly
            if (jpegOnlySession) {
                configureJpegCompanionReader(mapForStreams, surfaces)
            } else if (rawPick != null) {
                val (fmt, size) = rawPick
                // auto-drain listener was racing the still-capture path: when a RAW image
                // arrived for a real capture the listener would acquire+close it before
                // [captureRawStill]'s onCaptureCompleted got a chance, and the user saw
                // "No RAW buffer". Leaving the queue undrained lets onCaptureCompleted
                // acquire the image deterministically. Queue depth stays bounded via
                // [`PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES`] + [PNS.Reader] supersede logging.
                rawImageReader =
                    ImageReader.newInstance(size.width, size.height, fmt, PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES)
                rawPreviewFormat = fmt
                rawPreviewSize = size
                surfaces.add(rawImageReader!!.surface)
                Log.d(
                    tag,
                    "RAW ImageReader ${size.width}x${size.height} format=$fmt " +
                        "(${RawCaptureSupport.rawPickEffectiveLabel(fmt)}) rawStreamPreference=$rawStreamPreference",
                )
                when {
                    rawVideoController.isRecording -> attachRawVideoDrainListener()
                    wantsZslStillRing() -> attachZslRawRingListener()
                }

                configureJpegCompanionReader(mapForStreams, surfaces)
            }

            // When [automationSuppressFacePipeline] is true (ADB bracket automation), skip YUV analysis
            // surfaces unless the face HUD still needs them — extra YUV + H dial forced session churn and
            // RAW still HAL timeouts on some devices (e.g. CPH2655 with wantYuv=true while suppressFace=true).
            val wantYuv =
                !lifecycleBackgroundPaused &&
                    (
                        (commandDialMode == CommandDialMode.H && desiredFps < 120 && !automationSuppressFacePipeline) ||
                            (commandDialMode == CommandDialMode.Qr && !automationSuppressFacePipeline) ||
                            (!automationSuppressFacePipeline && previewHistogramEnabled) ||
                            (!automationSuppressFacePipeline && highlightClipZebraEnabled) ||
                            (hudFaceOverlayEnabled && !automationSuppressFacePipeline) ||
                            (smileStillEnabled && !automationSuppressFacePipeline) ||
                            (wantsReadoutExposureChase() && desiredFps < 120)
                    )
            if (wantYuv) {
                val yuvPick = desiredSurfaceSize ?: currentSurfaceSize
                val yuvSize = HighlightMeterSupport.pickYuv420AnalysisSize(map, yuvPick)
                if (yuvSize != null) {
                    yuvImageReader =
                        ImageReader.newInstance(
                            yuvSize.width,
                            yuvSize.height,
                            ImageFormat.YUV_420_888,
                            PerfBudget.Defaults.YUV_ANALYSIS_READER_MAX_IMAGES,
                        ).also { ir ->
                            ir.setOnImageAvailableListener({ reader -> processYuvForHighlight(reader) }, h)
                        }
                    surfaces.add(yuvImageReader!!.surface)
                    Log.d(tag, "YUV highlight ImageReader ${yuvSize.width}x${yuvSize.height}")
                } else {
                    Log.w(tag, "Highlight metering: no YUV_420_888 size")
                }
            }
            val displayHz =
                runCatching {
                    val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
                }.getOrNull()
            PnsLog.i(
                tag,
                buildString {
                    append("PNS.PreviewSessionCtx ")
                    append("defaultDisplayHz=")
                    append(if (displayHz != null) "%.1f".format(displayHz) else "?")
                    append(" desiredFps=").append(desiredFps)
                    append(" dial=").append(commandDialMode.name)
                    append(" useHighSpeed=").append(false)
                    append(" wantYuv=").append(wantYuv)
                    append(" yuvAttached=").append(yuvImageReader != null)
                    append(" suppressFacePipeline=").append(automationSuppressFacePipeline)
                    append(" sessionGen=").append(gen)
                },
            )
            recordCapturePipelineEvent(
                "SESSION_CTX",
                "normal_outputs",
                mapOf(
                    "displayHz" to (if (displayHz != null) "%.1f".format(displayHz) else "?"),
                    "wantYuv" to wantYuv.toString(),
                    "yuvAttached" to (yuvImageReader != null).toString(),
                    "useHighSpeed" to "false",
                    "sessGen" to gen.toString(),
                    "suppressFace" to automationSuppressFacePipeline.toString(),
                ),
            )
            }
        }

        val chosenPreviewDr: Long? =
            if (!useHighSpeed) {
                val ch = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                if (ch == null) {
                    null
                } else {
                    PreviewHdrSessionSupport.pickProfileForPreviewOutputsOrNull(
                        device = camera,
                        chars = ch,
                        outputSurfaces = surfaces.toList(),
                        wantHdrPreview = readHudCapturePrefs().enableHdr10LivePreview,
                    )
                }
            } else {
                null
            }
        if (chosenPreviewDr != null) {
            PnsAdbLog.i(appContext, "previewSessionDynamicRange profile=$chosenPreviewDr")
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
                        PnsAdbLog.i(
                            appContext,
                            "superMacroCloseup probe cameraId=$camId vendorKeyApplied=false type=none path=sessionParameters",
                        )
                    }
                    if (sessionApplied != null) {
                        val macroKind = sessionApplied
                        PreviewAeAntibanding.applyToRequest(sessionReqBuilder, sessionChars)
                        VideoEffectsProcessor.applyToVideoPreviewRequest(
                            sessionReqBuilder,
                            sessionChars,
                            readHudCapturePrefs(),
                            previewFpsRange = pickNormalFpsRange(camId, previewTargetFpsForSession()),
                            manualSensor = false,
                        )
                        val sessionParams = sessionReqBuilder.build()
                        // Vendor session-parameter probing runs on [h] while the main thread may still be
                        // resizing the TextureView; OutputConfiguration queries BufferQueue dimensions and
                        // can throw IllegalArgumentException: Surface was abandoned. Recover by closing and
                        // scheduling [maybeRestartBody] after a short delay so layout can settle.
                        val outputConfigs =
                            runCatching {
                                outputConfigurationsWithOptionalStreamUseCases(
                                    surfaces,
                                    enableHints = false,
                                    previewDynamicRangeProfile = chosenPreviewDr,
                                )
                            }.getOrElse { e ->
                                Log.w(
                                    tag,
                                    "macro session create: OutputConfiguration failed " +
                                        "(${e.javaClass.simpleName}: ${e.message}); scheduling restart",
                                )
                                superMacroSessionConfigured = false
                                closeCamera()
                                h.postDelayed(
                                    { maybeRestartBody() },
                                    MACRO_OUTPUT_CONFIG_RETRY_DELAY_MS,
                                )
                                return
                            }
                        val executor: Executor = Executor { cmd -> h.post(cmd) }
                        val sessionConfig =
                            SessionConfiguration(
                                SessionConfiguration.SESSION_REGULAR,
                                outputConfigs,
                                executor,
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(sess: CameraCaptureSession) {
                                        try {
                                            if (gen != generation || device == null) {
                                                Log.w(tag, "onConfigured ignored (stale gen=$gen current=$generation)")
                                                recordCapturePipelineEvent(
                                                    "STALE_ON_CONFIGURED",
                                                    "macro_sessionParameters",
                                                    mapOf("gen" to "$gen", "currentGen" to "$generation"),
                                                )
                                                runCatching { sess.close() }
                                                return
                                            }
                                            superMacroSessionConfigured = true
                                            PnsAdbLog.i(
                                                appContext,
                                                "superMacroCloseup probe cameraId=$camId vendorKeyApplied=true type=$macroKind path=sessionParameters",
                                            )
                                            session = sess
                                            sessionCommittedGeneration = generation
                                            sessionPreviewDynamicRangeShort =
                                                chosenPreviewDr?.let { dr ->
                                                    PreviewDynamicRangeLabels.shortLabel(dr)
                                                }
                                            val fpsRange = pickNormalFpsRange(camId, previewTargetFpsForSession())
                                            startRepeating(sess, camera, surf, fpsRange = fpsRange, camId = camId)
                                        } finally {
                                            captureSessionAsyncConfigurePending = false
                                        }
                                    }

                                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                                        try {
                                            if (gen == generation) {
                                                PnsAdbLog.i(
                                                    appContext,
                                                    "superMacroCloseup probe cameraId=$camId vendorKeyApplied=false type=$macroKind path=sessionParametersConfigureFailed",
                                                )
                                                superMacroSessionConfigured = false
                                                recordCapturePipelineEvent(
                                                    "SESSION_CONFIGURE_FAILED",
                                                    "macro_sessionParameters",
                                                    mapOf("camId" to camId),
                                                    flushToFile = true,
                                                )
                                                handleCaptureSessionConfigureFailed(
                                                    sess,
                                                    gen,
                                                    "macro_sessionParameters",
                                                )
                                            }
                                        } finally {
                                            captureSessionAsyncConfigurePending = false
                                        }
                                    }
                                },
                            )
                        sessionConfig.setSessionParameters(sessionParams)
                        captureSessionAsyncConfigurePending = true
                        val macroCreate = runCatching { camera.createCaptureSession(sessionConfig) }
                        macroCreate.exceptionOrNull()?.let { e ->
                            captureSessionAsyncConfigurePending = false
                            Log.w(
                                tag,
                                "createCaptureSession(SessionConfiguration macro) threw ${e::class.java.simpleName}: ${e.message}",
                            )
                            superMacroSessionConfigured = false
                            recordCapturePipelineEvent(
                                "SESSION_CREATE_THROW",
                                e.message ?: e::class.java.simpleName,
                                mapOf("path" to "macro_sessionParameters", "camId" to camId),
                                flushToFile = true,
                            )
                            PnsAdbLog.i(
                                appContext,
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
            val sessionCallback =
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        try {
                            if (gen != generation || device == null) {
                                Log.w(tag, "onConfigured ignored (stale gen=$gen current=$generation)")
                                recordCapturePipelineEvent(
                                    "STALE_ON_CONFIGURED",
                                    "normal",
                                    mapOf("gen" to "$gen", "currentGen" to "$generation"),
                                )
                                runCatching { sess.close() }
                                return
                            }
                            session = sess
                            sessionCommittedGeneration = generation
                            // Clear video session rebuild flag - new session now has recording surface
                            videoRecordingSessionRebuildPending = false
                            val fpsRange = pickNormalFpsRange(camId, previewTargetFpsForSession())
                            startRepeating(sess, camera, surf, fpsRange = fpsRange, camId = camId)
                        } finally {
                            captureSessionAsyncConfigurePending = false
                        }
                    }

                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                        try {
                            if (gen == generation) {
                                recordCapturePipelineEvent(
                                    "SESSION_CONFIGURE_FAILED",
                                    "normal",
                                    mapOf("camId" to camId),
                                    flushToFile = true,
                                )
                                handleCaptureSessionConfigureFailed(sess, gen, "normal")
                            }
                        } finally {
                            captureSessionAsyncConfigurePending = false
                        }
                    }
                }
            // Bisect #4a: omit API 33+ OutputConfiguration.setStreamUseCase tags on REGULAR session
            // (restore: docs/REVERTED_FEATURES_RESTORE_LIST.md §4).
            val streamHints = false
            captureSessionAsyncConfigurePending = true
            val researchAfSession = buildVendorSessionParametersTemplate(camera, camId)
            val sessionCharsForPhysicalPin =
                runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            val logicalMultiCam =
                sessionCharsForPhysicalPin?.physicalCameraIds?.isNotEmpty() == true
            val previewPin = previewSurfacePhysicalCameraId?.takeIf { it.isNotBlank() }
            // Preview (output 0) only: pinning RAW/JPEG to the same physical id when the HAL leaves
            // [TotalCaptureResult.physicalCameraTotalResults] empty (CPH2655-class) still delivers
            // auxiliary-sensor pixels while [DngMetadataResolver] must fall back to logical metadata
            // for [DngCreator] — that buffer/metadata split decodes as dark / green DNG. Unpinned RAW
            // stays on the logical default route so tags match pixels; JPEG still decodes via ISP.
            val physicalPinnedSurfaceIndices: Set<Int>? = null
            if (logicalMultiCam && previewPin != null) {
                Log.d(
                    tag,
                    "logical multi-camera: preview-only physical pin id=$previewPin " +
                        "(RAW/JPEG outputs not pinned — DNG alignment vs empty physicalCameraTotalResults)",
                )
            }
            val createErr =
                createRegularCaptureSessionWithRetries(
                    camera,
                    surfaces,
                    h,
                    sessionCallback,
                    streamHints,
                    chosenPreviewDr,
                    researchAfSession,
                    previewSurfacePhysicalCameraId,
                    physicalPinnedSurfaceIndices,
                )
            createErr?.let { e ->
                captureSessionAsyncConfigurePending = false
                Log.w(tag, "createCaptureSession threw ${e::class.java.simpleName}: ${e.message}")
                lastStatus = "Session create aborted: ${e::class.java.simpleName}"
                recordCapturePipelineEvent(
                    "SESSION_CREATE_THROW",
                    e.message ?: e::class.java.simpleName,
                    mapOf("camId" to camId, "path" to "normal"),
                    flushToFile = true,
                )
                // Full teardown + generation bump (TextureView / am-start cold paths recover via
                // [maybeRestart] from layout callbacks).
                closeCamera()
            }
            return
        }

        val displayHzHfr =
            runCatching {
                val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
            }.getOrNull()
        PnsLog.i(
            tag,
            buildString {
                append("PNS.PreviewSessionCtx ")
                append("defaultDisplayHz=")
                append(if (displayHzHfr != null) "%.1f".format(displayHzHfr) else "?")
                append(" desiredFps=").append(desiredFps)
                append(" dial=").append(commandDialMode.name)
                append(" useHighSpeed=").append(true)
                append(" wantYuv=").append(false)
                append(" yuvAttached=").append(false)
                append(" suppressFacePipeline=").append(automationSuppressFacePipeline)
                append(" sessionGen=").append(gen)
            },
        )
        recordCapturePipelineEvent(
            "SESSION_CTX",
            "hfr_outputs",
            mapOf(
                "displayHz" to (if (displayHzHfr != null) "%.1f".format(displayHzHfr) else "?"),
                "wantYuv" to "false",
                "yuvAttached" to "false",
                "useHighSpeed" to "true",
                "sessGen" to gen.toString(),
                "suppressFace" to automationSuppressFacePipeline.toString(),
            ),
        )

        val hfrOutputs = hfrSessionOutputSurfaces(surfaces, surf)
        Log.d(
            tag,
            "Creating HFR session fps=$desiredFps size=${target.first.width}x${target.first.height} " +
                "range=${target.second} outputs=${hfrOutputs.size} mcHfrDual=" +
                "${videoController.wantsMediaCodecPath && videoController.isRecorderPresent() && hfrOutputs.size >= 2}",
        )
        // Constrained high-speed session — same TOCTOU protection as the normal path.
        captureSessionAsyncConfigurePending = true
        val forceEncoderSdr =
            inAppVideoRecordingArmed &&
                videoController.isRecorderPresent() &&
                videoController.wantsMediaCodecPath &&
                (hfrOutputs.size >= 2 || hfrEncoderOnlyRecordActive)
        val hfrResult = runCatching {
            camera.createCaptureSessionHighSpeedOutputs(
                hfrOutputs,
                h,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        try {
                            if (gen != generation || device == null) {
                                Log.w(tag, "HFR onConfigured ignored (stale gen=$gen current=$generation)")
                                recordCapturePipelineEvent(
                                    "STALE_ON_CONFIGURED",
                                    "hfr",
                                    mapOf("gen" to "$gen", "currentGen" to "$generation"),
                                )
                                runCatching { sess.close() }
                                return
                            }
                            session = sess
                            sessionCommittedGeneration = generation
                            sessionPreviewDynamicRangeShort = null
                            videoRecordingSessionRebuildPending = false
                            val fpsRange = target!!.second
                            // Start MediaCodec before HS repeating — otherwise GraphicBufferSource
                            // queues pre-start buffers that are flushed when the codec starts.
                            if (hfrEncoderOnlyRecordActive && inAppVideoRecordingArmed) {
                                startInAppVideoRecorderNow()
                            }
                            startRepeating(
                                sess,
                                camera,
                                surf,
                                fpsRange = fpsRange,
                                camId = camId,
                            )
                        } finally {
                            captureSessionAsyncConfigurePending = false
                        }
                    }

                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                        try {
                            if (gen == generation) {
                                recordCapturePipelineEvent(
                                    "SESSION_CONFIGURE_FAILED",
                                    "hfr",
                                    mapOf("camId" to camId),
                                    flushToFile = true,
                                )
                                handleCaptureSessionConfigureFailed(sess, gen, "hfr")
                            }
                        } finally {
                            captureSessionAsyncConfigurePending = false
                        }
                    }
                },
                forceEncoderOutputSdr = forceEncoderSdr,
            )
        }
        if (forceEncoderSdr) {
            Log.i(
                HfrInterleavedPreviewSupport.TAG,
                "HS session encoder output: STANDARD dynamic range (8-bit MediaCodec)",
            )
        }
        hfrResult.exceptionOrNull()?.let { e ->
            captureSessionAsyncConfigurePending = false
            Log.w(tag, "createCaptureSession (high-speed) threw ${e::class.java.simpleName}: ${e.message}")
            lastStatus = "HFR session create aborted: ${e::class.java.simpleName}"
            recordCapturePipelineEvent(
                "SESSION_CREATE_THROW",
                e.message ?: e::class.java.simpleName,
                mapOf("camId" to camId, "path" to "hfr"),
                flushToFile = true,
            )
            runCatching { camera.close() }
            device = null
        }
    }

    private fun wantsHighlightMetering(): Boolean =
        commandDialMode == CommandDialMode.H &&
            desiredFps < 120

    /**
     * AE compensation index for [CONTROL_AE_EXPOSURE_COMPENSATION] when YUV highlight metering is active;
     * null = leave compensation unset (same as Auto).
     */
    private fun aeHighlightCompensationValue(): Int? {
        if (!wantsHighlightMetering() || yuvImageReader == null) return null
        val camId = selectedCameraId ?: return null
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return null
        if (usesHardwareHighlightAe(chars)) return null
        // Omit the key until we apply a non-zero compensation nudge, or when neutral again
        // ([lastAppliedHighlightComp] null) so preview matches Auto without pinning EV comp at 0.
        return lastAppliedHighlightComp
    }

    private fun resetHighlightMeterPipelineState() {
        lastAppliedHighlightComp = null
        highlightMeterEvEma = Double.NaN
        highlightDarkenEngageEma = Double.NaN
        lastHighlightAeRefreshWallMs = 0L
    }

    /** Debounced repeating request refresh when Highlight (H) AE compensation changes. */
    private fun refreshRepeatingPreviewForHighlightMeter() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHighlightAeRefreshWallMs < highlightAeRefreshMinGapMs) return
        lastHighlightAeRefreshWallMs = now
        refreshRepeatingPreviewOnly()
    }

    /**
     * @return smoothed engagement in `[0, 1]` for multiplying negative [HighlightMeter.HighlightEvBreakdown.evCore].
     */
    private fun smoothHighlightDarkenEngagement(target: Double): Double {
        val t = target.coerceIn(0.0, 1.0)
        val prev = highlightDarkenEngageEma
        val next =
            if (prev.isNaN()) {
                t
            } else {
                val alpha = if (t < prev) HIGHLIGHT_ENGAGE_EMA_FALL_ALPHA else HIGHLIGHT_ENGAGE_EMA_RISE_ALPHA
                prev * (1.0 - alpha) + t * alpha
            }
        highlightDarkenEngageEma = next
        return next
    }

    private fun resolveLogicalActivePhysicalIdRequestKey(chars: CameraCharacteristics): CaptureRequest.Key<String>? {
        val keys = chars.availableCaptureRequestKeys ?: return null
        val exact = CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID.name
        @Suppress("UNCHECKED_CAST")
        keys.firstOrNull { it.name == exact }?.let { return it as CaptureRequest.Key<String> }
        @Suppress("UNCHECKED_CAST")
        keys.firstOrNull { key ->
            val n = key.name
            (n.contains("logicalMultiCamera", ignoreCase = true) &&
                n.contains("activePhysical", ignoreCase = true)) ||
                n.endsWith("activePhysicalId", ignoreCase = true)
        }?.let { return it as CaptureRequest.Key<String> }
        return null
    }

    /**
     * Heavy smoothing on negative EV so AE compensation does not hunt; positive EV is unused when
     * [highlightMeterDarkenOnly] (Highlight dial protects whites only).
     */
    private fun smoothHighlightMeterEv(raw: Double): Double {
        val prev = highlightMeterEvEma
        val next =
            if (prev.isNaN()) {
                raw
            } else if (raw <= 0.0) {
                prev * 0.78 + raw * 0.22
            } else {
                prev * 0.92 + raw * 0.08
            }
        highlightMeterEvEma = next
        return next
    }

    private fun buildPreviewCaptureRequestBuilder(
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
        camId: String,
    ): CaptureRequest.Builder {
        val template =
            when {
                inAppVideoRecordingArmed && videoController.isRecorderPresent() -> CameraDevice.TEMPLATE_RECORD
                fpsRange != null && fpsRange.lower >= 120 -> CameraDevice.TEMPLATE_RECORD
                else -> CameraDevice.TEMPLATE_PREVIEW
            }
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
        return camera.createCaptureRequest(template).apply {
            addTarget(surf)
            // Only add recording surface if session rebuild is complete
            // (videoRecordingSessionRebuildPending is set when prepared, cleared when session created)
            if (inAppVideoRecordingArmed &&
                !videoRecordingSessionRebuildPending &&
                !dualVideoActive &&
                !hfrEncoderOnlyRecordActive
            ) {
                val rec = videoController.getRecordingSurface()?.takeIf { it.isValid }
                if (rec != null && rec != surf) addTarget(rec)
            }
            yuvImageReader?.let { addTarget(it.surface) }
            if (rawVideoController.isRecording) {
                rawImageReader?.let { addTarget(it.surface) }
            }
            if (fpsRange != null) {
                val aeFps =
                    when {
                        hfrEncoderOnlyRecordActive ||
                            (
                                useHfrInterleavedMcPreview() && inAppVideoRecordingArmed &&
                                    videoController.isRecorderPresent() &&
                                    fpsRange.lower != fpsRange.upper
                                ) ->
                            Range(fpsRange.upper, fpsRange.upper)
                        else -> fpsRange
                    }
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, aeFps)
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
                applyMacroVendorCloseupOnRequest(this, chars, camId)
                applyReadoutManualExposureAndWb(this, chars, camId)
                PreviewFlashPolicy.applyPreviewFlashHardwareKeys(
                    this,
                    chars,
                    previewFlashMode,
                    commandDialMode,
                    manualIsoOverride != null || manualExposureNsOverride != null,
                    previewFlashStrengthPercent,
                )
                if (!loggedChromeUxFlashHardware) {
                    loggedChromeUxFlashHardware = true
                    val hw = PreviewFlashPolicy.flashHardwareAvailable(chars)
                    Log.i(
                        "PNS.ChromeUx",
                        if (hw) "flashPreviewHardware=true" else "flashPreviewHardware=false",
                    )
                }
                PreviewAeAntibanding.applyToRequest(this, chars)
                VideoEffectsProcessor.applyToVideoPreviewRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = fpsRange,
                    manualSensor = manualSensor,
                )
                PreviewAutoFraming.applyIfAvailable(this, chars, readHudCapturePrefs())
                PreviewJpegProcessingHints.applyToCaptureRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    skipColorCorrection = manualAwbAlreadySetsColorCorrection(),
                )
                if (wantsZslStillRing() &&
                    dev.pointandshoot.fleet.OnePlus13FleetPolicy.enableZslOnPreviewRepeating()
                ) {
                    PreviewStillCaptureHints.applyZslIfCompatible(
                        builder = this,
                        characteristics = chars,
                        wantZsl = true,
                        manualSensorStill = manualSensor,
                    )
                }
                val activePhys = previewSurfacePhysicalCameraId
                val physChildren =
                    runCatching { chars.physicalCameraIds?.toSet() }.getOrNull().orEmpty()
                if (activePhys != null && activePhys in physChildren) {
                    val activeKey = resolveLogicalActivePhysicalIdRequestKey(chars)
                    if (activeKey != null) {
                        val dedupe = "${generation}_${activePhys}_${activeKey.name}"
                        runCatching {
                            set(activeKey, activePhys)
                        }.fold(
                            onSuccess = {
                                if (dedupe != lastTeleRouteAdbKey) {
                                    lastTeleRouteAdbKey = dedupe
                                    PnsAdbLog.i(
                                        appContext,
                                        "teleRoute logical=$camId phys=$activePhys requestKey=${activeKey.name}",
                                    )
                                }
                            },
                            onFailure = { e ->
                                Log.w(
                                    tag,
                                    "activePhysicalId request (${activeKey.name})=$activePhys: ${e.message}",
                                )
                            },
                        )
                    } else if ("${generation}_${activePhys}_none" != lastTeleRouteAdbKey) {
                        lastTeleRouteAdbKey = "${generation}_${activePhys}_none"
                        PnsAdbLog.i(
                            appContext,
                            "teleRoute logical=$camId phys=$activePhys requestKey=none " +
                                "(no matching entry in availableCaptureRequestKeys)",
                        )
                    }
                }
            }
        }
    }

    private fun readHudCapturePrefs(): HudSettings {
        val base = HudSettings.load(appContext)
        if (!adbAutomationVideoStabilization) return base
        return base.copy(
            enableVideoStabilizationPreview = true,
            enableLensOpticalStabilization = true,
        )
    }

    private fun buildPreviewCaptureRequest(
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
        camId: String,
    ): CaptureRequest = buildPreviewCaptureRequestBuilder(camera, surf, fpsRange, camId).build()

    /**
     * One-shot [CameraCaptureSession.capture] with AF/AE precapture triggers after tap metering
     * regions update (Sprint 4.4). Skipped for high-speed constrained sessions.
     */
    private fun fireTapFocusAfAeTriggers() {
        val sess = session ?: return
        val cam = device ?: return
        val surf = previewSurface ?: return
        val camId = selectedCameraId ?: return
        val h = handler ?: return
        val constrained =
            runCatching {
                sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true)
            }.getOrDefault(false)
        if (constrained) return
        val fpsRange = pickNormalFpsRange(camId, previewTargetFpsForSession())
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val keys = chars.availableCaptureRequestKeys ?: return
        try {
            val b = buildPreviewCaptureRequestBuilder(cam, surf, fpsRange, camId)
            var any = false
            if (keys.contains(CaptureRequest.CONTROL_AF_TRIGGER)) {
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                any = true
            }
            if (keys.contains(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)) {
                b.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                )
                any = true
            }
            if (!any) return
            sess.capture(
                b.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        Log.d(tag, "tapFocus precapture triggers capture seq=${result.frameNumber}")
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        Log.w(tag, "tapFocus precapture triggers failed reason=${failure.reason}")
                    }
                },
                h,
            )
        } catch (e: CameraAccessException) {
            Log.w(tag, "tapFocus precapture: ${e.reason}")
        } catch (t: Throwable) {
            Log.w(tag, "tapFocus precapture: ${t.message}")
        }
    }

    private fun manualAwbAlreadySetsColorCorrection(): Boolean {
        val mode = manualAwbModeOverride ?: return false
        return mode != CaptureRequest.CONTROL_AWB_MODE_OFF
    }

    private fun wantsReadoutExposureChase(): Boolean {
        if (desiredFps >= 120) return false
        return when (
            ReadoutAeCoupling.fromOverrides(manualIsoOverride, manualExposureNsOverride)
        ) {
            ReadoutAeCoupling.LOCKED_ISO_AUTO_SS,
            ReadoutAeCoupling.LOCKED_SS_AUTO_ISO,
            -> true
            else -> false
        }
    }

    private fun maybeAdjustReadoutChaseFromHistogram(hist: IntArray) {
        if (!wantsReadoutExposureChase()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadoutChaseProcessWallMs < readoutChaseHistMinIntervalMs) return
        lastReadoutChaseProcessWallMs = now
        val coupling =
            ReadoutAeCoupling.fromOverrides(manualIsoOverride, manualExposureNsOverride)
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val sampleBin = ReadoutExposureChase.medianBin(hist)
        readoutChaseMedianEma = ReadoutExposureChase.smoothMedian(readoutChaseMedianEma, sampleBin)
        val medianEma = readoutChaseMedianEma
        var applied = false
        when (coupling) {
            ReadoutAeCoupling.LOCKED_ISO_AUTO_SS -> {
                val cur =
                    readoutChaseExposureNs
                        ?: manualExposureNsOverride
                        ?: previewMetadata.get().exposureNs
                        ?: expRange?.lower
                        ?: 33_333_333L
                readoutChaseExposureNs = cur
                val res = ReadoutExposureChase.adjustExposureNs(cur, medianEma, expRange)
                if (res.applied) {
                    readoutChaseExposureNs = res.value
                    applied = true
                }
            }
            ReadoutAeCoupling.LOCKED_SS_AUTO_ISO -> {
                val cur =
                    readoutChaseIso
                        ?: manualIsoOverride
                        ?: previewMetadata.get().iso
                        ?: isoRange?.lower
                        ?: 100
                readoutChaseIso = cur
                val res =
                    ReadoutExposureChase.adjustIso(cur, medianEma, isoRange, readoutIsoBand)
                if (res.applied) {
                    readoutChaseIso = res.value
                    applied = true
                }
            }
            else -> return
        }
        val logEveryMs = if (adbReadoutChaseProof) 1000L else 3000L
        if (now - lastReadoutChaseLogWallMs >= logEveryMs) {
            lastReadoutChaseLogWallMs = now
            Log.i(
                tag,
                "readoutChase iso=${readoutChaseIso} ss=${readoutChaseExposureNs} " +
                    "coupling=$coupling medianEma=${"%.1f".format(medianEma)}",
            )
        }
        if (applied && now - lastReadoutChaseRefreshWallMs >= readoutChaseRefreshMinGapMs) {
            lastReadoutChaseRefreshWallMs = now
            handler?.post { refreshRepeatingPreviewOnly() }
        }
    }

    private fun applyReadoutManualExposureAndWb(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
    ) {
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val coupling =
            ReadoutAeCoupling.fromOverrides(manualIsoOverride, manualExposureNsOverride)
        if (coupling != ReadoutAeCoupling.AUTO) {
            if (!aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)) {
                Log.w(tag, "Readout manual ISO/shutter unavailable: no CONTROL_AE_MODE_OFF (AWB still applied)")
            } else {
                req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                when (coupling) {
                    ReadoutAeCoupling.LOCKED_ISO_AUTO_SS -> {
                        val isoPick =
                            manualIsoOverride ?: previewMetadata.get().iso ?: isoRange?.lower ?: 100
                        req.set(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            ReadoutExposureCatalog.clampIso(isoRange, isoPick),
                        )
                        val expPick =
                            readoutChaseExposureNs
                                ?: previewMetadata.get().exposureNs
                                ?: expRange?.lower
                                ?: 33_333_333L
                        req.set(
                            CaptureRequest.SENSOR_EXPOSURE_TIME,
                            ReadoutExposureCatalog.clampExposure(expRange, expPick),
                        )
                    }
                    ReadoutAeCoupling.LOCKED_SS_AUTO_ISO -> {
                        val isoPick =
                            readoutChaseIso
                                ?: previewMetadata.get().iso
                                ?: isoRange?.lower
                                ?: 100
                        req.set(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            ReadoutExposureCatalog.clampIso(isoRange, isoPick),
                        )
                        val expPick =
                            manualExposureNsOverride
                                ?: previewMetadata.get().exposureNs
                                ?: expRange?.lower
                                ?: 33_333_333L
                        req.set(
                            CaptureRequest.SENSOR_EXPOSURE_TIME,
                            ReadoutExposureCatalog.clampExposure(expRange, expPick),
                        )
                    }
                    ReadoutAeCoupling.MANUAL_BOTH -> {
                        val isoPick =
                            manualIsoOverride ?: previewMetadata.get().iso ?: isoRange?.lower ?: 100
                        req.set(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            ReadoutExposureCatalog.clampIso(isoRange, isoPick),
                        )
                        val expPick =
                            manualExposureNsOverride
                                ?: previewMetadata.get().exposureNs
                                ?: expRange?.lower
                                ?: 33_333_333L
                        req.set(
                            CaptureRequest.SENSOR_EXPOSURE_TIME,
                            ReadoutExposureCatalog.clampExposure(expRange, expPick),
                        )
                    }
                    ReadoutAeCoupling.AUTO -> Unit
                }
            }
        }
        manualAwbModeOverride?.let { mode ->
            val awbAvail = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
            if (awbAvail.contains(mode)) {
                val controlModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES) ?: intArrayOf()
                if (controlModes.contains(CaptureRequest.CONTROL_MODE_AUTO)) {
                    req.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                }
                req.set(CaptureRequest.CONTROL_AWB_MODE, mode)
                if (mode != CaptureRequest.CONTROL_AWB_MODE_OFF) {
                    val ccAvail =
                        chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) ?: intArrayOf()
                    when {
                        ccAvail.contains(CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY) ->
                            req.set(
                                CaptureRequest.COLOR_CORRECTION_MODE,
                                CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY,
                            )
                        ccAvail.contains(CaptureRequest.COLOR_CORRECTION_MODE_FAST) ->
                            req.set(
                                CaptureRequest.COLOR_CORRECTION_MODE,
                                CaptureRequest.COLOR_CORRECTION_MODE_FAST,
                            )
                    }
                }
                val maxAwb =
                    (chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) as? IntArray)
                        ?.firstOrNull()
                        ?: 0
                if (maxAwb > 0) {
                    val useDigitalCrop = focalCropMode != null && desiredFps < 120
                    val modeForCrop = if (useDigitalCrop) focalCropMode else null
                    val crop = scalerCropRectForSession(chars, camId, modeForCrop)
                    val cw = crop.width()
                    val ch = crop.height()
                    if (cw > 0 && ch > 0) {
                        req.set(
                            CaptureRequest.CONTROL_AWB_REGIONS,
                            arrayOf(
                                MeteringRectangle(
                                    crop.left,
                                    crop.top,
                                    cw,
                                    ch,
                                    MeteringRectangle.METERING_WEIGHT_MAX,
                                ),
                            ),
                        )
                    }
                }
            } else {
                Log.w(
                    tag,
                    "Readout AWB mode=$mode not in CONTROL_AWB_AVAILABLE_MODES=${awbAvail.contentToString()}",
                )
            }
        }
    }

    /**
     * OPLUS close-up macro on ultra-wide when [wantsMacroProgram] or [superMacroAdbProbe].
     */
    private fun applyMacroVendorCloseupOnRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
    ) {
        if (!wantsMacroProgram() && !superMacroAdbProbe) return
        val uw =
            runCatching {
                BackCameraRoleResolver.resolve(cm, cameraIds()).ultraWide
            }.getOrNull()
        if (uw != camId) {
            if (superMacroAdbProbe && !loggedSuperMacroProbeWrongCam) {
                loggedSuperMacroProbeWrongCam = true
                PnsAdbLog.i(
                    appContext,
                    "superMacroCloseup skipped cameraId=$camId (ultraWide=$uw); use pns_preview_camera_id on UW",
                )
            }
            return
        }
        if (superMacroAdbProbe) {
            if (superMacroSessionConfigured) return
            if (loggedSuperMacroProbeUw) return
            loggedSuperMacroProbeUw = true
        }
        val macroName = HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST
        val appliedKind =
            VendorKeyGuard.trySetVendorRequestEnable(
                req,
                chars,
                macroName,
            )
        if (wantsMacroProgram()) {
            Log.i(
                "PNS.ChromeUx",
                "macroMode vendorCloseup cameraId=$camId applied=${appliedKind != null}",
            )
        }
        if (superMacroAdbProbe) {
            val lookup = VendorKeyGuard.captureRequestKey(chars, macroName)
            val reqAvail = VendorKeyGuard.isRequestKeyAvailable(chars, macroName)
            val sessAvail = VendorKeyGuard.isSessionKeyAvailable(chars, macroName)
            PnsAdbLog.i(
                appContext,
                "superMacroCloseup keyLookup requestKeyObject=${lookup != null} requestEnum=$reqAvail sessionEnum=$sessAvail",
            )
            PnsAdbLog.i(
                appContext,
                "superMacroCloseup probe cameraId=$camId vendorKeyApplied=${appliedKind != null} type=${appliedKind ?: "none"}",
            )
        }
    }

    private fun clearFaceHudOverlayState() {
        faceHudLastEyes = emptyList()
        faceHudLastCameraFaceBoxes = emptyList()
        faceHudMlRawBoxes = emptyList()
        faceHudMlRawEyes = emptyList()
        faceHudMlSmoothedMeteringPrimary = null
        mlFaceBoxSmoother.clear()
        facePriorityMeteringRect = null
        lastFaceMeteringSig = Int.MIN_VALUE
        mainHandler.post {
            faceHudOverlayListener?.invoke(FaceHudOverlayState(emptyList(), emptyList()))
        }
        refreshRepeatingPreviewOnly()
    }

    private fun faceBoxAreaBuffer(b: FaceTrackBoxBuffer): Float {
        val w = (b.right - b.left).coerceAtLeast(0f)
        val h = (b.bottom - b.top).coerceAtLeast(0f)
        return w * h
    }

    /** Single subject for HUD + AF: largest face in buffer space (typical main portrait). */
    private fun pickPrimaryFaceBox(boxes: List<FaceTrackBoxBuffer>): FaceTrackBoxBuffer? =
        boxes.maxByOrNull { faceBoxAreaBuffer(it) }

    private fun eyeMarksInsideAnyFaceBox(
        eyes: List<EyeMark>,
        boxes: List<FaceTrackBoxBuffer>,
    ): List<EyeMark> {
        if (eyes.isEmpty()) return emptyList()
        if (boxes.isEmpty()) return eyes
        return eyes.filter { e ->
            val x = e.position.x
            val y = e.position.y
            boxes.any { box ->
                val span = kotlin.math.max(box.right - box.left, box.bottom - box.top)
                val pad = (span * 0.18f).coerceAtLeast(28f)
                x >= box.left - pad &&
                    x <= box.right + pad &&
                    y >= box.top - pad &&
                    y <= box.bottom + pad
            }
        }
    }

    private fun dispatchFaceHudOverlay() {
        val cameraMode = faceHudLastCameraFaceBoxes.isNotEmpty()
        val rawBoxes =
            if (cameraMode) {
                faceHudLastCameraFaceBoxes
            } else {
                faceHudMlRawBoxes
            }
        val eyesSource =
            if (cameraMode) {
                faceHudLastEyes
            } else {
                faceHudMlRawEyes
            }
        val eyesForHud = eyeMarksInsideAnyFaceBox(eyesSource, rawBoxes)
        val boxesForHud = rawBoxes
        val meteringPrimary =
            if (cameraMode) {
                pickPrimaryFaceBox(faceHudLastCameraFaceBoxes)
            } else {
                faceHudMlSmoothedMeteringPrimary ?: pickPrimaryFaceBox(faceHudMlRawBoxes)
            }
        scheduleFacePriorityMeteringSync(meteringPrimary)
        val boxesForOverlay = if (eyesForHud.isNotEmpty()) emptyList() else boxesForHud
        mainHandler.post {
            faceHudOverlayListener?.invoke(FaceHudOverlayState(eyesForHud, boxesForOverlay))
        }
    }

    private fun faceMeteringSignature(m: MeteringRectangle): Int {
        val qx = m.x / 6
        val qy = m.y / 6
        val qw = m.width / 10
        val qh = m.height / 10
        var h = 17
        h = 31 * h + qx
        h = 31 * h + qy
        h = 31 * h + qw
        h = 31 * h + qh
        return h
    }

    /** Drive CAF/AE-weighted regions toward the primary face (Auto / H / BKT); tap always wins. */
    private fun allowsFacePriorityMetering(): Boolean {
        if (wantsManualFocusDistance()) return false
        return when (commandDialMode) {
            CommandDialMode.Auto, CommandDialMode.H, CommandDialMode.BKT -> true
            CommandDialMode.M, CommandDialMode.S,
            CommandDialMode.Macro, CommandDialMode.Night, CommandDialMode.Bokeh,
            CommandDialMode.Qr, CommandDialMode.Dual,
            -> false
        }
    }

    private fun scheduleFacePriorityMeteringSync(primary: FaceTrackBoxBuffer?) {
        ensureThread()
        val h = handler ?: return
        if (android.os.Looper.myLooper() == h.looper) {
            syncFacePriorityMeteringBody(primary)
        } else {
            h.post { syncFacePriorityMeteringBody(primary) }
        }
    }

    private fun syncFacePriorityMeteringBody(primary: FaceTrackBoxBuffer?) {
        if (!hudFaceOverlayEnabled || automationSuppressFacePipeline || !allowsFacePriorityMetering()) {
            if (facePriorityMeteringRect != null) {
                facePriorityMeteringRect = null
                lastFaceMeteringSig = Int.MIN_VALUE
                refreshRepeatingPreviewOnlyBody()
            }
            return
        }
        if (tapMeteringRect != null) {
            if (facePriorityMeteringRect != null) {
                facePriorityMeteringRect = null
                lastFaceMeteringSig = Int.MIN_VALUE
                refreshRepeatingPreviewOnlyBody()
            }
            return
        }
        if (primary == null) {
            if (facePriorityMeteringRect != null) {
                facePriorityMeteringRect = null
                lastFaceMeteringSig = Int.MIN_VALUE
                refreshRepeatingPreviewOnlyBody()
            }
            return
        }
        val buf = desiredSurfaceSize ?: currentSurfaceSize
        val camId = selectedCameraId
        if (buf == null || buf.width <= 0 || buf.height <= 0 || camId == null) return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val rect =
            meteringRectangleFromBufferFaceBox(primary, buf.width, buf.height, chars, camId, active)
                ?: return
        val sig = faceMeteringSignature(rect)
        if (sig == lastFaceMeteringSig) return
        lastFaceMeteringSig = sig
        facePriorityMeteringRect = rect
        refreshRepeatingPreviewOnlyBody()
    }

    private fun meteringRectangleFromBufferFaceBox(
        box: FaceTrackBoxBuffer,
        bufW: Int,
        bufH: Int,
        chars: CameraCharacteristics,
        camId: String,
        active: Rect,
    ): MeteringRectangle? {
        if (bufW <= 0 || bufH <= 0) return null
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val crop = scalerCropRectForSession(chars, camId, modeForCrop)
        if (crop.width() <= 0 || crop.height() <= 0) return null

        fun nx(x: Float) = (x / bufW.toFloat()).coerceIn(0f, 1f)
        fun ny(y: Float) = (y / bufH.toFloat()).coerceIn(0f, 1f)
        var leftF = crop.left + nx(box.left) * crop.width()
        var rightF = crop.left + nx(box.right) * crop.width()
        var topF = crop.top + ny(box.top) * crop.height()
        var bottomF = crop.top + ny(box.bottom) * crop.height()
        if (leftF > rightF) {
            val t = leftF
            leftF = rightF
            rightF = t
        }
        if (topF > bottomF) {
            val t = topF
            topF = bottomF
            bottomF = t
        }
        var l = leftF.toInt()
        var t = topF.toInt()
        var r = kotlin.math.max(l + 1, rightF.toInt())
        var b = kotlin.math.max(t + 1, bottomF.toInt())
        var w = r - l
        var h = b - t
        val minSide =
            (kotlin.math.min(crop.width(), crop.height()) * 0.08f)
                .toInt()
                .coerceIn(48, 720)
        if (w < minSide) {
            val pad = (minSide - w) / 2
            l -= pad
            w = minSide
        }
        if (h < minSide) {
            val pad = (minSide - h) / 2
            t -= pad
            h = minSide
        }
        if (l < active.left) l = active.left
        if (t < active.top) t = active.top
        if (l + w > active.right) l = (active.right - w).coerceAtLeast(active.left)
        if (t + h > active.bottom) t = (active.bottom - h).coerceAtLeast(active.top)
        w = kotlin.math.min(w, active.right - l)
        h = kotlin.math.min(h, active.bottom - t)
        if (w < 1 || h < 1 || l + w > active.right || t + h > active.bottom) return null
        return MeteringRectangle(l, t, w, h, MeteringRectangle.METERING_WEIGHT_MAX)
    }

    private fun publishFaceHud(eyes: List<EyeMark>, cameraFaceBoxes: List<FaceTrackBoxBuffer>) {
        faceHudLastEyes = eyes
        faceHudLastCameraFaceBoxes = cameraFaceBoxes
        if (cameraFaceBoxes.isNotEmpty()) {
            mlFaceBoxSmoother.clear()
            faceHudMlRawBoxes = emptyList()
            faceHudMlRawEyes = emptyList()
            faceHudMlSmoothedMeteringPrimary = null
        }
        dispatchFaceHudOverlay()
    }

    private fun publishMlFaceHud(detections: MlFaceHudDetections) {
        faceHudMlRawBoxes = detections.boxes
        faceHudMlRawEyes = detections.eyeMarks
        val smoothed = mlFaceBoxSmoother.update(detections.boxes)
        faceHudMlSmoothedMeteringPrimary = smoothed.firstOrNull()
        dispatchFaceHudOverlay()
    }

    private fun currentDisplaySurfaceRotation(): Int {
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: @Suppress("DEPRECATION")
            (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

    private fun defaultDisplayRotationDegrees(): Int {
        // Do not use Context.getDisplay() / appContext.display from background threads: Application
        // context is not a "visual" context on API 30+ (throws UnsupportedOperationException).
        return when (currentDisplaySurfaceRotation()) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    /** [MediaRecorder.setOrientationHint] / [MediaMuxer.setOrientationHint] for in-app video. */
    private fun videoOrientationHintDegrees(camId: String): Int {
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return 0
        return RawCaptureSupport.orientationClockwiseDegForDng(chars, currentDisplaySurfaceRotation())
    }

    private fun mlInputImageRotationDegrees(chars: CameraCharacteristics): Int {
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val deviceRotation = defaultDisplayRotationDegrees()
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation + deviceRotation) % 360
        } else {
            (sensorOrientation - deviceRotation + 360) % 360
        }
    }

    /** Maps [Face] bounds (active-array space) into preview-buffer pixels for [FaceTrackBoxBuffer]. */
    private fun faceBoundsToBufferRect(
        bounds: Rect,
        cropRect: Rect,
        bufW: Int,
        bufH: Int,
        mirror: Boolean,
    ): FaceTrackBoxBuffer? {
        val corners =
            arrayOf(
                bounds.left to bounds.top,
                bounds.right to bounds.top,
                bounds.right to bounds.bottom,
                bounds.left to bounds.bottom,
            )
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        var i = 0
        for ((x, y) in corners) {
            val o =
                PreviewBufferCoordMap.activeArrayToPreviewBuffer(
                    x,
                    y,
                    cropRect,
                    bufW,
                    bufH,
                    mirror,
                )
            xs[i] = o.x
            ys[i] = o.y
            i++
        }
        val left = xs.minOrNull() ?: return null
        val right = xs.maxOrNull() ?: return null
        val top = ys.minOrNull() ?: return null
        val bottom = ys.maxOrNull() ?: return null
        if (right - left < 6f || bottom - top < 6f) return null
        return FaceTrackBoxBuffer(left, top, right, bottom, trackingLocked = false)
    }

    private fun pickFaceDetectMode(chars: CameraCharacteristics): Int =
        StillCaptureFaceDetectParity.pickMode(chars)

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
            PnsAdbLog.i(
                appContext,
                "eyeAf faceDetectMode=$mode availableModes=[$avail]",
            )
        }
        req.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
    }

    private fun processFaceStatistics(result: CaptureResult, isFinal: Boolean) {
        if (automationSuppressFacePipeline || !hudFaceOverlayEnabled) return
        if (!loggedAdbTrackerPipelineReady) {
            loggedAdbTrackerPipelineReady = true
            PnsAdbLog.i(
                appContext,
                "tracker statisticsPipeline active (metadata wired to TrackerState)",
            )
        }
        val rawFaces = result.get(CaptureResult.STATISTICS_FACES)
        val faces =
            rawFaces
                ?.asSequence()
                ?.filter { f ->
                    val b = f.bounds
                    b.width() > 0 && b.height() > 0
                }
                ?.toList()
                .orEmpty()
        if (faces.isEmpty()) {
            if (!isFinal) return
            if (result.frameNumber == lastPartialFacesFrameNumber) {
                return
            }
            faceTracker.update(emptySet())
            publishFaceHud(emptyList(), emptyList())
            return
        }
        if (!isFinal) {
            lastPartialFacesFrameNumber = result.frameNumber
        } else {
            lastPartialFacesFrameNumber = -1L
        }
        val observed = FaceTrackingSupport.observedIds(faces.toTypedArray())
        val snap = faceTracker.update(observed)
        if (!loggedFaceStatisticsSample) {
            loggedFaceStatisticsSample = true
            PnsAdbLog.i(
                appContext,
                "eyeAf statisticsSample faces=${faces.size} lockedTrackIds=${snap.locked.joinToString()}",
            )
        }
        if (snap.locked != lastTrackerLockedLogged) {
            lastTrackerLockedLogged = snap.locked.toSet()
            PnsAdbLog.i(
                appContext,
                "tracker lockedIds=${snap.locked.joinToString()} transientCount=${snap.transient.size}",
            )
        }
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val reportedPhys =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        val previewPhysHint =
            previewSurfacePhysicalCameraId?.takeIf { pid ->
                runCatching { chars.physicalCameraIds?.contains(pid) == true }.getOrDefault(false)
            }
        val physForGeometry = reportedPhys ?: previewPhysHint
        val charsForFaceGeometry =
            if (physForGeometry != null) {
                runCatching { cm.getCameraCharacteristics(physForGeometry) }.getOrNull()
            } else {
                null
            } ?: chars
        val activeLogical = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val activeForLandmarks =
            charsForFaceGeometry.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: activeLogical
        val bufSize = desiredSurfaceSize ?: currentSurfaceSize ?: return
        val bufW = bufSize.width
        val bufH = bufSize.height
        if (bufW <= 0 || bufH <= 0) return

        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val cropFromResult = result.get(CaptureResult.SCALER_CROP_REGION)
        val cropRect =
            if (cropFromResult != null && cropFromResult.width() > 0 && cropFromResult.height() > 0) {
                cropFromResult
            } else {
                scalerCropRectForSession(chars, camId, modeForCrop)
            }
        if (cropRect.width() <= 0 || cropRect.height() <= 0) return

        // Ignore tiny HAL crop deltas; meaningful digital zoom / crop uses linear buffer mapping.
        val cropTighterTolPx = 8
        val cropIsTighterThanActive =
            cropRect.width() < activeLogical.width() - cropTighterTolPx ||
                cropRect.height() < activeLogical.height() - cropTighterTolPx

        val lensFacing = charsForFaceGeometry.get(CameraCharacteristics.LENS_FACING)
        val mirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        val aw = activeForLandmarks.width()
        val ah = activeForLandmarks.height()
        val faceDiagWall = SystemClock.elapsedRealtime()
        if (faceDiagWall - lastFaceGeometryDiagLogWallMs >= 3000L) {
            lastFaceGeometryDiagLogWallMs = faceDiagWall
            Log.i(
                "PNS.FaceAlign",
                "faceDiag cam=$camId phys=$physForGeometry buf=${bufW}x$bufH " +
                    "active=${aw}x$ah crop=${cropRect.width()}x${cropRect.height()} " +
                    "cropTight=$cropIsTighterThanActive map=cropLinear fps=$desiredFps",
            )
        }
        val marks = ArrayList<EyeMark>(faces.size * 2)
        val faceBoxBuffers = ArrayList<FaceTrackBoxBuffer>(faces.size)
        for (face in faces) {
            val faceId = FaceTrackingSupport.stableFaceId(face)
            val locked = faceId in snap.locked
            val sc = face.score.coerceIn(1, 100) / 100f
            val left: Point? = face.leftEyePosition
            val right: Point? = face.rightEyePosition
            if (left != null) {
                val p =
                    PreviewBufferCoordMap.activeArrayToPreviewBuffer(
                        left.x,
                        left.y,
                        cropRect,
                        bufW,
                        bufH,
                        mirror,
                    )
                marks.add(
                    EyeMark(position = p, confidence = sc, trackingLocked = locked),
                )
            }
            if (right != null) {
                val p =
                    PreviewBufferCoordMap.activeArrayToPreviewBuffer(
                        right.x,
                        right.y,
                        cropRect,
                        bufW,
                        bufH,
                        mirror,
                    )
                marks.add(
                    EyeMark(position = p, confidence = sc, trackingLocked = locked),
                )
            }
            if (left == null && right == null) {
                val b = face.bounds
                val ax = (b.left + b.right) / 2
                val ay = b.top + ((b.bottom - b.top) / 3)
                val p =
                    PreviewBufferCoordMap.activeArrayToPreviewBuffer(
                        ax,
                        ay,
                        cropRect,
                        bufW,
                        bufH,
                        mirror,
                    )
                marks.add(
                    EyeMark(position = p, confidence = sc * 0.5f, trackingLocked = locked),
                )
            }
            faceBoundsToBufferRect(
                face.bounds,
                cropRect,
                bufW,
                bufH,
                mirror,
            )
                ?.copy(trackingLocked = locked)
                ?.let { faceBoxBuffers.add(it) }
        }
        publishFaceHud(marks, faceBoxBuffers)
    }

    /**
     * Maps a tap in [TextureView] pixel space to AE/AF metering regions and reapplies the
     * repeating request. Preview transform uses `uiTwistDegrees = 0` (see [LutCameraPreviewRenderer] UV mapping);
     * [uiTwistDegrees] is accepted for API symmetry with overlay chrome rotation only.
     */
    fun applyTapFocusFromView(
        viewX: Float,
        viewY: Float,
        viewW: Int,
        viewH: Int,
        @Suppress("UNUSED_PARAMETER") uiTwistDegrees: Float,
        surfaceTransformColumnMajor4x4: FloatArray? = null,
    ) {
        val buf = desiredSurfaceSize ?: currentSurfaceSize ?: return
        if (viewW <= 0 || viewH <= 0 || buf.width <= 0 || buf.height <= 0) return
        val (bx, by) =
            if (surfaceTransformColumnMajor4x4 != null && surfaceTransformColumnMajor4x4.size >= 16) {
                TexturePreviewFit.mapViewToBufferWithExternalOesPreview(
                    viewX,
                    viewY,
                    viewW,
                    viewH,
                    buf.width,
                    buf.height,
                    coverCrop = PREVIEW_FINDER_CONTAIN,
                    surfaceTransformColumnMajor4x4,
                )
            } else {
                TexturePreviewFit.mapViewToBuffer(
                    viewX,
                    viewY,
                    viewW,
                    viewH,
                    buf.width,
                    buf.height,
                    coverCrop = PREVIEW_FINDER_CONTAIN,
                )
            }
        val camId = selectedCameraId ?: return
        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull() ?: return
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val cropRect = scalerCropRectForSession(chars, camId, modeForCrop)
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
        facePriorityMeteringRect = null
        lastFaceMeteringSig = Int.MIN_VALUE
        Log.d(tag, "tap focus buffer=(${bx.toInt()},${by.toInt()}) metering=$left,$top ${w}x$h")
        refreshRepeatingPreviewOnly()
        handler?.post { fireTapFocusAfAeTriggers() }
    }

    /**
     * Rebuild the repeating request without tearing down the session (face overlay / AE comp /
     * readout ISO–Ss–WB). Must run on [handler] so HAL applies changes immediately to the preview
     * surface (same contract as [maybeRestart]).
     */
    private fun refreshRepeatingPreviewOnly() {
        ensureThread()
        val h = handler ?: return
        if (Looper.myLooper() == h.looper) {
            refreshRepeatingPreviewOnlyBody()
        } else {
            h.post { refreshRepeatingPreviewOnlyBody() }
        }
    }

    private fun refreshRepeatingPreviewOnlyBody() {
        val sess = session ?: return
        val cam = device ?: return
        val surf = previewSurface ?: return
        val camId = selectedCameraId ?: return
        val h = handler ?: return
        try {
            val constrained =
                runCatching {
                    sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true)
                }.getOrDefault(false)
            val fpsRange =
                if (constrained) {
                    val map =
                        runCatching {
                            cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        }.getOrNull()
                    pickHighSpeedTarget(map, desiredFps)?.second
                } else {
                    pickNormalFpsRange(camId, previewTargetFpsForSession())
                }
            if (constrained) {
                if (fpsRange == null) {
                    Log.w(
                        tag,
                        "refreshRepeatingPreviewOnly: constrained session but no high-speed fps range (desiredFps=$desiredFps)",
                    )
                    return
                }
                val req = buildPreviewCaptureRequest(cam, surf, fpsRange, camId)
                val hs = sess as? android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
                val list = hs?.createHighSpeedRequestList(req)
                if (list != null) {
                    runCatching { sess.stopRepeating() }
                        .exceptionOrNull()
                        ?.let { Log.w(tag, "refreshRepeatingPreviewOnly stopRepeating: ${it.message}") }
                    sess.setRepeatingBurst(list, fpsMeasuringCallback(), h)
                } else {
                    Log.w(tag, "refreshRepeatingPreviewOnly: createHighSpeedRequestList returned null")
                }
                return
            }
            val req = buildPreviewCaptureRequest(cam, surf, fpsRange, camId)
            runCatching { sess.stopRepeating() }
                .exceptionOrNull()
                ?.let { Log.w(tag, "refreshRepeatingPreviewOnly stopRepeating: ${it.message}") }
            sess.setRepeatingRequest(req, fpsMeasuringCallback(), h)
        } catch (e: CameraAccessException) {
            Log.w(tag, "refreshRepeatingPreviewOnly failed: ${e.reason}")
        } catch (t: Throwable) {
            Log.w(tag, "refreshRepeatingPreviewOnly failed: ${t.message}")
        }
    }

    /**
     * After [CameraCaptureSession.stopRepeating] for a RAW still, restarts the preview stream
     * (same wiring as [refreshRepeatingPreviewOnlyBody]). Must run on [handler].
     */
    private fun resumePreviewRepeatingIfPossible() {
        ensureThread()
        refreshRepeatingPreviewOnlyBody()
    }

    private fun processYuvForHighlight(reader: ImageReader) {
        if (yuvImageReader == null) {
            reader.acquireLatestImage()?.close()
            return
        }
        val wantHighlight = wantsHighlightMetering()
        val wantHist = previewHistogramEnabled
        val wantZebra = highlightClipZebraEnabled && desiredFps < 120
        val wantChase = wantsReadoutExposureChase()
        val wantFaceHud = hudFaceOverlayEnabled && !automationSuppressFacePipeline
        val wantSmileStill = smileStillEnabled && !automationSuppressFacePipeline
        val wantFace = wantFaceHud || wantSmileStill
        val wantQr = wantsQrScan()
        if (!wantHighlight && !wantHist && !wantFace && !wantZebra && !wantQr && !wantChase) {
            reader.acquireLatestImage()?.close()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val histGapMs =
            maxOf(
                if (wantHighlight) highlightMeterMinIntervalMs else 0L,
                if (wantHist) histogramUiMinIntervalMs else 0L,
                if (wantZebra) zebraMinIntervalMs else 0L,
                if (wantChase) readoutChaseHistMinIntervalMs else 0L,
            )
        val histOk =
            (!wantHighlight && !wantHist && !wantZebra && !wantChase) ||
                (now - lastHighlightProcessWallMs >= histGapMs)
        val skipMlBecauseCameraFaces =
            wantFaceHud && faceHudLastCameraFaceBoxes.isNotEmpty()
        val mlIntervalMs =
            when {
                skipMlBecauseCameraFaces -> 0L
                mlConsecutiveEmptyMlDetections >= mlFaceEmptyBackoffAfterFrames ->
                    mlFaceEmptyBackoffIntervalMs
                else -> mlFaceMinIntervalMs
            }
        val faceOk =
            !wantFace ||
                skipMlBecauseCameraFaces ||
                (now - lastMlFaceProcessWallMs >= mlIntervalMs)
        val qrOk =
            !wantQr ||
                (now - lastQrDecodeWallMs >= QR_SCAN_DECODE_MIN_INTERVAL_MS)
        if (!histOk && !faceOk && !qrOk) {
            reader.acquireLatestImage()?.close()
            return
        }
        if (histOk && (wantHighlight || wantHist || wantZebra || wantChase)) {
            lastHighlightProcessWallMs = now
        }
        if (!yuvAnalysisInFlight.compareAndSet(false, true)) {
            reader.acquireLatestImage()?.close()
            return
        }
        val image = reader.acquireLatestImage()
        if (image == null) {
            yuvAnalysisInFlight.set(false)
            return
        }
        meterExecutor.execute {
            try {
                val prioritizeHMetering =
                    commandDialMode == CommandDialMode.H && (wantHighlight || wantHist || wantZebra)
                val skipCameraFacesForMlHud =
                    wantFaceHud && faceHudLastCameraFaceBoxes.isNotEmpty()

                fun runSmileStillIfNeeded() {
                    if (!wantSmileStill) return
                    val nowSmile = SystemClock.elapsedRealtime()
                    if (nowSmile - lastSmileProcessWallMs < smileMinIntervalMs) return
                    lastSmileProcessWallMs = nowSmile
                    val faceCamId = selectedCameraId
                    val faceChars =
                        if (faceCamId != null) {
                            runCatching { cm.getCameraCharacteristics(faceCamId) }.getOrNull()
                        } else {
                            null
                        }
                    if (faceChars == null) {
                        if (nowSmile - lastSmileDiagLogWallMs >= 5_000L) {
                            lastSmileDiagLogWallMs = nowSmile
                            Log.i("PNS.SmileStill", "smileScan skipped: no camera characteristics")
                        }
                        return
                    }
                    val rot = mlInputImageRotationDegrees(faceChars)
                    val camHandler = handler ?: return
                    val smileProb =
                        MlKitFaceTrackSupport.maxSmilingProbability(
                            image = image,
                            rotationDegrees = rot,
                        )
                    if (smileProb == null) {
                        if (nowSmile - lastSmileDiagLogWallMs >= 5_000L) {
                            lastSmileDiagLogWallMs = nowSmile
                            Log.i(
                                "PNS.SmileStill",
                                "smileScan noFaceOrTimeout (enable Eye AF overlay if face is visible)",
                            )
                        }
                        return
                    }
                    if (nowSmile - lastSmileDiagLogWallMs >= 3_000L) {
                        lastSmileDiagLogWallMs = nowSmile
                        Log.i(
                            "PNS.SmileStill",
                            "smileScan prob=$smileProb threshold=${SmileStillCapturePolicy.SMILE_PROBABILITY_THRESHOLD}",
                        )
                    }
                    if (SmileStillCapturePolicy.shouldTrigger(smileProb)) {
                        Log.i("PNS.SmileStill", "smileTrigger prob=$smileProb")
                        val listener = smileStillCaptureListener
                        if (listener != null) {
                            camHandler.post { listener.invoke() }
                        }
                    }
                }

                fun runMlFaceHudIfNeeded() {
                    if (!wantFaceHud) {
                        return
                    }
                    if (skipCameraFacesForMlHud) {
                        return
                    }
                    val faceCamId = selectedCameraId
                    val faceBuf = desiredSurfaceSize ?: currentSurfaceSize
                    val faceChars =
                        if (faceCamId != null) {
                            runCatching { cm.getCameraCharacteristics(faceCamId) }.getOrNull()
                        } else {
                            null
                        }
                    if (faceChars == null ||
                        faceBuf == null ||
                        faceBuf.width <= 0 ||
                        faceBuf.height <= 0
                    ) {
                        return
                    }
                    lastMlFaceProcessWallMs = SystemClock.elapsedRealtime()
                    val rot = mlInputImageRotationDegrees(faceChars)
                    val mirror =
                        faceChars.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_FRONT
                    val hud =
                        MlKitFaceTrackSupport.detectFacesHud(
                            image = image,
                            rotationDegrees = rot,
                            bufferWidth = faceBuf.width,
                            bufferHeight = faceBuf.height,
                            mirrorHorizontally = mirror,
                            coverCrop = PREVIEW_FINDER_CONTAIN,
                        )
                    if (hud.boxes.isEmpty()) {
                        mlConsecutiveEmptyMlDetections++
                    } else {
                        mlConsecutiveEmptyMlDetections = 0
                    }
                    val camHandler = handler ?: return
                    camHandler.post {
                        if (!hudFaceOverlayEnabled || yuvImageReader == null) return@post
                        publishMlFaceHud(hud)
                        if (!loggedMlFaceSample) {
                            loggedMlFaceSample = true
                            PnsAdbLog.i(
                                appContext,
                                "mlFaceHud boxes=${hud.boxes.size} eyes=${hud.eyeMarks.size} rot=$rot",
                            )
                        }
                    }
                }

                fun runQrScanIfNeeded() {
                    if (!wantQr) return
                    val nowQr = SystemClock.elapsedRealtime()
                    if (nowQr - lastQrDecodeWallMs < QR_SCAN_DECODE_MIN_INTERVAL_MS) return
                    lastQrDecodeWallMs = nowQr
                    val decoded = QrCodeAnalyzer.tryDecodeFromImage(image) ?: return
                    Log.i(
                        QrCodeAnalyzer.TAG,
                        "decode ok format=${decoded.format} len=${decoded.text.length}",
                    )
                    val camHandler = handler ?: return
                    camHandler.post {
                        if (!wantsQrScan()) return@post
                        qrScanListener?.invoke(decoded.text, decoded.format)
                    }
                }

                fun runHistogramAndHighlightIfNeeded() {
                    if (!wantHighlight && !wantHist && !wantZebra && !wantChase) return

                    val plane = image.planes[0]
                    val buf = plane.buffer
                    buf.rewind()
                    val ps = plane.pixelStride
                    if (ps != 1) {
                        Log.w(tag, "YUV Y plane pixelStride=$ps (expected 1); skipping histogram sample")
                        return
                    }
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    val w = image.width
                    val h = image.height
                    val rowStride = plane.rowStride
                    val hist: IntArray? =
                        if ((wantHist || wantHighlight || wantChase) && w > 0 && h > 0) {
                            runCatching { PreviewLumaHistogram.reduceYuv420Y(bytes, w, h, rowStride) }
                                .onFailure { e -> Log.w(tag, "histogram reduce failed: ${e.message}") }
                                .getOrNull()
                        } else {
                            null
                        }
                    if (wantChase && hist != null) {
                        maybeAdjustReadoutChaseFromHistogram(hist)
                    }
                    if (wantZebra && w > 0 && h > 0) {
                        val wall = SystemClock.elapsedRealtime()
                        if (wall - lastZebraProcessWallMs >= zebraMinIntervalMs) {
                            lastZebraProcessWallMs = wall
                            runCatching {
                                PreviewLumaHistogram.buildClipZebraGridYuv420Y(
                                    bytes,
                                    w,
                                    h,
                                    rowStride,
                                    thresholdUnsigned = HIGHLIGHT_CLIP_ZEBRA_THRESHOLD_UNSIGNED,
                                )
                            }
                                .onSuccess { grid ->
                                    val camHandler = handler ?: return
                                    camHandler.post {
                                        if (!highlightClipZebraEnabled || yuvImageReader == null) return@post
                                        highlightClipZebraListener?.invoke(grid)
                                    }
                                }
                                .onFailure { e -> Log.w(tag, "zebra grid failed: ${e.message}") }
                        }
                    }
                    if (wantHighlight && hist != null) {
                        val bd = HighlightMeter.suggestEvCorrectionBreakdown(hist)
                        val engageSm = smoothHighlightDarkenEngagement(bd.darkenEngagement)
                        val rawEv =
                            when {
                                bd.evCore < 0.0 -> bd.evCore * engageSm
                                highlightMeterDarkenOnly -> 0.0
                                else -> bd.evCore
                            }
                        val smoothedEv = smoothHighlightMeterEv(rawEv)
                        val evForDrive =
                            when {
                                smoothedEv <= -highlightMeterStabilityDarkenBypassEv -> smoothedEv
                                kotlin.math.abs(smoothedEv) < highlightEvStabilityZone -> 0.0
                                else -> smoothedEv
                            }
                        val passesDeadband =
                            when {
                                evForDrive <= 0.0 ->
                                    kotlin.math.abs(evForDrive) >= highlightMeterEvDeadbandDarken
                                else ->
                                    kotlin.math.abs(evForDrive) >= highlightMeterEvDeadbandBrighten
                            }
                        val camId = selectedCameraId ?: return
                        val chars = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                            ?: return
                        val comp =
                            HighlightMeterSupport.evToCompensationIndex(evForDrive, chars) ?: return
                        val shouldPostComp =
                            comp != lastAppliedHighlightComp &&
                                (
                                    passesDeadband ||
                                        (comp == 0 && lastAppliedHighlightComp != null)
                                )
                        if (shouldPostComp) {
                            val camHandler = handler ?: return
                            camHandler.post {
                                if (!wantsHighlightMetering() || yuvImageReader == null) return@post
                                if (comp == lastAppliedHighlightComp) return@post
                                lastAppliedHighlightComp = if (comp == 0) null else comp
                                Log.d(
                                    tag,
                                    "HighlightMeter rawEv=${"%.2f".format(rawEv)} sm=${"%.2f".format(smoothedEv)} " +
                                        "aeComp=$comp",
                                )
                                val adbWall = SystemClock.elapsedRealtime()
                                if (adbWall - lastHighlightMeterAdbLogMs >= 3500L) {
                                    lastHighlightMeterAdbLogMs = adbWall
                                    PnsAdbLog.i(
                                        appContext,
                                        "highlightMeter ev=${"%.2f".format(evForDrive)} aeComp=$comp dial=H",
                                    )
                                }
                                refreshRepeatingPreviewForHighlightMeter()
                            }
                        }
                    }
                    if (wantHist && hist != null) {
                        val snap = hist.copyOf()
                        val camHandler = handler ?: return
                        camHandler.post {
                            if (!previewHistogramEnabled) return@post
                            previewHistogramListener?.invoke(snap)
                        }
                    }
                }

                if (wantSmileStill) {
                    runSmileStillIfNeeded()
                }
                if (prioritizeHMetering) {
                    runHistogramAndHighlightIfNeeded()
                    runMlFaceHudIfNeeded()
                } else {
                    runMlFaceHudIfNeeded()
                    if (!wantHighlight && !wantHist && !wantQr) return@execute
                    runHistogramAndHighlightIfNeeded()
                }
                runQrScanIfNeeded()
            } finally {
                image.close()
                yuvAnalysisInFlight.set(false)
            }
        }
    }

    private fun scalerCropRectForSession(
        chars: CameraCharacteristics,
        sessionCameraId: String,
        mode: FocalMode?,
    ): Rect {
        val ids = cameraIds()
        val roles = BackCameraRoleResolver.resolve(cm, ids)
        val sessionPhysicalIds =
            runCatching { cm.getCameraCharacteristics(sessionCameraId).physicalCameraIds?.toSet() }
                .getOrNull()
        return SensorCropGeometry.scalerCropRect(
            chars,
            sessionCameraId,
            mode,
            sessionPhysicalIds = sessionPhysicalIds,
            wideId = roles.wide,
            teleId = roles.tele,
        )
    }

    private fun applyScalerCropAndMetering(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
        aeHighlightComp: Int? = null,
    ) {
        val useDigitalCrop = focalCropMode != null && desiredFps < 120
        val modeForCrop = if (useDigitalCrop) focalCropMode else null
        val cropRect = scalerCropRectForSession(chars, camId, modeForCrop)
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            req.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            Log.d(
                tag,
                "SCALER_CROP_REGION=${cropRect.left},${cropRect.top}-${cropRect.right},${cropRect.bottom} mode=${modeForCrop?.name ?: "full"}",
            )
        }
        val tap = tapMeteringRect
        val manualFocusLocksAf = tap == null && wantsManualFocusDistance()
        val faceMeterForAe =
            if (tap == null &&
                facePriorityMeteringRect != null &&
                hudFaceOverlayEnabled &&
                !automationSuppressFacePipeline &&
                allowsFacePriorityMetering()
            ) {
                facePriorityMeteringRect
            } else {
                null
            }
        // Manual distance needs AF OFF; do not drive face CAF over the rack.
        val faceMeter = if (manualFocusLocksAf) null else faceMeterForAe
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
            faceMeterForAe != null && manualFocusLocksAf -> {
                req.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(faceMeterForAe))
                if (manualAwbModeOverride == null) {
                    val maxAwb =
                        (chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) as? IntArray)
                            ?.firstOrNull()
                            ?: 0
                    if (maxAwb > 0) {
                        req.set(CaptureRequest.CONTROL_AWB_REGIONS, arrayOf(faceMeterForAe))
                    }
                }
            }
            faceMeter != null -> {
                // Exposure + focus both weighted to the tracked face (max metering weight).
                req.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(faceMeter))
                req.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(faceMeter))
                // Auto WB: bias color toward the face when the device supports AWB regions.
                if (manualAwbModeOverride == null) {
                    val maxAwb =
                        (chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) as? IntArray)
                            ?.firstOrNull()
                            ?: 0
                    if (maxAwb > 0) {
                        req.set(CaptureRequest.CONTROL_AWB_REGIONS, arrayOf(faceMeter))
                    }
                }
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
        if (tap == null && manualFocusLocksAf) {
            applyManualDialFocus(req, chars)
        } else if (tap == null && faceMeter == null && wantsMacroProgram()) {
            applyMacroProgramAf(req, chars, camId)
        } else if (tap == null && faceMeter == null && commandDialMode == CommandDialMode.S) {
            applyStreetSnapAf(req, chars)
        } else if (tap == null && faceMeter == null && commandDialMode != CommandDialMode.S) {
            applyPreviewFocusSelection(req, chars)
        }
        // Highlight (H): same continuous AE + AF program as Auto; metering differs via
        // [CONTROL_AE_EXPOSURE_COMPENSATION] from [processYuvForHighlight].
        if (tap == null && (commandDialMode == CommandDialMode.Auto || commandDialMode == CommandDialMode.H)) {
            if (faceMeter == null && !wantsManualFocusDistance()) {
                applyAutoProgramAf(req, chars)
            }
            applyAutoProgramAeOn(req, chars)
        }
        // BKT: ensure AE is ON so face [CONTROL_AE_REGIONS] biases exposure during preview.
        if (tap == null && commandDialMode == CommandDialMode.BKT && faceMeter != null) {
            applyAutoProgramAeOn(req, chars)
        }
        if (aeHighlightComp != null) {
            req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeHighlightComp)
        }
    }

    @Volatile private var lastMacroAfDiagSig: Int = Int.MIN_VALUE

    private fun applyMacroProgramAf(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        camId: String,
    ) {
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val mode =
            when {
                afModes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ->
                    CaptureRequest.CONTROL_AF_MODE_MACRO
                afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ->
                    CaptureRequest.CONTROL_AF_MODE_AUTO
                else -> null
            }
        if (mode != null) {
            req.set(CaptureRequest.CONTROL_AF_MODE, mode)
        }
        val sig = (mode ?: -1) xor camId.hashCode()
        if (sig != lastMacroAfDiagSig) {
            lastMacroAfDiagSig = sig
            val modeLabel =
                when (mode) {
                    CaptureRequest.CONTROL_AF_MODE_MACRO -> "MACRO"
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
                    CaptureRequest.CONTROL_AF_MODE_AUTO -> "AUTO"
                    else -> "none"
                }
            Log.i(
                "PNS.ChromeUx",
                "macroMode afMode=$modeLabel cameraId=$camId hardwareMacro=${mode == CaptureRequest.CONTROL_AF_MODE_MACRO}",
            )
        }
        applyMacroVendorCloseupOnRequest(req, chars, camId)
    }

    private fun applyPreviewFocusSelection(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        when (val sel = previewFocusSelection) {
            PreviewFocusSelection.Auto -> applyAutoProgramAf(req, chars)
            PreviewFocusSelection.ManualDistance -> applyManualDialFocus(req, chars)
            is PreviewFocusSelection.HalAf ->
                if (afModes.contains(sel.mode)) {
                    req.set(CaptureRequest.CONTROL_AF_MODE, sel.mode)
                } else {
                    applyAutoProgramAf(req, chars)
                }
        }
    }

    /**
     * M dial / manual distance: fixed [LENS_FOCUS_DISTANCE] with AF off so GLES focus peaking is meaningful.
     * Tap / face metering branches above take precedence when active.
     */
    private fun applyManualDialFocus(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val range = ManualFocusDistance.focusRange(chars)
        if (!range.sliderEnabled) {
            applyAutoProgramAf(req, chars)
            return
        }
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        if (!afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)) {
            applyAutoProgramAf(req, chars)
            return
        }
        val diopters =
            ManualFocusDistance.clamp(
                manualFocusDiopters ?: ManualFocusDistance.defaultForLens(chars).also {
                    manualFocusDiopters = it
                },
                range.maxDiopters,
            )
        req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        req.set(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        logManualFocusPeakingDiag(chars, cameraId = selectedCameraId ?: "?")
    }

    private fun logManualFocusPeakingDiag(
        chars: CameraCharacteristics,
        cameraId: String = selectedCameraId ?: "?",
        range: ManualFocusDistance.FocusRange = ManualFocusDistance.focusRange(chars),
    ) {
        val diopters = manualFocusDiopters ?: return
        val recording = videoController.isRecorderPresent() || rawVideoController.isRecording
        val sig =
            (diopters * 1000f).toInt() xor
                (range.maxDiopters * 10f).toInt() xor
                cameraId.hashCode() xor
                (if (recording) 1 else 0) xor
                (commandDialMode.ordinal shl 4)
        if (sig == lastFocusPeakingDiagSig) return
        lastFocusPeakingDiagSig = sig
        Log.i(
            "PNS.FocusPeaking",
            "manualFocus active=true cameraId=$cameraId diopters=${"%.3f".format(diopters)} " +
                "rackMax=${"%.3f".format(range.maxDiopters)} halMin=${range.halMinimumFocusDiopters} " +
                "recording=$recording afMode=OFF peakingShader=edge",
        )
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

    private fun applyAutoProgramAeOn(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        if (commandDialMode == CommandDialMode.H && desiredFps < 120) {
            val mode = HighlightAeModeSupport.resolveHighlightWeightedAeModeOrNull(
                chars,
                highlightAeTryVendorExtraModes(),
            )
            if (mode != null && aeModes.contains(mode)) {
                req.set(CaptureRequest.CONTROL_AE_MODE, mode)
                return
            }
        }
        val picked = PreviewFlashPolicy.aeModeForAutoProgramWithFlashPref(aeModes, previewFlashMode)
        if (picked != null && aeModes.contains(picked)) {
            req.set(CaptureRequest.CONTROL_AE_MODE, picked)
        }
    }

    private fun applyAutoProgramAf(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
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

    /**
     * Called after preview settles to start the video recorder.
     * Sprint 12.4: Delegates to VideoRecordingController.
     */
    private fun maybeStartInAppVideoRecorderAfterPreview() {
        if (!inAppVideoRecordingArmed || !videoController.isRecorderPresent()) return
        if (hfrEncoderOnlyRecordActive) {
            scheduleHfrInterleavedWatchdogs()
            // MediaCodec + HS burst are started from HFR onConfigured (codec before burst).
            return
        }
        if (useHfrInterleavedMcPreview() && !videoController.isRecorderStarted()) {
            deferMcStartUntilPreviewFrame = true
            Log.i(
                HfrInterleavedPreviewSupport.TAG,
                "defer MediaCodec start until first HS preview frame",
            )
            mainHandler.postDelayed({
                if (deferMcStartUntilPreviewFrame && inAppVideoRecordingArmed) {
                    Log.w(
                        HfrInterleavedPreviewSupport.TAG,
                        "deferred MediaCodec start timeout — starting encoder",
                    )
                    deferMcStartUntilPreviewFrame = false
                    startInAppVideoRecorderNow()
                }
            }, 2_500L)
            return
        }
        startInAppVideoRecorderNow()
    }

    private fun startInAppVideoRecorderNow() {
        if (!inAppVideoRecordingArmed || !videoController.isRecorderPresent()) return
        if (videoController.isRecorderStarted()) return
        videoController.maybeStartRecorder()
        if (useHfrInterleavedMcPreview()) {
            scheduleHfrInterleavedWatchdogs()
        }
    }

    private fun onDeferredMcStartPreviewFrame() {
        if (!deferMcStartUntilPreviewFrame || !inAppVideoRecordingArmed) return
        deferMcStartUntilPreviewFrame = false
        handler?.post { startInAppVideoRecorderNow() }
    }

    private fun startRepeating(
        sess: CameraCaptureSession,
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
        camId: String,
    ) {
        val requestSurf =
            if (hfrEncoderOnlyRecordActive) {
                videoController.getRecordingSurface()?.takeIf { it.isValid } ?: surf
            } else {
                surf
            }
        val req =
            try {
                buildPreviewCaptureRequest(camera, requestSurf, fpsRange, camId)
            } catch (e: CameraAccessException) {
                lastStatus = "Preview request failed: ${e.reason}"
                Log.e(tag, "startRepeating buildPreviewCaptureRequest failed (CameraAccessException)", e)
                handler?.post { kickPreviewPipelineRestart() }
                return
            } catch (t: Throwable) {
                lastStatus = "Preview request failed: ${t::class.java.simpleName}"
                Log.e(tag, "startRepeating buildPreviewCaptureRequest failed", t)
                handler?.post { kickPreviewPipelineRestart() }
                return
            }

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
            runCatching { sess.stopRepeating() }
                .exceptionOrNull()
                ?.let { Log.w(tag, "startRepeating stopRepeating: ${it.message}") }
            if (constrained) {
                val hs = sess as? android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
                if (hfrEncoderOnlyRecordActive) {
                    // Single HS output (encoder). CPH2655: setRepeatingRequest throws
                    // UnsupportedOperationException — use HS burst list. MediaCodec must already be
                    // running (started from HFR onConfigured before we get here).
                    val startBurst = Runnable {
                        val encList = hs?.createHighSpeedRequestList(req)
                        if (!encList.isNullOrEmpty()) {
                            runCatching { sess.setRepeatingBurst(encList, fpsMeasuringCallback(), handler) }
                                .onSuccess {
                                    lastStatus = "Preview running (HFR ${fpsRange?.upper ?: "?"}fps)"
                                    Log.i(
                                        tag,
                                        "HFR repeatingBurst encoder-only n=${encList.size} " +
                                            "fps=${fpsRange?.upper ?: "?"}",
                                    )
                                    scheduleReadoutChromeUxFallback()
                                }
                                .onFailure { e ->
                                    lastStatus = "Repeating failed: ${e::class.java.simpleName}"
                                    Log.e(tag, "HFR encoder-only setRepeatingBurst failed", e)
                                }
                        } else {
                            Log.w(tag, "HFR encoder-only createHighSpeedRequestList returned empty")
                        }
                    }
                    if (videoController.isRecorderStarted()) {
                        startBurst.run()
                    } else {
                        handler?.post(startBurst)
                    }
                    return
                }
                val list = hs?.createHighSpeedRequestList(req)
                if (list != null) {
                    sess.setRepeatingBurst(list, fpsMeasuringCallback(), handler)
                    lastStatus = "Preview running (HFR ${fpsRange?.upper ?: "?"}fps)"
                    Log.i(
                        tag,
                        "HFR repeatingBurst started (n=${list.size}) fps=${fpsRange?.upper ?: "?"} " +
                            "interleaved=${useHfrInterleavedMcPreview() && inAppVideoRecordingArmed && videoController.isRecorderPresent()}",
                    )
                    scheduleReadoutChromeUxFallback()
                    maybeStartInAppVideoRecorderAfterPreview()
                    return
                }
            }

            sess.setRepeatingRequest(req, fpsMeasuringCallback(), handler)
            lastStatus = "Preview running (normal)"
            Log.d(tag, "Normal repeatingRequest started")
            scheduleReadoutChromeUxFallback()
            // Sprint 12.4: Video recorder start handled by VideoRecordingController after preview settles.
            maybeStartInAppVideoRecorderAfterPreview()
        } catch (e: CameraAccessException) {
            lastStatus = "Repeating failed: ${e.reason}"
        } catch (t: Throwable) {
            lastStatus = "Repeating failed: ${t::class.java.simpleName}"
        }
    }

    private fun updatePreviewMetadata(result: CaptureResult) {
        previewMetadata.updateAndGet { cur -> PreviewMetadata.mergeFromResult(cur, result) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)?.let { id ->
                if (id.isNotBlank()) {
                    lastLogicalMultiCameraPhysicalId = id
                    PreviewLogicalPhysicalDebugBridge.updateFromCaptureResult(id)
                }
            }
        }
        maybeLogChromeUxReadout()
        PnsStartupTrace.maybeMarkFirstFrameReadyFromPreview(result, smoothedFps)
    }

    private fun maybeLogChromeUxReadout() {
        if (loggedChromeUxReadout) return
        val meta = previewMetadata.get()
        if (meta.iso == null && meta.exposureNs == null && meta.awbMode == null) return
        loggedChromeUxReadout = true
        readoutFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        readoutFallbackRunnable = null
        val iso = meta.iso
        val ss = PreviewReadoutFormat.formatShutter(meta.exposureNs)
        val awb = PreviewReadoutFormat.awbModeLabel(meta.awbMode)
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
                processFaceStatistics(result, isFinal = true)
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
                if (hudFaceOverlayEnabled &&
                    !automationSuppressFacePipeline &&
                    partialResult.get(CaptureResult.STATISTICS_FACES) != null
                ) {
                    processFaceStatistics(partialResult, isFinal = false)
                }
            }

            private fun onCaptureResult(result: TotalCaptureResult) {
                lastPreviewTotalCaptureResult = result
                feedZslRingResult(result)
                lastPreviewControlAfState = result.get(CaptureResult.CONTROL_AF_STATE)
                if (stillBoundaryDiagEnabled()) {
                    lastPreviewBoundarySnapshot = StillCaptureBoundaryDiag.Snapshot.from(result)
                }
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
                if (deferMcStartUntilPreviewFrame && inAppVideoRecordingArmed) {
                    onDeferredMcStartPreviewFrame()
                }
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

    private fun pickHighSpeedTarget(map: StreamConfigurationMap?, desiredFps: Int): Pair<Size, Range<Int>>? =
        if (useHfrInterleavedMcPreview() && inAppVideoRecordingArmed && videoController.isRecorderPresent()) {
            InAppVideoRecordingSupport.pickInterleavedHighSpeedVideoTarget(
                map,
                desiredFps,
                inAppVideoEncodeSizePref,
            ) ?: InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
                map,
                desiredFps,
                inAppVideoEncodeSizePref,
            )
        } else {
            InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
                map,
                desiredFps,
                inAppVideoEncodeSizePref,
            )
        }

    /**
     * MediaCodec HFR: **encoder-only** constrained HS on the record camera plus a **second**
     * rear camera (~30 fps YUV) for the live finder ([startHfrRecordMonitor]).
     *
     * Interleaved preview+encoder on one HS session does not deliver encoder buffers on
     * CPH2655-class devices; encoder-only does (~120 fps file) while the monitor avoids a
     * frozen finder.
     */
    private fun hfrSessionOutputSurfaces(surfaces: List<Surface>, previewSurf: Surface): List<Surface> {
        if (useHfrInterleavedMcPreview() && inAppVideoRecordingArmed && videoController.isRecorderPresent()) {
            val enc = videoController.getRecordingSurface()?.takeIf { it.isValid } ?: return surfaces.filter { it.isValid }
            val hs = desiredHighSpeedSize
            if (startHfrRecordMonitor()) {
                Log.i(
                    HfrInterleavedPreviewSupport.TAG,
                    "HFR encoder-only HS encodeFps=$desiredFps buffer=${hs?.width ?: 0}x${hs?.height ?: 0}",
                )
                Log.i(
                    "PNS.ChromeUx",
                    "hfrEncoderOnly active=true encodeFps=$desiredFps monitorFinder=yuv",
                )
                return listOf(enc)
            }
            val prev = previewSurf.takeIf { it.isValid }
            if (prev != null) {
                Log.w(
                    HfrInterleavedPreviewSupport.TAG,
                    "HFR monitor camera unavailable — interleaved preview+encoder fallback " +
                        "encodeFps=$desiredFps",
                )
                Log.i(
                    "PNS.ChromeUx",
                    "hfrEncoderOnly active=false encodeFps=$desiredFps monitorFinder=interleaved",
                )
                notifyHfrFinderMonitorGl(false)
                return listOf(prev, enc)
            }
            Log.e(HfrInterleavedPreviewSupport.TAG, "HFR record: no monitor and no preview surface")
            return listOf(enc)
        }
        return surfaces.filter { it.isValid }
    }

    private fun resolveInAppVideoRecordSize(): Size {
        if (dualVideoActive) {
            return DualVideoRecordingController.compositeRecordSize()
        }
        val session = desiredSurfaceSize ?: currentSurfaceSize
        val pref = inAppVideoEncodeSizePref?.takeIf { it.width > 0 && it.height > 0 }
        val camId = selectedCameraId
        val map =
            camId?.let {
                runCatching {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                }.getOrNull()
            }
        if (desiredFps >= 120 && videoController.wantsMediaCodecPath) {
            InAppVideoRecordingSupport.pickHighSpeedVideoTarget(map, desiredFps, pref ?: session)
                ?.first
                ?.let { return it }
        }
        if (pref != null && map != null) {
            val hal = InAppVideoRecordingSupport.pickOutputSize(map, pref.width, pref.height)
            if (hal.width != pref.width || hal.height != pref.height) {
                Log.w(
                    tag,
                    "encodePref ${pref.width}x${pref.height} clamped to HAL MR ${hal.width}x${hal.height}",
                )
            }
            return hal
        }
        return when {
            desiredFps >= 120 -> session ?: pref ?: Size(1920, 1080)
            else -> pref ?: session ?: Size(1920, 1080)
        }
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
                    val camForRaw = selectedCameraId
                    val rawPair =
                        if (!camForRaw.isNullOrBlank()) {
                            RawCaptureSupport.pickRawOutputForPreviewSession(
                                cm,
                                cameraIds(),
                                camForRaw,
                                ch,
                                previewSurfacePhysicalCameraId,
                                rawStreamPreference,
                                focalCropMode = focalCropMode,
                                usePhysicalChildRawStreamMapForLogicalSession = false,
                            )
                        } else {
                            RawCaptureSupport.pickRawOutput(ch, rawStreamPreference)
                        }
                    rawPair?.second?.let { sz ->
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

/** Manual-focus rack + tap-to-shoot suspend (separate composable shrinks [PreviewEngineContent] DEX). */
private data class PreviewManualFocusUiState(
    val rackActive: Boolean,
    val rackDiopters: Float,
    val rackMaxDiopters: Float,
    val focusChipDiopters: Float?,
    val effectiveTapPreviewToCapture: Boolean,
    val onRackDiopters: (Float) -> Unit,
)

@Composable
private fun rememberPreviewManualFocusUiState(
    commandDialMode: CommandDialMode,
    previewFocusSelection: PreviewFocusSelection,
    selectedCameraId: String?,
    controller: PreviewController,
    chromePrefs: PreviewChromePreferencesState,
    chromeTapPreviewToCapture: Boolean,
): PreviewManualFocusUiState {
    val manualFocusRackActive =
        commandDialMode == CommandDialMode.M ||
            previewFocusSelection == PreviewFocusSelection.ManualDistance
    var manualRackUiDiopters by remember { mutableStateOf(0f) }
    var manualRackUiMax by remember { mutableStateOf(8f) }
    var manualRackEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(manualFocusRackActive, previewFocusSelection, selectedCameraId) {
        if (!manualFocusRackActive) return@LaunchedEffect
        val range = controller.peekManualFocusRange()
        manualRackEnabled = range?.sliderEnabled != false
        manualRackUiMax = range?.maxDiopters ?: controller.peekManualFocusMaxDiopters()
        if (manualRackUiMax <= 0f) {
            manualRackEnabled = false
            return@LaunchedEffect
        }
        if (commandDialMode == CommandDialMode.M) {
            controller.ensureManualFocusForDialM()
        }
        manualRackUiDiopters =
            controller.peekManualFocusDiopters()
                ?: (manualRackUiMax * 0.35f).coerceIn(0f, manualRackUiMax)
    }
    var tapPreviewSavedForManualFocus by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(manualFocusRackActive, chromeTapPreviewToCapture) {
        if (manualFocusRackActive) {
            if (tapPreviewSavedForManualFocus == null) {
                tapPreviewSavedForManualFocus = chromeTapPreviewToCapture
                if (chromeTapPreviewToCapture) {
                    val cur = chromePrefs.current
                    chromePrefs.update(cur.copy(tapPreviewToCapture = false))
                    Log.i("PNS.ChromeUx", "tapToShoot=suspendedForManualFocus")
                }
            }
        } else {
            tapPreviewSavedForManualFocus?.let { prior ->
                val cur = chromePrefs.current
                if (cur.tapPreviewToCapture != prior) {
                    chromePrefs.update(cur.copy(tapPreviewToCapture = prior))
                }
                Log.i("PNS.ChromeUx", "tapToShoot=restoredAfterManualFocus prior=$prior")
                tapPreviewSavedForManualFocus = null
            }
        }
    }
    val focusChipDiopters =
        if (manualFocusRackActive) {
            manualRackUiDiopters
        } else {
            controller.peekManualFocusDiopters()
        }
    return PreviewManualFocusUiState(
        rackActive = manualFocusRackActive && manualRackEnabled,
        rackDiopters = manualRackUiDiopters,
        rackMaxDiopters = manualRackUiMax,
        focusChipDiopters = focusChipDiopters,
        effectiveTapPreviewToCapture = chromeTapPreviewToCapture && !manualFocusRackActive,
        onRackDiopters = { value ->
            manualRackUiDiopters = value
            controller.setPreviewFocusManualDiopters(value)
        },
    )
}

// Marker shim (kept to avoid accidental imports/edits later).
private interface CameraConstrainedHighSpeedSessionShim

