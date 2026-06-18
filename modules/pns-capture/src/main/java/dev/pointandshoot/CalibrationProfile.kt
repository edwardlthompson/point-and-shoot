package dev.pointandshoot

/**
 * One-shot calibration result captured against a known reference chart per
 * BUILD_PLAN §7 ("Phase 4 - Calibration mode"). Pure data; no Android imports.
 *
 * The two engineering ingredients are:
 *   * [wbGains]  - per-channel scaling such that R/G ≈ 1, B/G ≈ 1 on neutrals.
 *   * [ccm]      - 3x3 color correction matrix solved by `CalibrationMath`.
 *
 * [bias] is the additive offset (usually near-zero) that pairs with [ccm];
 * combining them gives the inference: `out = ccm * (gainedRgb) + bias`.
 *
 * The profile JSON lives under `getExternalFilesDir(null)/calibration/`; the
 * `.cube` LUT generated from it via `CalibrationToLut.toCube` lives next to
 * the JSON. Both are pulled into `hfr-runs/calibration/` by the host script.
 */
data class CalibrationProfile(
    val wbGains: WbGains,
    val ccm: Ccm,
    val bias: Bias,
    val mtf50Lpph: Float? = null,
    val illuminant: Illuminant,
    val capturedAtMs: Long,
    val cameraId: String,
    val targetId: String,
) {
    init {
        if (mtf50Lpph != null) {
            require(mtf50Lpph >= 0f) { "mtf50Lpph must be >= 0 when set (was $mtf50Lpph)" }
        }
        require(cameraId.isNotBlank()) { "cameraId must not be blank" }
        require(targetId.isNotBlank()) { "targetId must not be blank" }
    }

    /**
     * Apply the (WB -> CCM -> bias) chain to one linear-light RGB triple.
     * Inputs and outputs are clamped to `[0, 1]`. Pure function.
     */
    fun apply(rgb: FloatArray): FloatArray {
        require(rgb.size == 3) { "rgb must be length 3 (was ${rgb.size})" }
        val rG = (rgb[0] * wbGains.r).coerceIn(0f, 1f)
        val gG = (rgb[1] * wbGains.g).coerceIn(0f, 1f)
        val bG = (rgb[2] * wbGains.b).coerceIn(0f, 1f)
        return floatArrayOf(
            (ccm.m00 * rG + ccm.m01 * gG + ccm.m02 * bG + bias.r).coerceIn(0f, 1f),
            (ccm.m10 * rG + ccm.m11 * gG + ccm.m12 * bG + bias.g).coerceIn(0f, 1f),
            (ccm.m20 * rG + ccm.m21 * gG + ccm.m22 * bG + bias.b).coerceIn(0f, 1f),
        )
    }

    /**
     * R / G / B multiplicative gains. Standard convention: G is anchored at 1.
     * `r > 1` warms the image (raises red); `b > 1` cools it.
     */
    data class WbGains(val r: Float, val g: Float, val b: Float) {
        init {
            require(r > 0f) { "r gain must be > 0 (was $r)" }
            require(g > 0f) { "g gain must be > 0 (was $g)" }
            require(b > 0f) { "b gain must be > 0 (was $b)" }
        }

        companion object {
            val Identity: WbGains = WbGains(1f, 1f, 1f)
        }
    }

    /** 3x3 row-major color correction matrix: `out_a = sum_b m[a][b] * in[b]`. */
    data class Ccm(
        val m00: Float, val m01: Float, val m02: Float,
        val m10: Float, val m11: Float, val m12: Float,
        val m20: Float, val m21: Float, val m22: Float,
    ) {
        companion object {
            val Identity: Ccm = Ccm(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
    }

    /** Additive offset applied after the CCM (usually near-zero). */
    data class Bias(val r: Float, val g: Float, val b: Float) {
        companion object {
            val Zero: Bias = Bias(0f, 0f, 0f)
        }
    }

    /**
     * CIE-standard illuminants the published reference Lab values are quoted
     * for. The CCM solve must use the illuminant the chart was photographed
     * under so the chromatic adaptation is consistent.
     */
    enum class Illuminant {
        D50, D55, D65, StdA, F2,
    }

    companion object {
        /** Convenience constructor for the all-identity case (uncalibrated). */
        fun identity(
            cameraId: String,
            targetId: String,
            illuminant: Illuminant = Illuminant.D65,
            capturedAtMs: Long = 0L,
        ): CalibrationProfile = CalibrationProfile(
            wbGains = WbGains.Identity,
            ccm = Ccm.Identity,
            bias = Bias.Zero,
            mtf50Lpph = null,
            illuminant = illuminant,
            capturedAtMs = capturedAtMs,
            cameraId = cameraId,
            targetId = targetId,
        )
    }
}
