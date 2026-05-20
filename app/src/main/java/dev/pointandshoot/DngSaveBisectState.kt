package dev.pointandshoot

import android.content.Intent
import android.util.Log
import dev.pointandshoot.fleet.StillDngBackend

/**
 * USB matrix bisect toggles (`pns_preview_dng_*` intent extras). Cleared when preview automation
 * extras are not trusted. See `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`.
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

    /** When true with reconcile, use Bayer ASN (legacy) instead of OP13 gains-first policy. */
    @Volatile
    var forceBayerAsnOnLeafReconcile: Boolean = false

    /** When true, do not apply [DngForwardMatrixFix] gain scales on leaf ASN patch. */
    @Volatile
    var skipHalWbGainCorrection: Boolean = false

    /** When true, skip [Op13LeafStillColorCorrection] on UW/tele still requests (bisect). */
    @Volatile
    var skipOp13CaptureTimeColorGains: Boolean = false

    /** When true, use legacy Bayer/gain ASN path instead of [LeafDngHalReconcile] hal-cal reconcile. */
    @Volatile
    var forceLegacyAsnReconcile: Boolean = false

    @Volatile
    var skipDngSoftwareDescription: Boolean = false

    fun reset() {
        skipStillMetadataApply = false
        stillDngBackendOverride = null
        skipUniqueCameraModel = false
        skipJpegProcessingHintsOnRawStill = false
        forceLeafHalReconcile = null
    }

    fun logActive() {
        Log.i(
            TAG,
            "bisect skipMeta=$skipStillMetadataApply backendOverride=${stillDngBackendOverride?.name ?: "null"} " +
                "skip50708=$skipUniqueCameraModel skipJpegHints=$skipJpegProcessingHintsOnRawStill " +
                "forceLeafReconcile=$forceLeafHalReconcile forceBayerAsn=$forceBayerAsnOnLeafReconcile " +
                "skipHalWbGain=$skipHalWbGainCorrection skipOp13StillGains=$skipOp13CaptureTimeColorGains " +
                "forceLegacyAsn=$forceLegacyAsnReconcile skipSoftwareDesc=$skipDngSoftwareDescription",
        )
    }

    /**
     * Reads [EXTRA_PNS_PREVIEW_DNG_*] from a cold-start preview intent. Returns true if any bisect
     * extra was present.
     */
    fun applyFromPreviewIntent(intent: Intent?): Boolean {
        reset()
        if (intent == null) return false
        var any = false
        if (intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_METADATA, false)) {
            skipStillMetadataApply = true
            any = true
        }
        if (intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_SKIP_UNIQUE_CAMERA_MODEL, false)) {
            skipUniqueCameraModel = true
            any = true
        }
        if (intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_SKIP_JPEG_HINTS_STILL, false)) {
            skipJpegProcessingHintsOnRawStill = true
            any = true
        }
        if (intent.hasExtra(EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE)) {
            forceLeafHalReconcile =
                intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE, false)
            any = true
        }
        if (intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_FORCE_BAYER_ASN, false)) {
            forceBayerAsnOnLeafReconcile = true
            any = true
        }
        if (intent.getBooleanExtra(EXTRA_PNS_PREVIEW_DNG_SKIP_SOFTWARE_DESC, false)) {
            skipDngSoftwareDescription = true
            any = true
        }
        val backendRaw = intent.getStringExtra(EXTRA_PNS_PREVIEW_STILL_DNG_BACKEND)?.trim()
        if (!backendRaw.isNullOrBlank()) {
            stillDngBackendOverride = parseStillDngBackendExtra(backendRaw)
            any = true
        }
        if (any) logActive()
        return any
    }

    private fun parseStillDngBackendExtra(raw: String): StillDngBackend? =
        when (raw.lowercase()) {
            "framework_proshot", "proshot" -> StillDngBackend.FRAMEWORK_PROSHOT
            "motioncam_inspired", "motioncam" -> StillDngBackend.MOTIONCAM_INSPIRED
            else -> {
                Log.w(TAG, "unknown pns_preview_still_dng_backend=$raw")
                null
            }
        }
}
