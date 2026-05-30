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
import kotlin.math.pow

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
    /** Sprint **13V.12**: battery drain + thermal chip on HFR / DCG video preview. */
    val showPowerThermalOverlay: Boolean = true,
    /** Sprint **13V.13**: minutes of video remaining at current encode bitrate on video preview. */
    val showStorageRemainingOverlay: Boolean = true,
    val showVideoTally: Boolean = true,
    val showFpsReadout: Boolean = true,
    val showIsoShutterReadout: Boolean = true,
    val showHistogram: Boolean = false,        // disabled by default - cost vs benefit on small phones
    /** Sprint 13.9: Extend luma histogram to RGB channels when enabled. Requires [showHistogram]. */
    val showRgbHistogram: Boolean = false,
    /** Sprint **15.34**: Luma histogram overlay while video-primary preview is recording. Off by default. */
    val showHistogramDuringVideo: Boolean = false,
    /** Near-clip zebra (~0.95 luma) from YUV analysis; optional aid on highlight-hostile sensors. */
    val showHighlightClipZebra: Boolean = false,
    /** Sprint **15.21** — [FalseColorMode.storageId]: off / zebra / false_color. */
    val falseColorMode: String = FalseColorMode.Off.storageId,
    /** Sprint **15.21** — zebra IRE % (75–100) → luma threshold via [PreviewLumaHistogram.irePercentToThresholdUnsigned]. */
    val zebraIreThreshold: Int = 95,
    val showHighlightWeightedMeter: Boolean = true,
    val showEyeAfOverlay: Boolean = true,
    /**
     * Sprint **14.5**: center crosshair on the preview tile for buffer↔view alignment vs face/eye overlays.
     * Off by default (developer HUD).
     */
    val showFaceAlignmentDebugCrosshair: Boolean = false,
    /** Sprint **13V.17**: auto still when ML Kit smile probability exceeds threshold (photo mode). */
    val enableSmileTriggeredStill: Boolean = false,
    /** Sprint **13V.17**: show OEM scene/quality vendor keys in readout when probed. */
    val showSceneVendorHints: Boolean = false,
    /**
     * Sprint **13V.17**: manual video bitrate scale (**50–150%** of probe table). **100** = default.
     */
    val videoBitrateScalePercent: Int = 100,
    /** Sony-style horizon line on the preview (accelerometer). */
    val showHorizonLevel: Boolean = true,
    /**
     * Focus-peaking false color; [FocusPeakingColor.Off] disables the overlay.
     * Implemented in `lut_preview_external.frag.glsl` on the GL preview path — **edge-based** luma
     * gradients, not a Camera2 AF confirmation (tele can show peaks while RAW is slightly soft).
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
    /**
     * When true and the HAL lists a non-OFF optical mode, preview + still requests set
     * [android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE] to ON.
     */
    val enableLensOpticalStabilization: Boolean = true,
    /**
     * When true, **still** [CaptureRequest]s force optical stabilization OFF when the HAL lists OFF,
     * while preview may still request OIS if [enableLensOpticalStabilization] is on. OEM-dependent
     * aid for tripod / static scenes where sensor-shift can look like motion blur.
     */
    val disableOisForStillCapture: Boolean = false,
    /**
     * Preview-only electronic stabilization ([android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE]).
     * Off by default (latency / crop); skipped for HFR (AE target range upper ≥ 120) and still captures.
     */
    val enableVideoStabilizationPreview: Boolean = false,
    /**
     * When true, RAW still requests may set [android.hardware.camera2.CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST]
     * to the midpoint of the advertised range. Off by default; never applied when manual ISO or exposure overrides are active.
     */
    val enablePostRawSensitivityBoost: Boolean = false,
    /**
     * When true and the device supports Camera2 auto-framing (API 35+), preview requests may set
     * [android.hardware.camera2.CaptureRequest.CONTROL_AUTOFRAMING] to ON. Off by default.
     */
    val enableAutoFraming: Boolean = false,
    /**
     * When true, REGULAR preview session creation probes [android.hardware.camera2.params.OutputConfiguration.setDynamicRangeProfile]
     * on the preview output (recommended 10-bit first when advertised) only if [android.hardware.camera2.CameraDevice.isSessionConfigurationSupported]
     * accepts the full surface list. Off by default (Milestone 4 HDR preview).
     */
    val enableHdr10LivePreview: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableAFBracketing`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters]. **Off by default**
     * (matrix / HAL risk); Milestone **10.6** Phase C.
     */
    val enableResearchAfBracketing: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableAICameraHSR`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters] to enable
     * AI Camera High Speed Recording (HFR). **Off by default** (matrix / HAL risk);
     * Milestone 13.3 research for 120fps video.
     */
    val enableResearchHfrAICameraHSR: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableVIULL`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters] to enable
     * Video ISP Ultra Low Latency mode (critical for HFR). **Off by default** (matrix / HAL risk);
     * Milestone 13.3 research for 120fps video.
     */
    val enableResearchHfrVIULL: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableVSR`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters] to enable
     * Video Stabilization Rotation (may be required for HFR). **Off by default** (matrix / HAL risk);
     * Milestone 13.3 research for 120fps video.
     */
    val enableResearchHfrVSR: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters] to enable
     * Dual Conversion Gain mode for HDR video. **Off by default** (matrix / HAL risk);
     * Milestone 13.2 research for 10-bit video.
     */
    val enableResearchDcgHDR: Boolean = false,
    /**
     * Research-only: when a device advertises Qualcomm **session** vendor key
     * `org.codeaurora.qcamera3.sessionParameters.EnableQHDR`, REGULAR preview session
     * creation may attach it via [SessionConfiguration.setSessionParameters] to enable
     * Qualcomm HDR mode for 10-bit video. **Off by default** (matrix / HAL risk);
     * Milestone 13.2 research for 10-bit video.
     */
    val enableResearchQHDR: Boolean = false,
    /**
     * In-app RAW still only (no scripted ADB label): after [stopRepeating] debounce, run preview-only
     * AF settle captures before the high-res still (Open Camera–style polling). **Off by default**;
     * requires flash off and non-manual sensor. USB-prove before treating as default-on.
     */
    val enableOpenCameraStyleAfSettleBeforeStill: Boolean = false,
    /**
     * In-app still only: after shutter tap, run AF precapture triggers and **wait** (with timeout)
     * until preview [android.hardware.camera2.CaptureResult.CONTROL_AF_STATE] is passive focused or
     * focused locked before starting RAW/JPEG still capture. Skipped for manual sensor, S dial,
     * flash paths that need OEM precapture freedom, and high-speed constrained sessions.
     */
    val waitForAfFocusBeforeStill: Boolean = false,
    /**
     * Discrete ISP bias for hardware JPEG / preview paths (-2 softer … +2 sharper).
     * Engine maps to advertised EDGE / NR / tonemap / color-correction modes ([PreviewJpegProcessingHints]).
     */
    val hardwareJpegIspBias: Int = 0,
    /**
     * Software re-encode quality for RAW companion JPEG path ([Bitmap.compress]); clamped 70–100 in engine.
     */
    val softwareJpegCompanionQuality: Int = 92,
    /**
     * Milestone **13.8** still path: **Standard** (default), **ZslStill** (13.8b), **HdrStill** (13.8c bracket burst).
     * ADB `pns_preview_still_mode` overrides when set on cold preview launch.
     */
    val stillCaptureMode: StillCaptureMode = StillCaptureMode.Standard,
    /**
     * Milestone **13.6** video encode lane. **Raw** is OP13-only via fleet policy; mutually exclusive
     * with research DCG HDR session in UI.
     */
    val videoEncodeLane: VideoEncodeLane = VideoEncodeLane.Encoded,
    /** Sprint **CC.1** — when true, shutter fires a burst ([burstShotCount] at [burstIntervalMs]). */
    val burstModeEnabled: Boolean = false,
    val burstShotCount: Int = 5,
    val burstIntervalMs: Int = 350,
    /** Sprint **15.29** — Night dial stack depth (4 / 6 / 8 JPEG frames). */
    val nightScapeFrameCount: Int = 6,
    /** Sprint **CC.1** — 0 = off; paired with [intervalometerRunning] on preview. */
    val intervalometerIntervalSec: Int = 0,
    val intervalometerRunning: Boolean = false,
    /** Sprint **CC.1** — preview RAW ring for ZSL / moment-before capture ([ZslStillFrameRing]). */
    val preCaptureBufferEnabled: Boolean = false,
    /** Sprint **CC.3** — last applied [ProPictureProfile.id] (null = custom LUT mix only). */
    val selectedPictureProfileId: String? = null,
    /** Sprint **CC.3** — loopback HTTP tether on port [TetheredCaptureServer.DEFAULT_PORT]. */
    val tetheredCaptureEnabled: Boolean = false,
    /** Sprint **15.37** — bind tether on **0.0.0.0** + NSD `_pns-tether._tcp` for LAN / Wi‑Fi Direct. */
    val wifiDirectTetherEnabled: Boolean = false,
    /**
     * Sprint **CC.3** — scales [CaptureRequest.FLASH_STRENGTH_LEVEL] when HAL advertises strength (API 35+).
     * **100** = HAL default clamped to max; **25** = minimum useful level.
     */
    val previewFlashStrengthPercent: Int = 100,
    /** Sprint **15.11** — video shutter-angle preset (see [VideoShutterAngle]). */
    val videoShutterAngle: String = VideoShutterAngle.Free.name,
    /** Sprint **15.16** — preview + encoder color intent ([VideoColorProfile]). */
    val videoColorProfile: String = VideoColorProfile.Sdr.storageId,
    /** Sprint **15.23** — timecode + thermal + PPM in 16:9 letterbox pillars while recording. */
    val showVideoPillarHud: Boolean = true,
    /** Sprint **15.24** — in-app video [AudioRecord] / MediaRecorder source. */
    val videoAudioSource: String = VideoAudioSource.Camcorder.storageId,
    /** Sprint **15.25** — wind noise filter (NS + AEC) when source is Camcorder. */
    val windNoiseFilterEnabled: Boolean = false,
    /** M19.4 — anamorphic desqueeze metadata on encode (viewers read SAR). */
    val anamorphicDesqueezeEnabled: Boolean = false,
    val anamorphicSqueezeFactor: Double = AnamorphicVideoMetadata.DEFAULT_SQUEEZE,
    /** Sprint **15.35** — in-app video mic gain in dB (−12…+12, 0.5 step). Applied in MediaCodec PCM loop. */
    val audioGainDb: Float = 0f,
    /** Sprint **15.27** — intervalometer output: still sequence vs H.264 MP4. */
    val timeLapseMode: String = TimeLapseMode.Off.storageId,
    /** Sprint **15.28** — tele manual-focus breathing crop compensation (M dial). */
    val enableFocusBreathingComp: Boolean = false,
    val focusBreathingCompK: Float = 0.005f,
    /** Sprint **15.36** — rack focus waypoint diopters (null = unset). */
    val rackFocusWaypointNear: Float? = null,
    val rackFocusWaypointFar: Float? = null,
    val rackFocusDurationMs: Int = RackFocusPull.DEFAULT_DURATION_MS,
    /** Sprint **15.38** — dual-ISO HDR video (experimental; requires multi-res stream map). */
    val dualIsoVideoEnabled: Boolean = false,
) {
    fun videoShutterAngleEnum(): VideoShutterAngle = VideoShutterAngle.fromStorage(videoShutterAngle)

    fun videoColorProfileEnum(): VideoColorProfile = VideoColorProfile.fromStorage(videoColorProfile)

    fun videoAudioSourceEnum(): VideoAudioSource = VideoAudioSource.fromStorage(videoAudioSource)

    fun timeLapseModeEnum(): TimeLapseMode = TimeLapseMode.fromStorage(timeLapseMode)

    /** True when wind NS/AEC may attach on the in-app video mic path. */
    fun windNoiseFilterActive(): Boolean =
        windNoiseFilterEnabled && videoAudioSourceEnum() == VideoAudioSource.Camcorder

    /** True when dual-ISO video may attach on session create (HAL multi-res map required). */
    fun dualIsoVideoActive(multiResSupported: Boolean): Boolean =
        dualIsoVideoEnabled && DualIsoVideoMerger.isSupportedOnDevice(multiResSupported)

    /** Linear PCM multiplier for [audioGainDb] (20·log10). */
    fun audioGainLinear(): Float = audioGainDbToLinear(coerceAudioGainDb(audioGainDb))

    fun falseColorModeEnum(): FalseColorMode = FalseColorMode.fromStorage(falseColorMode)

    fun wantsHighlightClipZebraEffective(): Boolean =
        showHighlightClipZebra || falseColorModeEnum().wantsZebra()

    /** YUV histogram analysis in preview (photo [showHistogram] or video [showHistogramDuringVideo]). */
    fun wantsPreviewHistogramPipeline(primaryPhoto: Boolean): Boolean =
        showHistogram || (!primaryPhoto && showHistogramDuringVideo)

    /** Finder overlay: photo when [showHistogram]; video only while recording. */
    fun wantsHistogramOverlayVisible(primaryPhoto: Boolean, isRecording: Boolean): Boolean =
        showHistogram || (!primaryPhoto && showHistogramDuringVideo && isRecording)
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
        private const val KEY_POWER_THERMAL = "show_power_thermal_overlay"
        private const val KEY_STORAGE_REMAINING = "show_storage_remaining_overlay"
        private const val KEY_TALLY = "show_video_tally"
        private const val KEY_FPS = "show_fps_readout"
        private const val KEY_ISO_SHUTTER = "show_iso_shutter_readout"
        private const val KEY_HISTOGRAM = "show_histogram"
        private const val KEY_HISTOGRAM_DURING_VIDEO = "show_histogram_during_video"
        private const val KEY_RGB_HISTOGRAM = "show_rgb_histogram"
        private const val KEY_HIGHLIGHT_CLIP_ZEBRA = "show_highlight_clip_zebra"
        private const val KEY_FALSE_COLOR_MODE = "false_color_mode"
        private const val KEY_ZEBRA_IRE_THRESHOLD = "zebra_ire_threshold"
        private const val KEY_HIGHLIGHT_METER = "show_highlight_meter"
        private const val KEY_EYE_AF = "show_eye_af_overlay"
        private const val KEY_FACE_ALIGN_CROSSHAIR = "show_face_alignment_debug_crosshair"
        private const val KEY_HORIZON = "show_horizon_level"
        private const val KEY_FOCUS_PEAKING = "show_focus_peaking"
        private const val KEY_FOCUS_PEAKING_COLOR = "focus_peaking_color"
        private const val KEY_FOCUS_PEAKING_STRENGTH = "focus_peaking_strength"
        private const val KEY_LUT_STILLS = "selected_lut_stills"
        private const val KEY_LUT_VIDEO = "selected_lut_video"
        private const val KEY_LENS_OIS = "enable_lens_optical_stabilization"
        private const val KEY_DISABLE_OIS_STILL = "disable_ois_for_still_capture"
        private const val KEY_VIDEO_STAB_PREVIEW = "enable_video_stabilization_preview"
        private const val KEY_POST_RAW_BOOST = "enable_post_raw_sensitivity_boost"
        private const val KEY_AUTO_FRAMING = "enable_auto_framing"
        private const val KEY_HDR_10_PREVIEW = "enable_hdr10_live_preview"
        private const val KEY_RESEARCH_AF_BRACKET = "enable_research_af_bracket"
        private const val KEY_RESEARCH_HFR_AI_CAMERA_HSR = "enable_research_hfr_ai_camera_hsr"
        private const val KEY_RESEARCH_HFR_VIULL = "enable_research_hfr_viull"
        private const val KEY_RESEARCH_HFR_VSR = "enable_research_hfr_vsr"
        private const val KEY_RESEARCH_DCG_HDR = "enable_research_dcg_hdr"
        private const val KEY_RESEARCH_QHDR = "enable_research_qhdr"
        private const val KEY_AF_SETTLE_BEFORE_STILL = "enable_open_camera_style_af_settle_before_still"
        private const val KEY_WAIT_AF_FOCUS_BEFORE_STILL = "wait_for_af_focus_before_still"
        private const val KEY_HARDWARE_JPEG_ISP_BIAS = "hardware_jpeg_isp_bias"
        private const val KEY_SOFTWARE_JPEG_QUALITY = "software_jpeg_companion_quality"
        private const val KEY_BRACKET_PATTERN = "bracket_pattern_last"
        private const val KEY_STILL_CAPTURE_MODE = "still_capture_mode"
        private const val KEY_VIDEO_ENCODE_LANE = "video_encode_lane"
        private const val KEY_BURST_MODE = "burst_mode_enabled"
        private const val KEY_BURST_COUNT = "burst_shot_count"
        private const val KEY_BURST_INTERVAL_MS = "burst_interval_ms"
        private const val KEY_NIGHTSCAPE_FRAME_COUNT = "nightscape_frame_count"
        private const val KEY_INTERVALOMETER_SEC = "intervalometer_interval_sec"
        private const val KEY_INTERVALOMETER_RUNNING = "intervalometer_running"
        private const val KEY_PRECAPTURE_BUFFER = "pre_capture_buffer_enabled"
        private const val KEY_PICTURE_PROFILE = "selected_picture_profile_id"
        private const val KEY_TETHERED_CAPTURE = "tethered_capture_enabled"
        private const val KEY_WIFI_DIRECT_TETHER = "wifi_direct_tether_enabled"
        private const val KEY_PREVIEW_FLASH_STRENGTH = "preview_flash_strength_percent"
        private const val KEY_VIDEO_SHUTTER_ANGLE = "video_shutter_angle"
        private const val KEY_VIDEO_COLOR_PROFILE = "video_color_profile"
        private const val KEY_VIDEO_PILLAR_HUD = "show_video_pillar_hud"
        private const val KEY_VIDEO_AUDIO_SOURCE = "video_audio_source"
        private const val KEY_WIND_NOISE_FILTER = "wind_noise_filter_enabled"
        private const val KEY_AUDIO_GAIN_DB = "video_audio_gain_db"
        private const val KEY_TIME_LAPSE_MODE = "time_lapse_mode"
        private const val KEY_FOCUS_BREATHING_COMP = "enable_focus_breathing_comp"
        private const val KEY_FOCUS_BREATHING_K = "focus_breathing_comp_k"
        private const val KEY_RACK_WP_NEAR_SET = "rack_focus_wp_near_set"
        private const val KEY_RACK_WP_NEAR = "rack_focus_wp_near"
        private const val KEY_RACK_WP_FAR_SET = "rack_focus_wp_far_set"
        private const val KEY_RACK_WP_FAR = "rack_focus_wp_far"
        private const val KEY_RACK_DURATION_MS = "rack_focus_duration_ms"
        private const val KEY_DUAL_ISO_VIDEO = "dual_iso_video_enabled"
        private const val KEY_ANAMORPHIC_DESQUEEZE = "anamorphic_desqueeze_enabled"
        private const val KEY_ANAMORPHIC_SQUEEZE = "anamorphic_squeeze_factor"
        const val FOCUS_BREATHING_K_MIN = 0.001f
        const val FOCUS_BREATHING_K_MAX = 0.02f
        private const val KEY_SMILE_STILL = "enable_smile_triggered_still"
        private const val KEY_SCENE_VENDOR_HINTS = "show_scene_vendor_hints"
        private const val KEY_VIDEO_BITRATE_SCALE = "video_bitrate_scale_percent"

        const val VIDEO_BITRATE_SCALE_MIN = 50
        const val VIDEO_BITRATE_SCALE_MAX = 150

        const val AUDIO_GAIN_DB_MIN = -12f
        const val AUDIO_GAIN_DB_MAX = 12f

        /** Round to nearest 0.5 dB and clamp to [AUDIO_GAIN_DB_MIN]…[AUDIO_GAIN_DB_MAX]. */
        fun coerceAudioGainDb(db: Float): Float {
            val stepped = kotlin.math.round(db * 2f) / 2f
            return stepped.coerceIn(AUDIO_GAIN_DB_MIN, AUDIO_GAIN_DB_MAX)
        }

        /** dB → linear gain for PCM multiply (0 dB = 1.0). */
        fun audioGainDbToLinear(db: Float): Float =
            10.0.pow((coerceAudioGainDb(db) / 20.0).toDouble()).toFloat()

        const val PREVIEW_FLASH_STRENGTH_MIN = 25
        const val PREVIEW_FLASH_STRENGTH_MAX = 100

        const val SOFTWARE_JPEG_COMPANION_QUALITY_MIN = 70
        const val SOFTWARE_JPEG_COMPANION_QUALITY_MAX = 100

        private const val HARDWARE_JPEG_ISP_BIAS_MIN = -2
        private const val HARDWARE_JPEG_ISP_BIAS_MAX = 2
        private const val KEY_COMMAND_DIAL_MODE = "command_dial_mode"
        private const val KEY_IMAGING_PROFILE = "imaging_profile"
        private const val KEY_IMG_RAW_TIER = "img_menu_raw_tier"
        private const val KEY_IMG_JPEG_TIER = "img_menu_jpeg_tier"
        private const val KEY_IMG_HDR_WHEN_JPEG_OFF = "img_menu_hdr_when_jpeg_off"
        private const val KEY_LAST_RAW_IMAGING_PROFILE_ID = "last_raw_imaging_profile_id"

        /** Last Standard / Ultra raw choice for BKT auto-exit from JPEG-only. */
        fun loadLastRawImagingProfileId(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_LAST_RAW_IMAGING_PROFILE_ID, ImagingProfile.StandardPro.id)
                ?: ImagingProfile.StandardPro.id
        }

        fun saveLastRawImagingProfileId(context: Context, id: String) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_RAW_IMAGING_PROFILE_ID, id)
                .apply()
        }

        fun loadComposedStillIntent(
            context: Context,
            jpegCompanionOn: Boolean,
        ): ComposedStillIntent {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawS = prefs.getString(KEY_IMG_RAW_TIER, null)
            if (rawS == null) {
                return ComposedStillIntent.fromLegacyImagingProfile(loadImagingProfile(context), jpegCompanionOn)
            }
            val raw = runCatching { ImgMenuTier.valueOf(rawS) }.getOrElse { ImgMenuTier.Standard }
            val jpeg = runCatching { ImgMenuTier.valueOf(prefs.getString(KEY_IMG_JPEG_TIER, ImgMenuTier.Standard.name)!!) }
                .getOrElse { ImgMenuTier.Standard }
            val hdrOff = runCatching {
                ImgMenuTier.valueOf(prefs.getString(KEY_IMG_HDR_WHEN_JPEG_OFF, ImgMenuTier.Standard.name)!!)
            }.getOrElse { ImgMenuTier.Standard }
            val hdrWhenOff = if (hdrOff == ImgMenuTier.Off) ImgMenuTier.Standard else hdrOff
            return ComposedStillIntent(raw = raw, jpeg = jpeg, hdrWhenJpegOff = hdrWhenOff)
        }

        fun saveComposedStillIntent(context: Context, intent: ComposedStillIntent) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_IMG_RAW_TIER, intent.raw.name)
                .putString(KEY_IMG_JPEG_TIER, intent.jpeg.name)
                .putString(KEY_IMG_HDR_WHEN_JPEG_OFF, intent.hdrWhenJpegOff.name)
                .putString(KEY_IMAGING_PROFILE, intent.storageProfile().id)
                .apply()
        }

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

        fun loadBracketPattern(context: Context): BracketPattern {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_BRACKET_PATTERN, null) ?: return BracketPattern.Three
            return BracketPattern.entries.firstOrNull { it.name == name } ?: BracketPattern.Three
        }

        fun saveBracketPattern(context: Context, pattern: BracketPattern) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BRACKET_PATTERN, pattern.name)
                .apply()
        }

        fun loadStillCaptureMode(context: Context): StillCaptureMode {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_STILL_CAPTURE_MODE, null) ?: return StillCaptureMode.Standard
            return StillCaptureMode.entries.firstOrNull { it.name == name } ?: StillCaptureMode.Standard
        }

        fun saveStillCaptureMode(context: Context, mode: StillCaptureMode) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STILL_CAPTURE_MODE, mode.name)
                .apply()
        }

        fun load(context: Context): HudSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = HudSettings()
            return HudSettings(
                showCommandDial = prefs.getBoolean(KEY_DIAL, defaults.showCommandDial),
                showTimecode = prefs.getBoolean(KEY_TIMECODE, defaults.showTimecode),
                showPowerThermalOverlay =
                    prefs.getBoolean(KEY_POWER_THERMAL, defaults.showPowerThermalOverlay),
                showStorageRemainingOverlay =
                    prefs.getBoolean(KEY_STORAGE_REMAINING, defaults.showStorageRemainingOverlay),
                showVideoTally = prefs.getBoolean(KEY_TALLY, defaults.showVideoTally),
                showFpsReadout = prefs.getBoolean(KEY_FPS, defaults.showFpsReadout),
                showIsoShutterReadout = prefs.getBoolean(KEY_ISO_SHUTTER, defaults.showIsoShutterReadout),
                showHistogram = prefs.getBoolean(KEY_HISTOGRAM, defaults.showHistogram),
                showHistogramDuringVideo =
                    prefs.getBoolean(KEY_HISTOGRAM_DURING_VIDEO, defaults.showHistogramDuringVideo),
                showRgbHistogram = prefs.getBoolean(KEY_RGB_HISTOGRAM, defaults.showRgbHistogram),
                showHighlightClipZebra = prefs.getBoolean(KEY_HIGHLIGHT_CLIP_ZEBRA, defaults.showHighlightClipZebra),
                falseColorMode =
                    prefs.getString(KEY_FALSE_COLOR_MODE, defaults.falseColorMode)
                        ?: defaults.falseColorMode,
                zebraIreThreshold =
                    prefs.getInt(KEY_ZEBRA_IRE_THRESHOLD, defaults.zebraIreThreshold)
                        .coerceIn(PreviewLumaHistogram.IRE_MIN, PreviewLumaHistogram.IRE_MAX),
                showHighlightWeightedMeter = prefs.getBoolean(KEY_HIGHLIGHT_METER, defaults.showHighlightWeightedMeter),
                showEyeAfOverlay = prefs.getBoolean(KEY_EYE_AF, defaults.showEyeAfOverlay),
                showFaceAlignmentDebugCrosshair =
                    prefs.getBoolean(KEY_FACE_ALIGN_CROSSHAIR, defaults.showFaceAlignmentDebugCrosshair),
                enableSmileTriggeredStill = prefs.getBoolean(KEY_SMILE_STILL, defaults.enableSmileTriggeredStill),
                showSceneVendorHints = prefs.getBoolean(KEY_SCENE_VENDOR_HINTS, defaults.showSceneVendorHints),
                videoBitrateScalePercent =
                    prefs.getInt(KEY_VIDEO_BITRATE_SCALE, defaults.videoBitrateScalePercent)
                        .coerceIn(VIDEO_BITRATE_SCALE_MIN, VIDEO_BITRATE_SCALE_MAX),
                showHorizonLevel = prefs.getBoolean(KEY_HORIZON, defaults.showHorizonLevel),
                focusPeakingColor = loadFocusPeakingColor(prefs, defaults),
                focusPeakingStrength = loadFocusPeakingStrength(prefs, defaults),
                selectedLutForStills = prefs.getString(KEY_LUT_STILLS, defaults.selectedLutForStills) ?: defaults.selectedLutForStills,
                selectedLutForVideo = prefs.getString(KEY_LUT_VIDEO, defaults.selectedLutForVideo) ?: defaults.selectedLutForVideo,
                enableLensOpticalStabilization = prefs.getBoolean(KEY_LENS_OIS, defaults.enableLensOpticalStabilization),
                disableOisForStillCapture = prefs.getBoolean(KEY_DISABLE_OIS_STILL, defaults.disableOisForStillCapture),
                enableVideoStabilizationPreview = prefs.getBoolean(KEY_VIDEO_STAB_PREVIEW, defaults.enableVideoStabilizationPreview),
                enablePostRawSensitivityBoost = prefs.getBoolean(KEY_POST_RAW_BOOST, defaults.enablePostRawSensitivityBoost),
                enableAutoFraming = prefs.getBoolean(KEY_AUTO_FRAMING, defaults.enableAutoFraming),
                enableHdr10LivePreview = prefs.getBoolean(KEY_HDR_10_PREVIEW, defaults.enableHdr10LivePreview),
                enableResearchAfBracketing =
                    prefs.getBoolean(KEY_RESEARCH_AF_BRACKET, defaults.enableResearchAfBracketing),
                enableResearchHfrAICameraHSR =
                    prefs.getBoolean(KEY_RESEARCH_HFR_AI_CAMERA_HSR, defaults.enableResearchHfrAICameraHSR),
                enableResearchHfrVIULL =
                    prefs.getBoolean(KEY_RESEARCH_HFR_VIULL, defaults.enableResearchHfrVIULL),
                enableResearchHfrVSR =
                    prefs.getBoolean(KEY_RESEARCH_HFR_VSR, defaults.enableResearchHfrVSR),
                enableResearchDcgHDR =
                    prefs.getBoolean(KEY_RESEARCH_DCG_HDR, defaults.enableResearchDcgHDR),
                enableResearchQHDR =
                    prefs.getBoolean(KEY_RESEARCH_QHDR, defaults.enableResearchQHDR),
                enableOpenCameraStyleAfSettleBeforeStill =
                    prefs.getBoolean(KEY_AF_SETTLE_BEFORE_STILL, defaults.enableOpenCameraStyleAfSettleBeforeStill),
                waitForAfFocusBeforeStill =
                    prefs.getBoolean(KEY_WAIT_AF_FOCUS_BEFORE_STILL, defaults.waitForAfFocusBeforeStill),
                hardwareJpegIspBias = prefs.getInt(KEY_HARDWARE_JPEG_ISP_BIAS, defaults.hardwareJpegIspBias)
                    .coerceIn(HARDWARE_JPEG_ISP_BIAS_MIN, HARDWARE_JPEG_ISP_BIAS_MAX),
                softwareJpegCompanionQuality =
                    prefs.getInt(KEY_SOFTWARE_JPEG_QUALITY, defaults.softwareJpegCompanionQuality)
                        .coerceIn(SOFTWARE_JPEG_COMPANION_QUALITY_MIN, SOFTWARE_JPEG_COMPANION_QUALITY_MAX),
                stillCaptureMode = loadStillCaptureMode(context),
                videoEncodeLane = loadVideoEncodeLane(context),
                burstModeEnabled = prefs.getBoolean(KEY_BURST_MODE, defaults.burstModeEnabled),
                burstShotCount =
                    AdvancedCaptureSettings.normalizeBurstCount(
                        prefs.getInt(KEY_BURST_COUNT, defaults.burstShotCount),
                    ),
                burstIntervalMs =
                    AdvancedCaptureSettings.normalizeBurstIntervalMs(
                        prefs.getInt(KEY_BURST_INTERVAL_MS, defaults.burstIntervalMs),
                    ),
                nightScapeFrameCount =
                    AdvancedCaptureSettings.normalizeNightScapeFrameCount(
                        prefs.getInt(KEY_NIGHTSCAPE_FRAME_COUNT, defaults.nightScapeFrameCount),
                    ),
                intervalometerIntervalSec =
                    AdvancedCaptureSettings.normalizeIntervalometerSec(
                        prefs.getInt(KEY_INTERVALOMETER_SEC, defaults.intervalometerIntervalSec),
                    ),
                preCaptureBufferEnabled =
                    prefs.getBoolean(KEY_PRECAPTURE_BUFFER, defaults.preCaptureBufferEnabled),
                intervalometerRunning =
                    prefs.getBoolean(KEY_INTERVALOMETER_RUNNING, defaults.intervalometerRunning),
                selectedPictureProfileId =
                    prefs.getString(KEY_PICTURE_PROFILE, defaults.selectedPictureProfileId),
                tetheredCaptureEnabled =
                    prefs.getBoolean(KEY_TETHERED_CAPTURE, defaults.tetheredCaptureEnabled),
                wifiDirectTetherEnabled =
                    prefs.getBoolean(KEY_WIFI_DIRECT_TETHER, defaults.wifiDirectTetherEnabled),
                previewFlashStrengthPercent =
                    prefs.getInt(KEY_PREVIEW_FLASH_STRENGTH, defaults.previewFlashStrengthPercent)
                        .coerceIn(PREVIEW_FLASH_STRENGTH_MIN, PREVIEW_FLASH_STRENGTH_MAX),
                videoShutterAngle =
                    prefs.getString(KEY_VIDEO_SHUTTER_ANGLE, defaults.videoShutterAngle)
                        ?: defaults.videoShutterAngle,
                videoColorProfile =
                    prefs.getString(KEY_VIDEO_COLOR_PROFILE, defaults.videoColorProfile)
                        ?: defaults.videoColorProfile,
                showVideoPillarHud =
                    prefs.getBoolean(KEY_VIDEO_PILLAR_HUD, defaults.showVideoPillarHud),
                videoAudioSource =
                    prefs.getString(KEY_VIDEO_AUDIO_SOURCE, defaults.videoAudioSource)
                        ?: defaults.videoAudioSource,
                windNoiseFilterEnabled =
                    prefs.getBoolean(KEY_WIND_NOISE_FILTER, defaults.windNoiseFilterEnabled),
                audioGainDb =
                    coerceAudioGainDb(prefs.getFloat(KEY_AUDIO_GAIN_DB, defaults.audioGainDb)),
                timeLapseMode =
                    prefs.getString(KEY_TIME_LAPSE_MODE, defaults.timeLapseMode)
                        ?: defaults.timeLapseMode,
                enableFocusBreathingComp =
                    prefs.getBoolean(KEY_FOCUS_BREATHING_COMP, defaults.enableFocusBreathingComp),
                focusBreathingCompK =
                    prefs.getFloat(KEY_FOCUS_BREATHING_K, defaults.focusBreathingCompK)
                        .coerceIn(FOCUS_BREATHING_K_MIN, FOCUS_BREATHING_K_MAX),
                rackFocusWaypointNear =
                    if (prefs.getBoolean(KEY_RACK_WP_NEAR_SET, false)) {
                        prefs.getFloat(KEY_RACK_WP_NEAR, 0f)
                    } else {
                        null
                    },
                rackFocusWaypointFar =
                    if (prefs.getBoolean(KEY_RACK_WP_FAR_SET, false)) {
                        prefs.getFloat(KEY_RACK_WP_FAR, 0f)
                    } else {
                        null
                    },
                rackFocusDurationMs =
                    RackFocusPull.coerceDurationMs(
                        prefs.getInt(KEY_RACK_DURATION_MS, defaults.rackFocusDurationMs),
                    ),
                dualIsoVideoEnabled =
                    prefs.getBoolean(KEY_DUAL_ISO_VIDEO, defaults.dualIsoVideoEnabled),
                anamorphicDesqueezeEnabled =
                    prefs.getBoolean(KEY_ANAMORPHIC_DESQUEEZE, defaults.anamorphicDesqueezeEnabled),
                anamorphicSqueezeFactor =
                    prefs.getFloat(KEY_ANAMORPHIC_SQUEEZE, defaults.anamorphicSqueezeFactor.toFloat()).toDouble(),
            )
        }

        fun save(context: Context, settings: HudSettings) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // commit() so LUT + HUD toggles survive immediate process kill (apply() is async).
            prefs.edit()
                .putBoolean(KEY_DIAL, settings.showCommandDial)
                .putBoolean(KEY_TIMECODE, settings.showTimecode)
                .putBoolean(KEY_POWER_THERMAL, settings.showPowerThermalOverlay)
                .putBoolean(KEY_STORAGE_REMAINING, settings.showStorageRemainingOverlay)
                .putBoolean(KEY_TALLY, settings.showVideoTally)
                .putBoolean(KEY_FPS, settings.showFpsReadout)
                .putBoolean(KEY_ISO_SHUTTER, settings.showIsoShutterReadout)
                .putBoolean(KEY_HISTOGRAM, settings.showHistogram)
                .putBoolean(KEY_HISTOGRAM_DURING_VIDEO, settings.showHistogramDuringVideo)
                .putBoolean(KEY_RGB_HISTOGRAM, settings.showRgbHistogram)
                .putBoolean(KEY_HIGHLIGHT_CLIP_ZEBRA, settings.showHighlightClipZebra)
                .putString(KEY_FALSE_COLOR_MODE, settings.falseColorModeEnum().storageId)
                .putInt(
                    KEY_ZEBRA_IRE_THRESHOLD,
                    settings.zebraIreThreshold.coerceIn(
                        PreviewLumaHistogram.IRE_MIN,
                        PreviewLumaHistogram.IRE_MAX,
                    ),
                )
                .putBoolean(KEY_HIGHLIGHT_METER, settings.showHighlightWeightedMeter)
                .putBoolean(KEY_EYE_AF, settings.showEyeAfOverlay)
                .putBoolean(KEY_FACE_ALIGN_CROSSHAIR, settings.showFaceAlignmentDebugCrosshair)
                .putBoolean(KEY_SMILE_STILL, settings.enableSmileTriggeredStill)
                .putBoolean(KEY_SCENE_VENDOR_HINTS, settings.showSceneVendorHints)
                .putInt(
                    KEY_VIDEO_BITRATE_SCALE,
                    settings.videoBitrateScalePercent.coerceIn(VIDEO_BITRATE_SCALE_MIN, VIDEO_BITRATE_SCALE_MAX),
                )
                .putBoolean(KEY_HORIZON, settings.showHorizonLevel)
                .putBoolean(KEY_FOCUS_PEAKING, settings.focusPeakingEnabled())
                .putString(KEY_FOCUS_PEAKING_COLOR, settings.focusPeakingColor.name)
                .putString(KEY_FOCUS_PEAKING_STRENGTH, settings.focusPeakingStrength.name)
                .putString(KEY_LUT_STILLS, settings.selectedLutForStills)
                .putString(KEY_LUT_VIDEO, settings.selectedLutForVideo)
                .putBoolean(KEY_LENS_OIS, settings.enableLensOpticalStabilization)
                .putBoolean(KEY_DISABLE_OIS_STILL, settings.disableOisForStillCapture)
                .putBoolean(KEY_VIDEO_STAB_PREVIEW, settings.enableVideoStabilizationPreview)
                .putBoolean(KEY_POST_RAW_BOOST, settings.enablePostRawSensitivityBoost)
                .putBoolean(KEY_AUTO_FRAMING, settings.enableAutoFraming)
                .putBoolean(KEY_HDR_10_PREVIEW, settings.enableHdr10LivePreview)
                .putBoolean(KEY_RESEARCH_AF_BRACKET, settings.enableResearchAfBracketing)
                .putBoolean(KEY_RESEARCH_HFR_AI_CAMERA_HSR, settings.enableResearchHfrAICameraHSR)
                .putBoolean(KEY_RESEARCH_HFR_VIULL, settings.enableResearchHfrVIULL)
                .putBoolean(KEY_RESEARCH_HFR_VSR, settings.enableResearchHfrVSR)
                .putBoolean(KEY_RESEARCH_DCG_HDR, settings.enableResearchDcgHDR)
                .putBoolean(KEY_RESEARCH_QHDR, settings.enableResearchQHDR)
                .putBoolean(KEY_AF_SETTLE_BEFORE_STILL, settings.enableOpenCameraStyleAfSettleBeforeStill)
                .putBoolean(KEY_WAIT_AF_FOCUS_BEFORE_STILL, settings.waitForAfFocusBeforeStill)
                .putInt(
                    KEY_HARDWARE_JPEG_ISP_BIAS,
                    settings.hardwareJpegIspBias.coerceIn(HARDWARE_JPEG_ISP_BIAS_MIN, HARDWARE_JPEG_ISP_BIAS_MAX),
                )
                .putInt(
                    KEY_SOFTWARE_JPEG_QUALITY,
                    settings.softwareJpegCompanionQuality.coerceIn(
                        SOFTWARE_JPEG_COMPANION_QUALITY_MIN,
                        SOFTWARE_JPEG_COMPANION_QUALITY_MAX,
                    ),
                )
                .putString(KEY_STILL_CAPTURE_MODE, settings.stillCaptureMode.name)
                .putString(KEY_VIDEO_ENCODE_LANE, settings.videoEncodeLane.name)
                .putBoolean(KEY_BURST_MODE, settings.burstModeEnabled)
                .putInt(KEY_BURST_COUNT, settings.burstShotCount)
                .putInt(KEY_BURST_INTERVAL_MS, settings.burstIntervalMs)
                .putInt(KEY_NIGHTSCAPE_FRAME_COUNT, settings.nightScapeFrameCount)
                .putInt(KEY_INTERVALOMETER_SEC, settings.intervalometerIntervalSec)
                .putBoolean(KEY_INTERVALOMETER_RUNNING, settings.intervalometerRunning)
                .putBoolean(KEY_PRECAPTURE_BUFFER, settings.preCaptureBufferEnabled)
                .putString(KEY_PICTURE_PROFILE, settings.selectedPictureProfileId)
                .putBoolean(KEY_TETHERED_CAPTURE, settings.tetheredCaptureEnabled)
                .putBoolean(KEY_WIFI_DIRECT_TETHER, settings.wifiDirectTetherEnabled)
                .putInt(
                    KEY_PREVIEW_FLASH_STRENGTH,
                    settings.previewFlashStrengthPercent.coerceIn(
                        PREVIEW_FLASH_STRENGTH_MIN,
                        PREVIEW_FLASH_STRENGTH_MAX,
                    ),
                )
                .putString(KEY_VIDEO_SHUTTER_ANGLE, settings.videoShutterAngleEnum().name)
                .putString(KEY_VIDEO_COLOR_PROFILE, settings.videoColorProfileEnum().storageId)
                .putBoolean(KEY_VIDEO_PILLAR_HUD, settings.showVideoPillarHud)
                .putString(KEY_VIDEO_AUDIO_SOURCE, settings.videoAudioSourceEnum().storageId)
                .putBoolean(KEY_WIND_NOISE_FILTER, settings.windNoiseFilterEnabled)
                .putFloat(KEY_AUDIO_GAIN_DB, coerceAudioGainDb(settings.audioGainDb))
                .putString(KEY_TIME_LAPSE_MODE, settings.timeLapseModeEnum().storageId)
                .putBoolean(KEY_FOCUS_BREATHING_COMP, settings.enableFocusBreathingComp)
                .putFloat(
                    KEY_FOCUS_BREATHING_K,
                    settings.focusBreathingCompK.coerceIn(FOCUS_BREATHING_K_MIN, FOCUS_BREATHING_K_MAX),
                )
                .putBoolean(KEY_RACK_WP_NEAR_SET, settings.rackFocusWaypointNear != null)
                .putFloat(KEY_RACK_WP_NEAR, settings.rackFocusWaypointNear ?: 0f)
                .putBoolean(KEY_RACK_WP_FAR_SET, settings.rackFocusWaypointFar != null)
                .putFloat(KEY_RACK_WP_FAR, settings.rackFocusWaypointFar ?: 0f)
                .putInt(KEY_RACK_DURATION_MS, RackFocusPull.coerceDurationMs(settings.rackFocusDurationMs))
                .putBoolean(KEY_DUAL_ISO_VIDEO, settings.dualIsoVideoEnabled)
                .putBoolean(KEY_ANAMORPHIC_DESQUEEZE, settings.anamorphicDesqueezeEnabled)
                .putFloat(KEY_ANAMORPHIC_SQUEEZE, settings.anamorphicSqueezeFactor.toFloat())
                .commit()
        }

        fun loadVideoEncodeLane(context: Context): VideoEncodeLane {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_VIDEO_ENCODE_LANE, null) ?: return VideoEncodeLane.Encoded
            return VideoEncodeLane.entries.firstOrNull { it.name == name } ?: VideoEncodeLane.Encoded
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
) {
    fun updateMutate(block: (HudSettings) -> HudSettings) {
        update(block(current))
    }
}
