package dev.pointandshoot

import dev.pointandshoot.fleet.OnePlus13FleetPolicy

/**
 * Sprint **15.15** Phase 2 — per-device WB scale factors for leaf aux DNG (CPH2655 class).
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

    /** UW / tele scales on OP13 dodge ids ([OnePlus13FleetPolicy]). */
    fun fmScaleForOp13Leaf(cameraId: String): DngFmScale? =
        when (cameraId) {
            OnePlus13FleetPolicy.CANONICAL_UW,
            OnePlus13FleetPolicy.CANONICAL_TELE,
            -> fmScaleFor("CPH2655", cameraId)
            else -> null
        }
}
