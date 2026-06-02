package dev.pointandshoot

import dev.pointandshoot.fleet.LegacyFleetPolicy

/**
 * Sprint **15.15** Phase 2 — per-device WB scale factors for leaf aux DNG (LegacySku class).
 *
 * Canonical values live in [DngForwardMatrixFix]; this object is the stable lookup surface for
 * gates/tests and future in-place TIFF work under [dng-save-pipeline-lock] rules.
 */
data class DngFmScale(
    val scaleR: Float,
    val scaleB: Float,
)

object DngDeviceColorProfile {
    fun fmScaleFor(model: String, cameraId: String): DngFmScale? {
        val wb =
            DngForwardMatrixFix.getWbCorrection(model.lowercase(), cameraId) ?: return null
        return DngFmScale(wb.scaleR, wb.scaleB)
    }

    /** UW / tele scales on LegacyDevice dodge ids ([LegacyFleetPolicy]). */
    fun fmScaleForOp13Leaf(cameraId: String): DngFmScale? =
        when (cameraId) {
            LegacyFleetPolicy.CANONICAL_UW,
            LegacyFleetPolicy.CANONICAL_TELE,
            -> fmScaleFor("LegacySku", cameraId)
            else -> null
        }
}
