package dev.pointandshoot

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

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
    /** Near-clip zebra (~0.95 luma) from YUV analysis; optional aid on highlight-hostile sensors. */
    val showHighlightClipZebra: Boolean = false,
    val showHighlightWeightedMeter: Boolean = true,
    val showEyeAfOverlay: Boolean = true,
    /** Sony-style horizon line on the preview (accelerometer). */
    val showHorizonLevel: Boolean = true,
    /**
     * Focus-peaking false color; [FocusPeakingColor.Off] disables the overlay.
     * Implemented in `lut_preview_external.frag.glsl` on the GL preview path.
     */
    val focusPeakingColor: FocusPeakingColor = FocusPeakingColor.Off,
    val focusPeakingStrength: FocusPeakingStrength = FocusPeakingStrength.Medium,
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

    /** True when a non-off peaking color is selected (preview overlay enabled when shader exists). */
    fun focusPeakingEnabled(): Boolean = focusPeakingColor != FocusPeakingColor.Off

    companion object {
        const val PREFS_NAME = "pns_hud_settings"

        private const val KEY_DIAL = "show_command_dial"
        private const val KEY_TIMECODE = "show_timecode"
        private const val KEY_TALLY = "show_video_tally"
        private const val KEY_FPS = "show_fps_readout"
        private const val KEY_ISO_SHUTTER = "show_iso_shutter_readout"
        private const val KEY_HISTOGRAM = "show_histogram"
        private const val KEY_HIGHLIGHT_CLIP_ZEBRA = "show_highlight_clip_zebra"
        private const val KEY_HIGHLIGHT_METER = "show_highlight_meter"
        private const val KEY_EYE_AF = "show_eye_af_overlay"
        private const val KEY_HORIZON = "show_horizon_level"
        private const val KEY_FOCUS_PEAKING = "show_focus_peaking"
        private const val KEY_FOCUS_PEAKING_COLOR = "focus_peaking_color"
        private const val KEY_FOCUS_PEAKING_STRENGTH = "focus_peaking_strength"
        private const val KEY_LUT_STILLS = "selected_lut_stills"
        private const val KEY_LUT_VIDEO = "selected_lut_video"
        private const val KEY_COMMAND_DIAL_MODE = "command_dial_mode"
        private const val KEY_IMAGING_PROFILE = "imaging_profile"

        /** Last imaging profile (Standard Pro vs Ultra-Max); persists across launches. */
        fun loadImagingProfile(context: Context): ImagingProfile {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_IMAGING_PROFILE, null)
            return ImagingProfile.byId(id)
        }

        fun saveImagingProfile(context: Context, profile: ImagingProfile) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_IMAGING_PROFILE, profile.id)
                .apply()
        }

        /** Last shooting-mode dial selection (persists across launches). */
        fun loadCommandDialMode(context: Context): CommandDialMode {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_COMMAND_DIAL_MODE, null) ?: return CommandDialMode.M
            return CommandDialMode.entries.firstOrNull { it.name == name } ?: CommandDialMode.M
        }

        fun saveCommandDialMode(context: Context, mode: CommandDialMode) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_COMMAND_DIAL_MODE, mode.name)
                .apply()
        }

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
                showHighlightClipZebra = prefs.getBoolean(KEY_HIGHLIGHT_CLIP_ZEBRA, defaults.showHighlightClipZebra),
                showHighlightWeightedMeter = prefs.getBoolean(KEY_HIGHLIGHT_METER, defaults.showHighlightWeightedMeter),
                showEyeAfOverlay = prefs.getBoolean(KEY_EYE_AF, defaults.showEyeAfOverlay),
                showHorizonLevel = prefs.getBoolean(KEY_HORIZON, defaults.showHorizonLevel),
                focusPeakingColor = loadFocusPeakingColor(prefs, defaults),
                focusPeakingStrength = loadFocusPeakingStrength(prefs, defaults),
                selectedLutForStills = prefs.getString(KEY_LUT_STILLS, defaults.selectedLutForStills) ?: defaults.selectedLutForStills,
                selectedLutForVideo = prefs.getString(KEY_LUT_VIDEO, defaults.selectedLutForVideo) ?: defaults.selectedLutForVideo,
            )
        }

        fun save(context: Context, settings: HudSettings) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // commit() so LUT + HUD toggles survive immediate process kill (apply() is async).
            prefs.edit()
                .putBoolean(KEY_DIAL, settings.showCommandDial)
                .putBoolean(KEY_TIMECODE, settings.showTimecode)
                .putBoolean(KEY_TALLY, settings.showVideoTally)
                .putBoolean(KEY_FPS, settings.showFpsReadout)
                .putBoolean(KEY_ISO_SHUTTER, settings.showIsoShutterReadout)
                .putBoolean(KEY_HISTOGRAM, settings.showHistogram)
                .putBoolean(KEY_HIGHLIGHT_CLIP_ZEBRA, settings.showHighlightClipZebra)
                .putBoolean(KEY_HIGHLIGHT_METER, settings.showHighlightWeightedMeter)
                .putBoolean(KEY_EYE_AF, settings.showEyeAfOverlay)
                .putBoolean(KEY_HORIZON, settings.showHorizonLevel)
                .putBoolean(KEY_FOCUS_PEAKING, settings.focusPeakingEnabled())
                .putString(KEY_FOCUS_PEAKING_COLOR, settings.focusPeakingColor.name)
                .putString(KEY_FOCUS_PEAKING_STRENGTH, settings.focusPeakingStrength.name)
                .putString(KEY_LUT_STILLS, settings.selectedLutForStills)
                .putString(KEY_LUT_VIDEO, settings.selectedLutForVideo)
                .commit()
        }

        private fun resolveLut(name: String): LutCatalog =
            LutCatalog.entries.firstOrNull { it.name == name } ?: LutCatalog.None

        private fun loadFocusPeakingColor(prefs: SharedPreferences, defaults: HudSettings): FocusPeakingColor {
            val stored = prefs.getString(KEY_FOCUS_PEAKING_COLOR, null)
            if (stored != null) {
                return FocusPeakingColor.entries.firstOrNull { it.name == stored }
                    ?: defaults.focusPeakingColor
            }
            // Migrate legacy boolean: any "on" maps to red (common default).
            return if (prefs.getBoolean(KEY_FOCUS_PEAKING, false)) {
                FocusPeakingColor.Red
            } else {
                defaults.focusPeakingColor
            }
        }

        private fun loadFocusPeakingStrength(prefs: SharedPreferences, defaults: HudSettings): FocusPeakingStrength {
            val stored = prefs.getString(KEY_FOCUS_PEAKING_STRENGTH, null) ?: return defaults.focusPeakingStrength
            return FocusPeakingStrength.entries.firstOrNull { it.name == stored }
                ?: defaults.focusPeakingStrength
        }
    }
}

/**
 * Compose-friendly accessor with bidirectional state. Updates flush to
 * [SharedPreferences] synchronously ([HudSettings.save] uses [commit]) so LUT/HUD
 * choices survive process death. Reloads from disk on each [Lifecycle.Event.ON_RESUME]
 * so reopening the app or returning from another screen picks up persisted LUTs.
 */
@Composable
fun rememberHudSettings(): HudSettingsState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var current by remember { mutableStateOf(HudSettings.load(appContext)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appContext) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    current = HudSettings.load(appContext)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(current) {
        HudSettingsState(
            current = current,
            update = { next ->
                HudSettings.save(appContext, next)
                current = next
            },
        )
    }
}

class HudSettingsState(
    val current: HudSettings,
    val update: (HudSettings) -> Unit,
)
