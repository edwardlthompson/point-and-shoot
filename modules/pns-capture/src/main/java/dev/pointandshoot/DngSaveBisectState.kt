package dev.pointandshoot

import android.content.Intent
import android.util.Log
import dev.pointandshoot.StillDngBackend

/**
 * USB matrix bisect toggles (`pns_preview_dng_*` intent extras). Cleared when preview automation
 * extras are not trusted. See `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md` and
 * `docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md`.
 *
 * **Isolation:** All flags default off. Normal consumer launches (any SKU) must not change AE/IQ/DNG
 * behavior. Do not promote these into GenericFleetPolicy defaults — OP13 exposure work ships only
 * via ADB bisect or opt-in LegacyDeviceFleetPolicy after USB proof.
 */
object DngSaveBisectState {
    private const val TAG = "PNS.DngBisect"

    @Volatile
    var skipStillMetadataApply: Boolean = false

    @Volatile
    var stillDngBackendOverride: StillDngBackend? = null

    @Volatile
    var skipUniqueCameraModel: Boolean = false

    @Volatile
    var skipJpegProcessingHintsOnRawStill: Boolean = false

    /**
     * When non-null, overrides [dev.pointandshoot.fleet.LeafDngHalReconcile] gating for leaf rear ids.
     */
    @Volatile
    var forceLeafHalReconcile: Boolean? = null

    /** When true with reconcile, use Bayer ASN (legacy) instead of LegacyDevice gains-first policy. */
    @Volatile
    var forceBayerAsnOnLeafReconcile: Boolean = false

    /** When true, do not apply [DngForwardMatrixFix] gain scales on leaf ASN patch. */
    @Volatile
    var skipHalWbGainCorrection: Boolean = false

    /** When true, skip [LegacyLeafStillColorCorrection] on UW/tele still requests (bisect). */
    @Volatile
    var skipOp13CaptureTimeColorGains: Boolean = false

    /** When true, use legacy Bayer/gain ASN path instead of [LeafDngHalReconcile] hal-cal reconcile. */
    @Volatile
    var forceLegacyAsnReconcile: Boolean = false

    @Volatile
    var skipDngSoftwareDescription: Boolean = false

    /** E03: skip pure-HAL [CONTROL_AE_LOCK] after precapture on RAW still. */
    @Volatile
    var skipPureHalAeLockOnStill: Boolean = false

    /** E04: when &gt; 0, overrides stopRepeating→still debounce ms. */
    @Volatile
    var afterStopDebounceMsOverride: Long = 0L

    /** E05: skip ProShot weight-0 AE regions. */
    @Volatile
    var skipProShotDefaultAeRegions: Boolean = false

    /** E08: AE exposure compensation steps (0 = leave unset). */
    @Volatile
    var stillAeExposureCompensationSteps: Int = 0

    /** E09: precapture settle uses TEMPLATE_STILL_CAPTURE instead of PREVIEW. */
    @Volatile
    var precaptureUseStillTemplate: Boolean = false

    /** E11: skip [StillCaptureIqPolicy] on still. */
    @Volatile
    var skipStillIq: Boolean = false

    /**
     * ProShot capture **process** rebuild (ADB-only). Precapture via session.capture while
     * repeating runs (C0353b0 L6/f6), then stop + STILL. See matrix cell **PS01**.
     * Never a GenericFleet default.
     */
    @Volatile
    var useProShotCapturePipeline: Boolean = false

    fun reset() {
        skipStillMetadataApply = false
        stillDngBackendOverride = null
        skipUniqueCameraModel = false
        skipJpegProcessingHintsOnRawStill = false
        forceLeafHalReconcile = null
        forceBayerAsnOnLeafReconcile = false
        skipHalWbGainCorrection = false
        skipOp13CaptureTimeColorGains = false
        forceLegacyAsnReconcile = false
        skipDngSoftwareDescription = false
        skipPureHalAeLockOnStill = false
        afterStopDebounceMsOverride = 0L
        skipProShotDefaultAeRegions = false
        stillAeExposureCompensationSteps = 0
        precaptureUseStillTemplate = false
        skipStillIq = false
        useProShotCapturePipeline = false
    }

    fun logActive() {
        Log.i(
            TAG,
            "bisect skipMeta=$skipStillMetadataApply backendOverride=${stillDngBackendOverride?.name ?: "null"} " +
                "skip50708=$skipUniqueCameraModel skipJpegHints=$skipJpegProcessingHintsOnRawStill " +
                "forceLeafReconcile=$forceLeafHalReconcile forceBayerAsn=$forceBayerAsnOnLeafReconcile " +
                "skipHalWbGain=$skipHalWbGainCorrection skipOp13StillGains=$skipOp13CaptureTimeColorGains " +
                "forceLegacyAsn=$forceLegacyAsnReconcile skipSoftwareDesc=$skipDngSoftwareDescription " +
                "skipAeLock=$skipPureHalAeLockOnStill afterStopMs=$afterStopDebounceMsOverride " +
                "skipAeRegions=$skipProShotDefaultAeRegions aeCompSteps=$stillAeExposureCompensationSteps " +
                "precaptureStillTpl=$precaptureUseStillTemplate skipStillIq=$skipStillIq " +
                "proshotPipeline=$useProShotCapturePipeline",
        )
    }

    /**
     * Reads [PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_*] from a cold-start preview intent. Returns true if any bisect
     * extra was present.
     */
    fun applyFromPreviewIntent(intent: Intent?): Boolean {
        reset()
        if (intent == null) return false
        var any = false
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_METADATA, false)) {
            skipStillMetadataApply = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_UNIQUE_CAMERA_MODEL, false)) {
            skipUniqueCameraModel = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_JPEG_HINTS_STILL, false)) {
            skipJpegProcessingHintsOnRawStill = true
            any = true
        }
        if (intent.hasExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE)) {
            forceLeafHalReconcile =
                intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE, false)
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_FORCE_BAYER_ASN, false)) {
            forceBayerAsnOnLeafReconcile = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_SOFTWARE_DESC, false)) {
            skipDngSoftwareDescription = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_AE_LOCK, false)) {
            skipPureHalAeLockOnStill = true
            any = true
        }
        if (intent.hasExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_AFTER_STOP_DEBOUNCE_MS)) {
            afterStopDebounceMsOverride =
                intent.getIntExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_AFTER_STOP_DEBOUNCE_MS, 0).toLong()
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_AE_REGIONS, false)) {
            skipProShotDefaultAeRegions = true
            any = true
        }
        if (intent.hasExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_AE_COMP_STEPS)) {
            stillAeExposureCompensationSteps =
                intent.getIntExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_AE_COMP_STEPS, 0)
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_PRECAPTURE_STILL_TEMPLATE, false)) {
            precaptureUseStillTemplate = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_IQ, false)) {
            skipStillIq = true
            any = true
        }
        if (intent.getBooleanExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_DNG_PROSHOT_PIPELINE, false)) {
            useProShotCapturePipeline = true
            // ProShot Auto still does not AE_LOCK; ASN patch is post-save surgery ProShot never does.
            skipPureHalAeLockOnStill = true
            any = true
        }
        val backendRaw = intent.getStringExtra(PreviewDngBisectIntentExtras.EXTRA_PNS_PREVIEW_STILL_DNG_BACKEND)?.trim()
        if (!backendRaw.isNullOrBlank()) {
            stillDngBackendOverride = parseStillDngBackendExtra(backendRaw)
            any = true
        }
        if (any) logActive()
        return any
    }

    private fun parseStillDngBackendExtra(raw: String): StillDngBackend? =
        when (raw.lowercase()) {
            "framework_referenceapp", "referencecam" -> StillDngBackend.FRAMEWORK_REFERENCEAPP
            "altreferenceapp_inspired", "altreferenceapp" -> StillDngBackend.ALTREFERENCEAPP_INSPIRED
            else -> {
                Log.w(TAG, "unknown pns_preview_still_dng_backend=$raw")
                null
            }
        }
}
