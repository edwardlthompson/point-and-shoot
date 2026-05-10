package dev.pointandshoot

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Capture / preview chrome that is not part of the HUD overlay (brightness, keys, DND).
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
    val context = LocalContext.current
    var current by remember { mutableStateOf(PreviewChromePreferences.load(context)) }

    LaunchedEffect(Unit) {
        current = PreviewChromePreferences.load(context)
    }

    return remember(current) {
        PreviewChromePreferencesState(
            current = current,
            update = { next ->
                PreviewChromePreferences.save(context, next)
                current = next
            },
        )
    }
}

class PreviewChromePreferencesState(
    val current: PreviewChromePreferences,
    val update: (PreviewChromePreferences) -> Unit,
)
