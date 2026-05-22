package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Capture / preview chrome that is not part of the HUD overlay (brightness, keys, DND).
 *
 * **Persistence:** [load]/[save] use [PREFS_NAME]. With manifest **allowBackup** and
 * `res/xml/pns_backup_rules.xml`, these prefs participate in **Android Auto Backup** / device
 * transfer when the OEM/Google account restores the app after reinstall (not guaranteed on all
 * devices or sideload-only flows).
 */
data class PreviewChromePreferences(
    val maxBrightnessInPreview: Boolean = true,
    /** Total interruption silence while preview is visible (shared ref-count with recording DND). */
    val dndWhileInPreview: Boolean = true,
    val dndWhileRecording: Boolean = false,
    val volumeKeysCapture: Boolean = true,
    /** When true and location permission is granted, new captures use [CaptureLocationBridge] for GPS metadata. */
    val saveLocationWithMedia: Boolean = false,
    /** Large circular shutter over the preview (Sony hardware-shutter style apps often omit this). */
    val showOnScreenShutter: Boolean = true,
    /**
     * Tap preview: DOWN moves AF/AE to the tap; UP fires a still (when RAW capture is available).
     * See [TapToShootHandler].
     */
    val tapPreviewToCapture: Boolean = true,
    /**
     * When true, the letterboxed preview accepts four taps (TL→TR→BR→BL) and draws a bilinear
     * patch grid (defaults to Generic24 topology) for chart alignment. Disables tap-to-shoot on
     * the preview while active.
     */
    val liveChartCornerOverlay: Boolean = false,
    /**
     * When **true** (default), the live stream uses center-**crop** inside the [TextureView] so the
     * finder fills the tile (may show a tighter field of view than the JPEG/DNG still). When **false**,
     * the stream uses center-**contain** (letterboxed inside the tile) so framing matches still output.
     */
    val previewTextureCoverCrop: Boolean = true,
    /**
     * Static rotation applied to the displayed preview via `graphicsLayer.rotationZ` (0 / 90 / 180 /
     * 270). The value is **fixed at runtime** — the preview never auto-rotates as the phone tilts.
     *
     * Default stored **90°** maps to **0°** effective rotation (see [effectivePreviewStaticRotationDeg]
     * in [PreviewLayoutOrientation]) — a fixed **90° CCW** correction vs the raw buffer. Use
     * **Spin (preview)** when printed charts (e.g. DGK 8.5×11) do not line up; the button label shows
     * **effective** degrees.
     *
     * The chrome (rails / icons / horizon level) handles its own gravity-based rotation
     * separately, so changing this never affects how the chrome rotates.
     */
    val staticPreviewRotationDeg: Int = 90,
    /** Still capture delay in seconds; only **0 / 3 / 5 / 10** are persisted (invalid loads snap to 0). */
    val selfTimerDelaySec: Int = 0,
    /**
     * When true, still capture targets RAW+DNG plus a hardware JPEG companion (**RAW+**). When false,
     * JPEG [ImageReader] is omitted (**RAW** only, lower bandwidth).
     */
    val stillCaptureJpegCompanion: Boolean = true,
    /**
     * Rear flash / torch intent for preview + still [android.hardware.camera2.CaptureRequest].
     * Default **Auto** (not forced flash); user choice persists in [PREFS_NAME].
     */
    val previewFlashMode: PreviewFlashMode = PreviewFlashMode.Auto,
    /**
     * In-app [android.media.MediaRecorder] encode width/height; **0×0** means auto (720p-first heuristic).
     * Validated against per-camera [android.hardware.camera2.params.StreamConfigurationMap] when recording starts.
     */
    val inAppVideoEncodeWidth: Int = 0,
    val inAppVideoEncodeHeight: Int = 0,
    /** [VideoCodec.ordinal] of the last user-selected video codec; -1 = not set (use default). */
    val inAppVideoCodecOrdinal: Int = -1,
    /** Last user-selected video frame rate; 0 = not set (use default). */
    val inAppVideoFps: Int = 0,
) {
    companion object {
        const val PREFS_NAME = "pns_preview_chrome"

        private const val KEY_MAX_BRIGHTNESS = "max_brightness_preview"
        private const val KEY_DND_PREVIEW = "dnd_while_in_preview"
        private const val KEY_DND_RECORDING = "dnd_while_recording"
        private const val KEY_VOLUME_KEYS = "volume_keys_capture"
        private const val KEY_SAVE_LOCATION = "save_location_with_media"
        private const val KEY_SHOW_SHUTTER = "show_on_screen_shutter"
        private const val KEY_TAP_PREVIEW_CAPTURE = "tap_preview_to_capture"
        private const val KEY_LIVE_CHART_CORNERS = "live_chart_corner_overlay"
        private const val KEY_PREVIEW_TEXTURE_COVER_CROP = "preview_texture_cover_crop"
        private const val KEY_STATIC_PREVIEW_ROT = "static_preview_rotation_deg"
        private const val KEY_SELF_TIMER_DELAY_SEC = "self_timer_delay_sec"
        private const val KEY_STILL_JPEG_COMPANION = "still_capture_jpeg_companion"
        private const val KEY_PREVIEW_FLASH_MODE = "preview_flash_mode_ordinal"
        private const val KEY_IN_APP_VIDEO_ENC_W = "in_app_video_encode_w"
        private const val KEY_IN_APP_VIDEO_ENC_H = "in_app_video_encode_h"
        private const val KEY_IN_APP_VIDEO_CODEC = "in_app_video_codec_ordinal"
        private const val KEY_IN_APP_VIDEO_FPS = "in_app_video_fps"
        private const val KEY_LAST_REAR_CAMERA_ID = "last_rear_camera_id"

        /** Last non-front preview `cameraId` (for Milestone **10.2** / future front→rear UX). */
        fun readLastRearCameraId(context: Context): String? {
            val raw =
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_REAR_CAMERA_ID, null)
                    ?.trim()
                    ?: return null
            if (raw.isEmpty()) return null
            val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return raw.takeIf { !Camera2Facing.isFrontCamera(cm, it) }
        }

        fun saveLastRearCameraIdIfRear(context: Context, cameraId: String) {
            val id = cameraId.trim()
            if (id.isEmpty()) return
            val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (Camera2Facing.isFrontCamera(cm, id)) return
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_REAR_CAMERA_ID, id)
                .apply()
        }

        fun load(context: Context): PreviewChromePreferences {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = PreviewChromePreferences()
            return PreviewChromePreferences(
                maxBrightnessInPreview = prefs.getBoolean(KEY_MAX_BRIGHTNESS, defaults.maxBrightnessInPreview),
                dndWhileInPreview = prefs.getBoolean(KEY_DND_PREVIEW, defaults.dndWhileInPreview),
                dndWhileRecording = prefs.getBoolean(KEY_DND_RECORDING, defaults.dndWhileRecording),
                volumeKeysCapture = prefs.getBoolean(KEY_VOLUME_KEYS, defaults.volumeKeysCapture),
                saveLocationWithMedia = prefs.getBoolean(KEY_SAVE_LOCATION, defaults.saveLocationWithMedia),
                showOnScreenShutter = prefs.getBoolean(KEY_SHOW_SHUTTER, defaults.showOnScreenShutter),
                tapPreviewToCapture = prefs.getBoolean(KEY_TAP_PREVIEW_CAPTURE, defaults.tapPreviewToCapture),
                liveChartCornerOverlay = prefs.getBoolean(KEY_LIVE_CHART_CORNERS, defaults.liveChartCornerOverlay),
                previewTextureCoverCrop = prefs.getBoolean(KEY_PREVIEW_TEXTURE_COVER_CROP, defaults.previewTextureCoverCrop),
                staticPreviewRotationDeg = normalizeStaticRotation(
                    prefs.getInt(KEY_STATIC_PREVIEW_ROT, defaults.staticPreviewRotationDeg),
                ),
                selfTimerDelaySec = normalizeSelfTimerDelaySec(
                    prefs.getInt(KEY_SELF_TIMER_DELAY_SEC, defaults.selfTimerDelaySec),
                ),
                stillCaptureJpegCompanion = prefs.getBoolean(KEY_STILL_JPEG_COMPANION, defaults.stillCaptureJpegCompanion),
                previewFlashMode =
                    PreviewFlashMode.fromStorageOrdinal(
                        prefs.getInt(KEY_PREVIEW_FLASH_MODE, defaults.previewFlashMode.ordinal),
                    ),
                inAppVideoEncodeWidth =
                    prefs.getInt(KEY_IN_APP_VIDEO_ENC_W, defaults.inAppVideoEncodeWidth).coerceAtLeast(0),
                inAppVideoEncodeHeight =
                    prefs.getInt(KEY_IN_APP_VIDEO_ENC_H, defaults.inAppVideoEncodeHeight).coerceAtLeast(0),
                inAppVideoCodecOrdinal =
                    prefs.getInt(KEY_IN_APP_VIDEO_CODEC, defaults.inAppVideoCodecOrdinal),
                inAppVideoFps =
                    prefs.getInt(KEY_IN_APP_VIDEO_FPS, defaults.inAppVideoFps).coerceAtLeast(0),
            )
        }

        fun save(context: Context, value: PreviewChromePreferences) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_MAX_BRIGHTNESS, value.maxBrightnessInPreview)
                .putBoolean(KEY_DND_PREVIEW, value.dndWhileInPreview)
                .putBoolean(KEY_DND_RECORDING, value.dndWhileRecording)
                .putBoolean(KEY_VOLUME_KEYS, value.volumeKeysCapture)
                .putBoolean(KEY_SAVE_LOCATION, value.saveLocationWithMedia)
                .putBoolean(KEY_SHOW_SHUTTER, value.showOnScreenShutter)
                .putBoolean(KEY_TAP_PREVIEW_CAPTURE, value.tapPreviewToCapture)
                .putBoolean(KEY_LIVE_CHART_CORNERS, value.liveChartCornerOverlay)
                .putBoolean(KEY_PREVIEW_TEXTURE_COVER_CROP, value.previewTextureCoverCrop)
                .putInt(KEY_STATIC_PREVIEW_ROT, normalizeStaticRotation(value.staticPreviewRotationDeg))
                .putInt(KEY_SELF_TIMER_DELAY_SEC, normalizeSelfTimerDelaySec(value.selfTimerDelaySec))
                .putBoolean(KEY_STILL_JPEG_COMPANION, value.stillCaptureJpegCompanion)
                .putInt(KEY_PREVIEW_FLASH_MODE, value.previewFlashMode.ordinal)
                .putInt(KEY_IN_APP_VIDEO_ENC_W, value.inAppVideoEncodeWidth.coerceAtLeast(0))
                .putInt(KEY_IN_APP_VIDEO_ENC_H, value.inAppVideoEncodeHeight.coerceAtLeast(0))
                .putInt(KEY_IN_APP_VIDEO_CODEC, value.inAppVideoCodecOrdinal)
                .putInt(KEY_IN_APP_VIDEO_FPS, value.inAppVideoFps.coerceAtLeast(0))
                .apply()
        }

        /** Allowed self-timer values in UI order (cycle wraps). */
        val SELF_TIMER_DELAY_SEC_OPTIONS: IntArray = intArrayOf(0, 3, 5, 10)

        fun normalizeSelfTimerDelaySec(raw: Int): Int =
            when (raw) {
                0, 3, 5, 10 -> raw
                else -> 0
            }

        fun cycleSelfTimerDelaySec(current: Int): Int {
            val norm = normalizeSelfTimerDelaySec(current)
            val idx = SELF_TIMER_DELAY_SEC_OPTIONS.indexOf(norm).let { if (it >= 0) it else 0 }
            return SELF_TIMER_DELAY_SEC_OPTIONS[(idx + 1) % SELF_TIMER_DELAY_SEC_OPTIONS.size]
        }

        /** Snaps any int to {0, 90, 180, 270}; out-of-range or NaN-ish values fall back to 0. */
        fun normalizeStaticRotation(raw: Int): Int {
            val mod = ((raw % 360) + 360) % 360
            return when {
                mod < 45 || mod >= 315 -> 0
                mod < 135 -> 90
                mod < 225 -> 180
                else -> 270
            }
        }
    }
}

@Composable
fun rememberPreviewChromePreferences(): PreviewChromePreferencesState {
    val context = LocalContext.current.applicationContext
    val holder =
        remember {
            mutableStateOf(
                runCatching { PreviewChromePreferences.load(context) }
                    .getOrElse { PreviewChromePreferences() },
            )
        }

    LaunchedEffect(Unit) {
        holder.value =
            runCatching { PreviewChromePreferences.load(context) }
                .getOrElse { holder.value }
    }

    // Stable holder — do not key [remember] on prefs: that recreated [PreviewChromePreferencesState]
    // each change and could expose a stale snapshot to [PreviewEngineContent] mid-frame.
    return remember {
        PreviewChromePreferencesState(
            liveCurrent = {
                holder.value ?: PreviewChromePreferences().also {
                    Log.w("PNS.ChromeUx", "previewChromePrefs holder was null; using defaults")
                }
            },
            update = { next ->
                val prev = holder.value ?: PreviewChromePreferences()
                if (prev.dndWhileInPreview && !next.dndWhileInPreview) {
                    restoreSystemInterruptionFilterAfterPreviewDndDisabled(context)
                }
                PreviewChromePreferences.save(context, next)
                holder.value = next
            },
            /** ADB / automation seeds (e.g. self-timer) must not overwrite disk prefs — see [PreviewEngineScreen]. */
            applySessionOnly = { next ->
                holder.value = next
            },
        )
    }
}

class PreviewChromePreferencesState(
    private val liveCurrent: () -> PreviewChromePreferences,
    val update: (PreviewChromePreferences) -> Unit,
    val applySessionOnly: (PreviewChromePreferences) -> Unit,
) {
    val current: PreviewChromePreferences
        get() =
            liveCurrent() ?: PreviewChromePreferences().also {
                Log.w("PNS.ChromeUx", "previewChromePrefs liveCurrent returned null; using defaults")
            }
}
