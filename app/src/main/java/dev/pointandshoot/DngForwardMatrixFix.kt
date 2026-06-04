package dev.pointandshoot

import android.util.Rational

/**
 * Per-camera ForwardMatrix overrides for devices where the HAL copy-pastes one sensor's
 * ForwardMatrix to all camera IDs (causes green/dark DNG cast on UW/tele in raw converters).
 *
 * Matrices were derived from each camera's actual ColorMatrix2 (D65) via:
 *   FM = sRGB_D50 × pinv(CM2)
 * then validated against the captured logcat on LegacySku (OPPO Find X8 Pro) May 2026.
 *
 * **Deprecated / unwired (May 2026):** FM/ASN post-patch in [Dng12Saver] was reverted — broke wide/tele on LegacySku.
 * Do not wire without ReferenceCam-aligned session design + USB proof. See [docs/DNG_REFERENCE_APPS.md].
 *
 * **Safe no-op:** if the patch fails or the camera ID is not listed the original DNG is written.
 */
object DngForwardMatrixFix {
    private fun normalizeModelToken(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    data class FmOverride(
        /** 9 rational values, row-major (3×3 XYZ→linear-RGB forward matrix, D50 illuminant). */
        val fm1: Array<Rational>,
        /** fm2 — same as fm1 when HAL only provides one illuminant. */
        val fm2: Array<Rational> = fm1,
    )

    private fun r(n: Int, d: Int = 10000) = Rational(n, d)

    /**
     * Map of camera ID → ForwardMatrix override.
     * Outer key: device model prefix (Build.MODEL prefix, lower-case).
     * Inner key: camera ID string.
     *
     * LegacySku = Legacy device dodge leaf ids ([LegacyFleetPolicy]): **3** UW, **2** wide, **4** tele.
     */
    private val overrides: Map<String, Map<String, FmOverride>> = mapOf(
        "legacy_sku" to mapOf(
            // UW (cam3) — FM = sRGB→XYZ_D50 × pinv(CM2_uw), computed May 2026 from v4 DNG
            "3" to FmOverride(
                fm1 = arrayOf(
                    r(3083), r(3971), r(5936),
                    r(2278), r(5766), r(4936),
                    r(-121), r(-799), r(10619),
                ),
            ),
            // Tele (cam4) — FM = sRGB→XYZ_D50 × pinv(CM2_tele), computed May 2026 from v4 DNG
            "4" to FmOverride(
                fm1 = arrayOf(
                    r(5032), r(3194), r(5407),
                    r(4271), r(4902), r(4270),
                    r(439),  r(-1076), r(10907),
                ),
            ),
        ),
    )

    /**
     * Per-camera static WB correction factors for devices where the HAL reports wrong
     * COLOR_CORRECTION_GAINS for aux cameras.
     *
     * The HAL gives each aux camera gains that are systematically off by a fixed ratio
     * relative to what the raw pixel data requires. These factors correct that offset:
     *   corrected_gains_R = hal_gains_R * scaleR
     *   corrected_gains_B = hal_gains_B * scaleB
     *
     * Derived from raw bayer channel mean ratios (aux R/G / wide R/G) across 3 paired
     * shots with different illuminants. Std < 2% confirms these are hardware constants.
     *
     * LegacySku measurements (May 2026):
     *   UW   R/G ratio vs wide: 1.147 → scaleR=1.147, scaleB=1.036
     *   Tele R/G ratio vs wide: 0.661 → scaleR=1.602, scaleB=1.147
     */
    data class WbCorrection(val scaleR: Float, val scaleB: Float)

    private val wbCorrections: Map<String, Map<String, WbCorrection>> = mapOf(
        "legacy_sku" to mapOf(
            "3" to WbCorrection(scaleR = 1.147f, scaleB = 1.036f),   // UW
            "4" to WbCorrection(scaleR = 1.602f, scaleB = 1.147f),   // Tele
        ),
    )

    /**
     * Returns the [FmOverride] for [cameraId] on [modelLower] (lower-cased Build.MODEL),
     * or null if no override is registered.
     */
    fun get(modelLower: String, cameraId: String?): FmOverride? =
        if (cameraId == null) null
        else {
            val normalizedModel = normalizeModelToken(modelLower)
            overrides.entries
                .firstOrNull { normalizedModel.contains(normalizeModelToken(it.key)) }
                ?.value?.get(cameraId)
        }

    /**
     * Returns the [WbCorrection] for [cameraId] on [modelLower], or null if none registered.
     * Returns null for the wide (reference) camera so it passes through unchanged.
     */
    fun getWbCorrection(modelLower: String, cameraId: String?): WbCorrection? =
        if (cameraId == null) null
        else {
            val normalizedModel = normalizeModelToken(modelLower)
            wbCorrections.entries
                .firstOrNull { normalizedModel.contains(normalizeModelToken(it.key)) }
                ?.value?.get(cameraId)
        }
}
