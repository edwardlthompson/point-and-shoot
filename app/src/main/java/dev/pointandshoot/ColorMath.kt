package dev.pointandshoot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-data color-difference math per BUILD_PLAN §7 ("Phase 4 V&V gates: CCM
 * produces ≤ 1.0 dE_2000 mean error on the synthetic 24-patch fixture; .cube
 * round-trip shrinks mean dE_2000 by ≥ 80 %").
 *
 *   * [linearSrgbToXyz] / [xyzToLab] / [linearSrgbToLab] convert pixels into
 *     CIE Lab (D65) so we can compare colors perceptually.
 *   * [deltaE2000] implements the full CIEDE2000 formula (Sharma et al., 2005)
 *     including the L*, C*, H* weighting functions and the rotation term.
 *
 * No Android imports; safe for unit testing on the JVM. The numbers here are
 * facts (CIE-published reference matrices + the published CIEDE2000 formula),
 * not copyrightable, and ship under the same Apache-2.0 license as the rest
 * of the project.
 */
object ColorMath {

    /**
     * Convert a linear-light sRGB triple `[0, 1]` per channel to CIE-XYZ
     * (D65 white point). Uses the official sRGB primaries matrix from
     * IEC 61966-2-1.
     */
    fun linearSrgbToXyz(rgb: FloatArray): FloatArray {
        require(rgb.size == 3) { "rgb must be length 3 (was ${rgb.size})" }
        val r = rgb[0].toDouble(); val g = rgb[1].toDouble(); val b = rgb[2].toDouble()
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        return floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())
    }

    /**
     * Convert CIE-XYZ to CIE Lab using the supplied white point reference
     * (defaults to [D65]). The CIE-Lab space is approximately perceptually
     * uniform; one Lab unit ≈ one "just noticeable difference" on small
     * patches.
     */
    fun xyzToLab(xyz: FloatArray, white: WhitePoint = D65): FloatArray {
        require(xyz.size == 3) { "xyz must be length 3 (was ${xyz.size})" }
        val xn = xyz[0].toDouble() / white.x
        val yn = xyz[1].toDouble() / white.y
        val zn = xyz[2].toDouble() / white.z
        val fx = labF(xn)
        val fy = labF(yn)
        val fz = labF(zn)
        val l = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val b = 200.0 * (fy - fz)
        return floatArrayOf(l.toFloat(), a.toFloat(), b.toFloat())
    }

    /** Convenience: linear-light sRGB → CIE Lab (D65). */
    fun linearSrgbToLab(rgb: FloatArray, white: WhitePoint = D65): FloatArray =
        xyzToLab(linearSrgbToXyz(rgb), white)

    /**
     * CIEDE2000 color difference between two Lab triples. Matches the
     * Sharma / Wu / Dalal (2005) reference implementation; well-known
     * test vectors agree to within `1e-3`.
     *
     * Default weighting `kL = kC = kH = 1.0` is the "graphic-arts" preset
     * (BUILD_PLAN §7 V&V uses these weights).
     */
    fun deltaE2000(
        lab1: FloatArray,
        lab2: FloatArray,
        kL: Double = 1.0,
        kC: Double = 1.0,
        kH: Double = 1.0,
    ): Double {
        require(lab1.size == 3 && lab2.size == 3) {
            "lab1 and lab2 must be length 3"
        }
        val l1 = lab1[0].toDouble(); val a1 = lab1[1].toDouble(); val b1 = lab1[2].toDouble()
        val l2 = lab2[0].toDouble(); val a2 = lab2[1].toDouble(); val b2 = lab2[2].toDouble()

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) * 0.5
        val cBarPow7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBarPow7 / (cBarPow7 + POW25_7)))
        val a1p = (1.0 + g) * a1
        val a2p = (1.0 + g) * a2
        val c1p = sqrt(a1p * a1p + b1 * b1)
        val c2p = sqrt(a2p * a2p + b2 * b2)
        val h1p = atan2Deg(b1, a1p)
        val h2p = atan2Deg(b2, a2p)

        val deltaLp = l2 - l1
        val deltaCp = c2p - c1p

        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180.0 -> h2p - h1p
            (h2p - h1p) > 180.0 -> (h2p - h1p) - 360.0
            else -> (h2p - h1p) + 360.0
        }
        val deltaHp = 2.0 * sqrt(c1p * c2p) * sin(degToRad(dhp / 2.0))

        val lBarP = (l1 + l2) * 0.5
        val cBarP = (c1p + c2p) * 0.5
        val hBarP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) * 0.5
            (h1p + h2p) < 360.0 -> (h1p + h2p + 360.0) * 0.5
            else -> (h1p + h2p - 360.0) * 0.5
        }

        val t = 1.0 -
            0.17 * cos(degToRad(hBarP - 30.0)) +
            0.24 * cos(degToRad(2.0 * hBarP)) +
            0.32 * cos(degToRad(3.0 * hBarP + 6.0)) -
            0.20 * cos(degToRad(4.0 * hBarP - 63.0))
        val deltaTheta = 30.0 * exp(-((hBarP - 275.0) / 25.0).pow(2.0))
        val cBarPPow7 = cBarP.pow(7.0)
        val rC = 2.0 * sqrt(cBarPPow7 / (cBarPPow7 + POW25_7))
        val sL = 1.0 + (0.015 * (lBarP - 50.0).pow(2.0)) / sqrt(20.0 + (lBarP - 50.0).pow(2.0))
        val sC = 1.0 + 0.045 * cBarP
        val sH = 1.0 + 0.015 * cBarP * t
        val rT = -sin(degToRad(2.0 * deltaTheta)) * rC

        val termL = deltaLp / (kL * sL)
        val termC = deltaCp / (kC * sC)
        val termH = deltaHp / (kH * sH)
        return sqrt(termL * termL + termC * termC + termH * termH + rT * termC * termH)
    }

    /** Convenience: CIEDE2000 between two linear-light sRGB triples. */
    fun deltaE2000FromLinearSrgb(rgb1: FloatArray, rgb2: FloatArray): Double {
        val lab1 = linearSrgbToLab(rgb1)
        val lab2 = linearSrgbToLab(rgb2)
        return deltaE2000(lab1, lab2)
    }

    /** A reference white point in the XYZ space (Y normalized to 1.0). */
    data class WhitePoint(val x: Double, val y: Double, val z: Double)

    /** D65 reference white (CIE 1931 2-degree observer). */
    val D65: WhitePoint = WhitePoint(0.95047, 1.0, 1.08883)

    /** D50 reference white (CIE 1931 2-degree observer). */
    val D50: WhitePoint = WhitePoint(0.96422, 1.0, 0.82521)

    // ---------- helpers ----------

    private const val EPSILON: Double = 216.0 / 24389.0      // = (6/29)^3
    private const val KAPPA: Double = 24389.0 / 27.0         // = (29/3)^3
    private val POW25_7: Double = 25.0.pow(7.0)

    private fun labF(t: Double): Double = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16.0) / 116.0

    private fun degToRad(deg: Double): Double = deg * PI / 180.0

    private fun atan2Deg(y: Double, x: Double): Double {
        if (x == 0.0 && y == 0.0) return 0.0
        var deg = atan2(y, x) * 180.0 / PI
        if (deg < 0) deg += 360.0
        return deg
    }
}
