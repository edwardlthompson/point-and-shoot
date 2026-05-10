package dev.pointandshoot

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Granular Pro-HUD element toggles per BUILD_PLAN §5 (Phase 2): "Settings > HUD".
 *
 * Backed by [SharedPreferences] so settings persist across launches without
 * pulling in a new dependency (DataStore would also work; SharedPreferences
 * keeps APK size and dep surface minimal for this small flag set).
 *
 * Each flag has a hard-coded default that matches the BUILD_PLAN spec for the
 * "default Pro HUD" loadout. Toggles are read at composition time via
 * [rememberHudSettings] which observes preference changes.
 */
data class HudSettings(
    val showCommandDial: Boolean = true,
    val showTimecode: Boolean = true,
    val showVideoTally: Boolean = true,
    val showFpsReadout: Boolean = true,
    val showIsoShutterReadout: Boolean = true,
    val showHistogram: Boolean = false,        // disabled by default - cost vs benefit on small phones
    val showHighlightWeightedMeter: Boolean = true,
    val showEyeAfOverlay: Boolean = true,
    /** Sony-style horizon line on the preview (accelerometer). */
    val showHorizonLevel: Boolean = true,
    val showFocusPeaking: Boolean = false,     // requires NDK shader (Phase 1+) - off until then
    /**
     * Per-mode LUT memory per BUILD_PLAN \u00a77 ("HUD chip 'LUT' alongside the
     * imaging-profile selector; per-mode memory; 'None' (identity) is always
     * the default and survives app restart unless the user explicitly chose
     * otherwise"). Stored as the [LutCatalog] enum name so the schema is
     * stable across enum reorderings.
     */
    val selectedLutForStills: String = LutCatalog.None.name,
    val selectedLutForVideo: String = LutCatalog.None.name,
) {
    /** Resolve the currently-active stills LUT, falling back to None on rename / removal. */
    fun stillsLut(): LutCatalog = resolveLut(selectedLutForStills)

    /** Resolve the currently-active video LUT, falling back to None on rename / removal. */
    fun videoLut(): LutCatalog = resolveLut(selectedLutForVideo)

    companion object {
        const val PREFS_NAME = "pns_hud_settings"

        private const val KEY_DIAL = "show_command_dial"
        private const val KEY_TIMECODE = "show_timecode"
        private const val KEY_TALLY = "show_video_tally"
        private const val KEY_FPS = "show_fps_readout"
        private const val KEY_ISO_SHUTTER = "show_iso_shutter_readout"
        private const val KEY_HISTOGRAM = "show_histogram"
        private const val KEY_HIGHLIGHT_METER = "show_highlight_meter"
        private const val KEY_EYE_AF = "show_eye_af_overlay"
        private const val KEY_HORIZON = "show_horizon_level"
        private const val KEY_FOCUS_PEAKING = "show_focus_peaking"
        private const val KEY_LUT_STILLS = "selected_lut_stills"
        private const val KEY_LUT_VIDEO = "selected_lut_video"

        fun load(context: Context): HudSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = HudSettings()
            return HudSettings(
                showCommandDial = prefs.getBoolean(KEY_DIAL, defaults.showCommandDial),
                showTimecode = prefs.getBoolean(KEY_TIMECODE, defaults.showTimecode),
                showVideoTally = prefs.getBoolean(KEY_TALLY, defaults.showVideoTally),
                showFpsReadout = prefs.getBoolean(KEY_FPS, defaults.showFpsReadout),
                showIsoShutterReadout = prefs.getBoolean(KEY_ISO_SHUTTER, defaults.showIsoShutterReadout),
                showHistogram = prefs.getBoolean(KEY_HISTOGRAM, defaults.showHistogram),
                showHighlightWeightedMeter = prefs.getBoolean(KEY_HIGHLIGHT_METER, defaults.showHighlightWeightedMeter),
                showEyeAfOverlay = prefs.getBoolean(KEY_EYE_AF, defaults.showEyeAfOverlay),
                showHorizonLevel = prefs.getBoolean(KEY_HORIZON, defaults.showHorizonLevel),
                showFocusPeaking = prefs.getBoolean(KEY_FOCUS_PEAKING, defaults.showFocusPeaking),
                selectedLutForStills = prefs.getString(KEY_LUT_STILLS, defaults.selectedLutForStills) ?: defaults.selectedLutForStills,
                selectedLutForVideo = prefs.getString(KEY_LUT_VIDEO, defaults.selectedLutForVideo) ?: defaults.selectedLutForVideo,
            )
        }

        fun save(context: Context, settings: HudSettings) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_DIAL, settings.showCommandDial)
                .putBoolean(KEY_TIMECODE, settings.showTimecode)
                .putBoolean(KEY_TALLY, settings.showVideoTally)
                .putBoolean(KEY_FPS, settings.showFpsReadout)
                .putBoolean(KEY_ISO_SHUTTER, settings.showIsoShutterReadout)
                .putBoolean(KEY_HISTOGRAM, settings.showHistogram)
                .putBoolean(KEY_HIGHLIGHT_METER, settings.showHighlightWeightedMeter)
                .putBoolean(KEY_EYE_AF, settings.showEyeAfOverlay)
                .putBoolean(KEY_HORIZON, settings.showHorizonLevel)
                .putBoolean(KEY_FOCUS_PEAKING, settings.showFocusPeaking)
                .putString(KEY_LUT_STILLS, settings.selectedLutForStills)
                .putString(KEY_LUT_VIDEO, settings.selectedLutForVideo)
                .apply()
        }

        private fun resolveLut(name: String): LutCatalog =
            LutCatalog.entries.firstOrNull { it.name == name } ?: LutCatalog.None
    }
}

/**
 * Compose-friendly accessor with bidirectional state. Updates flush to
 * [SharedPreferences] immediately so changes survive process death.
 */
@Composable
fun rememberHudSettings(): HudSettingsState {
    val context = LocalContext.current
    var current by remember { mutableStateOf(HudSettings.load(context)) }

    LaunchedEffect(Unit) {
        current = HudSettings.load(context)
    }

    return remember(current) {
        HudSettingsState(
            current = current,
            update = { next ->
                HudSettings.save(context, next)
                current = next
            },
        )
    }
}

class HudSettingsState(
    val current: HudSettings,
    val update: (HudSettings) -> Unit,
)
