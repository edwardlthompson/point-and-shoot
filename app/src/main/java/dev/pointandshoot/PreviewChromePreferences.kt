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
     * Static rotation applied to the displayed preview via `graphicsLayer.rotationZ` (0 / 90 / 180 /
     * 270). The value is **fixed at runtime** — the preview never auto-rotates as the phone tilts.
     *
     * Default **270°** is a common fix when the live buffer appears **90° CW** from reality in
     * a fixed-orientation window (same net effect as **−90°** when positive `rotationZ` is
     * clockwise in Compose).
     *
     * The chrome (rails / icons / horizon level) handles its own gravity-based rotation
     * separately, so changing this never affects how the chrome rotates.
     */
    val staticPreviewRotationDeg: Int = 270,
) {
    companion object {
        const val PREFS_NAME = "pns_preview_chrome"

        private const val KEY_MAX_BRIGHTNESS = "max_brightness_preview"
        private const val KEY_DND_RECORDING = "dnd_while_recording"
        private const val KEY_VOLUME_KEYS = "volume_keys_capture"
        private const val KEY_SAVE_LOCATION = "save_location_with_media"
        private const val KEY_SHOW_SHUTTER = "show_on_screen_shutter"
        private const val KEY_TAP_PREVIEW_CAPTURE = "tap_preview_to_capture"
        private const val KEY_LIVE_CHART_CORNERS = "live_chart_corner_overlay"
        private const val KEY_STATIC_PREVIEW_ROT = "static_preview_rotation_deg"

        fun load(context: Context): PreviewChromePreferences {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = PreviewChromePreferences()
            return PreviewChromePreferences(
                maxBrightnessInPreview = prefs.getBoolean(KEY_MAX_BRIGHTNESS, defaults.maxBrightnessInPreview),
                dndWhileRecording = prefs.getBoolean(KEY_DND_RECORDING, defaults.dndWhileRecording),
                volumeKeysCapture = prefs.getBoolean(KEY_VOLUME_KEYS, defaults.volumeKeysCapture),
                saveLocationWithMedia = prefs.getBoolean(KEY_SAVE_LOCATION, defaults.saveLocationWithMedia),
                showOnScreenShutter = prefs.getBoolean(KEY_SHOW_SHUTTER, defaults.showOnScreenShutter),
                tapPreviewToCapture = prefs.getBoolean(KEY_TAP_PREVIEW_CAPTURE, defaults.tapPreviewToCapture),
                liveChartCornerOverlay = prefs.getBoolean(KEY_LIVE_CHART_CORNERS, defaults.liveChartCornerOverlay),
                staticPreviewRotationDeg = normalizeStaticRotation(
                    prefs.getInt(KEY_STATIC_PREVIEW_ROT, defaults.staticPreviewRotationDeg),
                ),
            )
        }

        fun save(context: Context, value: PreviewChromePreferences) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_MAX_BRIGHTNESS, value.maxBrightnessInPreview)
                .putBoolean(KEY_DND_RECORDING, value.dndWhileRecording)
                .putBoolean(KEY_VOLUME_KEYS, value.volumeKeysCapture)
                .putBoolean(KEY_SAVE_LOCATION, value.saveLocationWithMedia)
                .putBoolean(KEY_SHOW_SHUTTER, value.showOnScreenShutter)
                .putBoolean(KEY_TAP_PREVIEW_CAPTURE, value.tapPreviewToCapture)
                .putBoolean(KEY_LIVE_CHART_CORNERS, value.liveChartCornerOverlay)
                .putInt(KEY_STATIC_PREVIEW_ROT, normalizeStaticRotation(value.staticPreviewRotationDeg))
                .apply()
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
