package dev.pointandshoot

import dev.pointandshoot.CalibrationProfile.Illuminant
import kotlin.math.abs

/**
 * Pure-data converter from a [CalibrationProfile] to the three DNG color
 * tags desktop processors (`darktable`, `RawTherapee`, Adobe DNG SDK) read
 * to honor an in-camera calibration **without** baking it into the RAW.
 *
 * The DNG color contract (per Adobe DNG 1.7 §5):
 *   * **AsShotNeutral**: `(R, G, B)` the camera saw for a neutral patch
 *     under the calibration illuminant, normalized so `max = 1.0`.
 *     This is the inverse of our [CalibrationProfile.WbGains] (gains
 *     "raise" a channel; AsShotNeutral describes "what to raise it from").
 *   * **ColorMatrix1**: `XYZ → camera-native RGB` under
 *     `CalibrationIlluminant1`. Computed as
 *     `inverse(diag(wbGains) * sRGBtoXYZ_D65 * CCM)` because our CCM
 *     calibration maps WB-gained camera RGB to sRGB-D65 (the standard
 *     ColorChecker reference space).
 *   * **ForwardMatrix1**: `(WB-gained) camera-native RGB → XYZ-D50`,
 *     which is the DNG connection space. Computed as
 *     `Bradford_D65_to_D50 * sRGBtoXYZ_D65 * CCM`.
 *
 * The published constants (`sRGBtoXYZ_D65`, `Bradford_D65_to_D50`,
 * `EXIF LightSource` codes) live below as private constants - they're
 * standards data, not copyrightable, and the calibration math relies on
 * them being byte-stable over time.
 *
 * **No Android imports.** The Camera2 wire-up (which actually pushes the
 * three resulting arrays into a [android.hardware.camera2.DngCreator]) is
 * a thin glue layer that lives in the engine; this class is pure data so
 * JUnit can lock the math without a device.
 */
object DngColorTags {

    /**
     * Resolve all three DNG color tag arrays at once. Convenience for the
     * engine's DNG-write path - it pulls the active [CalibrationProfile]
     * from `CalibrationProfileStorage`, calls [forProfile], then forwards
     * the three arrays into a `DngCreator` (or, for streaming writes, into
     * the `IFD0` directory of the in-memory TIFF builder).
     */
    fun forProfile(profile: CalibrationProfile): DngColor {
        return DngColor(
            asShotNeutral = asShotNeutral(profile.wbGains),
            colorMatrix1 = colorMatrix1(profile),
            forwardMatrix1 = forwardMatrix1(profile),
            calibrationIlluminant1 = calibrationIlluminantCode(profile.illuminant),
        )
    }

    /**
     * Per DNG spec: "AsShotNeutral specifies the selected white balance at
     * time of capture, encoded as the coordinates of a perfectly neutral
     * color in linear reference space values. The values must be scaled
     * such that the maximum value is 1.0."
     *
     * Returns a length-3 [FloatArray] in `[R, G, B]` order, max == 1.0f.
     */
    fun asShotNeutral(gains: CalibrationProfile.WbGains): FloatArray {
        val invR = 1f / gains.r
        val invG = 1f / gains.g
        val invB = 1f / gains.b
        val maxInv = maxOf(invR, invG, invB)
        return floatArrayOf(invR / maxInv, invG / maxInv, invB / maxInv)
    }

    /**
     * `ColorMatrix1`: `XYZ → camera-native (un-WB) RGB` at the calibration
     * illuminant. Stored as a length-9 `FloatArray` in row-major order
     * (the DNG SRATIONAL serialization is the engine's responsibility).
     */
    fun colorMatrix1(profile: CalibrationProfile): FloatArray {
        // Our CCM maps "WB-gained camera RGB" -> "sRGB at the calibration
        // illuminant". So:
        //   sRGB     = CCM * WB * cameraRgb
        //   XYZ      = sRGBtoXYZ_D65 * sRGB         (assuming chart is sRGB D65)
        //   cameraRgb = inverse(sRGBtoXYZ_D65 * CCM * WB) * XYZ
        val ccm = ccmAsArray(profile.ccm)
        val wbDiag = floatArrayOf(
            profile.wbGains.r, 0f, 0f,
            0f, profile.wbGains.g, 0f,
            0f, 0f, profile.wbGains.b,
        )
        val sRgbToXyz = SRGB_TO_XYZ_D65
        val combined = mul3x3(sRgbToXyz, mul3x3(ccm, wbDiag))
        return invert3x3(combined)
    }

    /**
     * `ForwardMatrix1`: `WB-gained camera RGB → XYZ-D50`. This pairs with
     * `AsShotNeutral` and tells desktop processors how to turn the
     * white-balanced raw triple into XYZ in the DNG profile connection
     * space.
     */
    fun forwardMatrix1(profile: CalibrationProfile): FloatArray {
        // sRGB_D65 = CCM * cameraRgbWB
        // XYZ_D65  = sRGBtoXYZ_D65 * sRGB_D65
        // XYZ_D50  = Bradford_D65_to_D50 * XYZ_D65
        val ccm = ccmAsArray(profile.ccm)
        val sRgbToXyz = SRGB_TO_XYZ_D65
        val bradford = BRADFORD_D65_TO_D50
        return mul3x3(bradford, mul3x3(sRgbToXyz, ccm))
    }

    /** EXIF LightSource code for `CalibrationIlluminant1`. */
    fun calibrationIlluminantCode(illuminant: Illuminant): Int = when (illuminant) {
        Illuminant.D50 -> 23
        Illuminant.D55 -> 20
        Illuminant.D65 -> 21
        Illuminant.StdA -> 17
        // EXIF "cool white fluorescent" - the closest code to F2.
        Illuminant.F2 -> 14
    }

    /**
     * Bundle of the three DNG color tag values plus the EXIF light source
     * code. The engine forwards these into `DngCreator` (or the writer's
     * IFD0 if we ever pivot off `DngCreator`).
     */
    data class DngColor(
        val asShotNeutral: FloatArray,
        val colorMatrix1: FloatArray,
        val forwardMatrix1: FloatArray,
        val calibrationIlluminant1: Int,
    ) {
        init {
            require(asShotNeutral.size == 3) { "asShotNeutral must be length 3 (was ${asShotNeutral.size})" }
            require(colorMatrix1.size == 9) { "colorMatrix1 must be length 9 (was ${colorMatrix1.size})" }
            require(forwardMatrix1.size == 9) { "forwardMatrix1 must be length 9 (was ${forwardMatrix1.size})" }
            // AsShotNeutral max must equal 1 (within float epsilon).
            val mx = maxOf(asShotNeutral[0], asShotNeutral[1], asShotNeutral[2])
            require(abs(mx - 1f) < 1e-5f) { "asShotNeutral max must equal 1.0f (was $mx)" }
        }

        // FloatArray equality is reference-based by default; data class
        // auto-generated equals/hashCode would compare references too.
        // Override so tests can compare profiles directly.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DngColor) return false
            return asShotNeutral.contentEquals(other.asShotNeutral) &&
                colorMatrix1.contentEquals(other.colorMatrix1) &&
                forwardMatrix1.contentEquals(other.forwardMatrix1) &&
                calibrationIlluminant1 == other.calibrationIlluminant1
        }

        override fun hashCode(): Int {
            var h = asShotNeutral.contentHashCode()
            h = 31 * h + colorMatrix1.contentHashCode()
            h = 31 * h + forwardMatrix1.contentHashCode()
            h = 31 * h + calibrationIlluminant1.hashCode()
            return h
        }
    }

    // ---- math helpers (private) -----------------------------------------

    private fun ccmAsArray(c: CalibrationProfile.Ccm): FloatArray = floatArrayOf(
        c.m00, c.m01, c.m02,
        c.m10, c.m11, c.m12,
        c.m20, c.m21, c.m22,
    )

    /** Row-major 3x3 matrix multiply: `out = a * b`. */
    private fun mul3x3(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var s = 0f
                for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]
                out[i * 3 + j] = s
            }
        }
        return out
    }

    /**
     * 3x3 inverse via adjugate / determinant. Throws on singular matrices
     * (`|det| < 1e-12f`); callers should fall back to identity color tags.
     */
    private fun invert3x3(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) >= 1e-12f) { "matrix is singular (det = $det)" }
        val invDet = 1f / det

        return floatArrayOf(
            (e * i - f * h) * invDet,
            -(b * i - c * h) * invDet,
            (b * f - c * e) * invDet,
            -(d * i - f * g) * invDet,
            (a * i - c * g) * invDet,
            -(a * f - c * d) * invDet,
            (d * h - e * g) * invDet,
            -(a * h - b * g) * invDet,
            (a * e - b * d) * invDet,
        )
    }

    // ---- standards constants (public-domain) ----------------------------

    /**
     * Linear sRGB -> XYZ at D65 (IEC 61966-2-1, BT.709 primaries). Public
     * domain (CIE / IEC standards data).
     */
    private val SRGB_TO_XYZ_D65: FloatArray = floatArrayOf(
        0.4124564f, 0.3575761f, 0.1804375f,
        0.2126729f, 0.7151522f, 0.0721750f,
        0.0193339f, 0.1191920f, 0.9503041f,
    )

    /**
     * Bradford chromatic adaptation transform from D65 to D50. Public
     * domain (CIE standard reference values, derived from the published
     * Bradford cone-response matrix). Used to land the ForwardMatrix1
     * output in the DNG connection space (XYZ-D50).
     */
    private val BRADFORD_D65_TO_D50: FloatArray = floatArrayOf(
        1.0478112f, 0.0228866f, -0.0501270f,
        0.0295424f, 0.9904844f, -0.0170491f,
        -0.0092345f, 0.0150436f, 0.7521316f,
    )
}
