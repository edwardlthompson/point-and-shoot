package dev.pointandshoot

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Phase 4 V&V: closes BUILD_PLAN §7
 *   * "On a synthetic 24-patch fixture (known reference values, no noise):
 *      computed CCM produces ≤ 1.0 dE_2000 mean error" — covered by
 *      [CCM solver recovers a known camera distortion to within 1 dE_2000 mean].
 *   * "`.cube` round-trip: applying the exported LUT to the captured chart
 *      shrinks mean dE_2000 by ≥ 80 % vs the un-LUT'd capture" — covered by
 *      [LUT round-trip shrinks mean dE_2000 by at least 80 percent].
 *
 * The fixture deliberately *invents* a "camera distortion" matrix and runs it
 * over the bundled `BundledReferenceTargets.ColorCheckerClassic24` reference
 * values to produce synthetic "measured" patches; the calibration solver is
 * then asked to recover the inverse, and the resulting CCM (and a 33^3 LUT
 * baked from it) is graded against ground truth via `ColorMath.deltaE2000`.
 *
 * A small per-patch noise term is added so the solver does not get a
 * trivially-perfect 0 dE solution; the noise level is small enough that the
 * BUILD_PLAN ≤ 1 dE_2000 budget is still comfortably met.
 */
class CalibrationCcmAccuracyTest {

    /**
     * Synthetic camera distortion: simulates an ISP that mixes a small amount
     * of channels into each other (e.g., crosstalk + a slight green cast).
     * The matrix is invertible and well-conditioned so the LSQ solver has a
     * clean recovery path.
     */
    private val cameraDistortion: FloatArray = floatArrayOf(
        1.05f, 0.04f, -0.03f,
        0.02f, 0.95f, 0.02f,
        -0.04f, 0.06f, 1.02f,
    )

    private fun applyMatrix(m: FloatArray, rgb: FloatArray): FloatArray {
        val r = m[0] * rgb[0] + m[1] * rgb[1] + m[2] * rgb[2]
        val g = m[3] * rgb[0] + m[4] * rgb[1] + m[5] * rgb[2]
        val b = m[6] * rgb[0] + m[7] * rgb[1] + m[8] * rgb[2]
        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    private fun applyCcm(ccm: CalibrationProfile.Ccm, rgb: FloatArray): FloatArray = applyMatrix(
        floatArrayOf(
            ccm.m00, ccm.m01, ccm.m02,
            ccm.m10, ccm.m11, ccm.m12,
            ccm.m20, ccm.m21, ccm.m22,
        ),
        rgb,
    )

    private fun simulateMeasurements(
        target: List<FloatArray>,
        noiseStdDev: Float,
        seed: Long,
    ): List<FloatArray> {
        val rng = Random(seed)
        return target.map { ref ->
            val distorted = applyMatrix(cameraDistortion, ref)
            // Symmetric small per-channel additive noise (uniform); keeps results in [0, 1].
            FloatArray(3) { ch ->
                (distorted[ch] + (rng.nextFloat() - 0.5f) * 2f * noiseStdDev).coerceIn(0f, 1f)
            }
        }
    }

    private fun meanDe2000(predicted: List<FloatArray>, reference: List<FloatArray>): Double {
        require(predicted.size == reference.size) { "size mismatch" }
        var sum = 0.0
        for (i in predicted.indices) {
            sum += ColorMath.deltaE2000FromLinearSrgb(predicted[i], reference[i])
        }
        return sum / predicted.size
    }

    // ---------- Gate 1: CCM solver dE_2000 accuracy ----------

    @Test
    fun `CCM solver recovers a known camera distortion to within 1 dE_2000 mean`() {
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = simulateMeasurements(target, noiseStdDev = 0.005f, seed = 1234L)

        // Recover the correction matrix `M` such that `target = M * measured`.
        val ccm = CalibrationMath.computeCcm(measured, target)

        // Apply the recovered correction.
        val corrected = measured.map { applyCcm(ccm, it) }

        val meanDe = meanDe2000(corrected, target)
        assertTrue(
            "CCM-corrected mean dE_2000 = $meanDe should be <= 1.0 per BUILD_PLAN §7 V&V gate",
            meanDe <= 1.0,
        )
    }

    @Test
    fun `CCM solver recovers a noiseless distortion to near-zero dE_2000`() {
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = simulateMeasurements(target, noiseStdDev = 0f, seed = 0L)

        val ccm = CalibrationMath.computeCcm(measured, target)
        val corrected = measured.map { applyCcm(ccm, it) }

        val meanDe = meanDe2000(corrected, target)
        assertTrue(
            "noiseless CCM-corrected mean dE_2000 = $meanDe should be ~0 (got > 0.05)",
            meanDe < 0.05,
        )
    }

    // ---------- Gate 2: cube round-trip shrinks mean dE_2000 by >= 80% ----------

    @Test
    fun `LUT round-trip shrinks mean dE_2000 by at least 80 percent`() {
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = simulateMeasurements(target, noiseStdDev = 0.003f, seed = 4242L)

        // Baseline: how bad is the un-corrected measurement?
        val baselineMeanDe = meanDe2000(measured, target)
        assertTrue(
            "test setup sanity: baseline dE = $baselineMeanDe should be > 1 so the shrinkage gate is meaningful",
            baselineMeanDe > 1.0,
        )

        // Solve a CCM + bake into the same LUT we would actually ship.
        val ccm = CalibrationMath.computeCcm(measured, target)
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains.Identity,
            ccm = ccm,
            bias = CalibrationProfile.Bias.Zero,
            mtf50Lpph = null,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 0L,
            cameraId = "synthetic",
            targetId = BundledReferenceTargets.ColorCheckerClassic24.id,
        )
        val lut = CalibrationToLut.toLut3D(profile, size = 33)

        // Apply the LUT to each measured patch (this is what the ship path does
        // for stills via LutPipeline.applyTrilinear).
        val correctedViaLut = measured.map { rgb ->
            LutPipeline.applyTrilinear(rgb, lut)
        }

        val correctedMeanDe = meanDe2000(correctedViaLut, target)
        val shrinkage = 1.0 - correctedMeanDe / baselineMeanDe
        assertTrue(
            "LUT-corrected mean dE_2000 = $correctedMeanDe " +
                "(baseline=$baselineMeanDe, shrinkage=${"%.2f".format(shrinkage * 100)} %); " +
                "expected >= 80 % shrinkage per BUILD_PLAN §7 V&V gate",
            shrinkage >= 0.80,
        )
    }

    @Test
    fun `LUT and direct CCM apply agree to within 1 LSB on 8 bit`() {
        // Round-trip parity gate: for the same profile, the LUT path must match
        // the direct apply within float precision (1 LSB on 8-bit = 1/255 ~ 0.004).
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = simulateMeasurements(target, noiseStdDev = 0.002f, seed = 999L)
        val ccm = CalibrationMath.computeCcm(measured, target)
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains.Identity,
            ccm = ccm,
            bias = CalibrationProfile.Bias.Zero,
            mtf50Lpph = null,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 0L,
            cameraId = "synthetic",
            targetId = BundledReferenceTargets.ColorCheckerClassic24.id,
        )
        val lut = CalibrationToLut.toLut3D(profile, size = 33)

        for (rgb in measured) {
            val direct = profile.apply(rgb)
            val viaLut = LutPipeline.applyTrilinear(rgb, lut)
            for (ch in 0 until 3) {
                val diff = kotlin.math.abs(direct[ch] - viaLut[ch])
                assertTrue(
                    "LUT vs direct apply diverged: ch=$ch direct=${direct[ch]} lut=${viaLut[ch]} diff=$diff",
                    diff < 1f / 255f,
                )
            }
        }
    }

    // ---------- Generic 24-patch sanity ----------

    @Test
    fun `Generic 24-patch CCM solver also lands within the 1 dE budget`() {
        val target = BundledReferenceTargets.Generic24.patches.map { it.referenceRgb }
        val measured = simulateMeasurements(target, noiseStdDev = 0.005f, seed = 7777L)

        val ccm = CalibrationMath.computeCcm(measured, target)
        val corrected = measured.map { applyCcm(ccm, it) }

        val meanDe = meanDe2000(corrected, target)
        assertTrue(
            "Generic24 CCM-corrected mean dE_2000 = $meanDe should be <= 1.0",
            meanDe <= 1.0,
        )
    }

    // ---------- WB-only correction sanity ----------

    @Test
    fun `WB gains alone shrink the dE of a pure WB drift`() {
        // Construct a "warm cast" without any non-diagonal mixing so it should be
        // fully recoverable by WB gains (no CCM needed).
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = target.map { ref ->
            // r/g/b multiplied by (1.18, 1.0, 0.78): a warm cast.
            floatArrayOf(
                (ref[0] * 1.18f).coerceAtMost(1f),
                ref[1],
                (ref[2] * 0.78f).coerceAtMost(1f),
            )
        }
        val baselineMeanDe = meanDe2000(measured, target)
        assertTrue("WB cast should produce baseline dE > 5 (got $baselineMeanDe)", baselineMeanDe > 5.0)

        val neutrals = BundledReferenceTargets.ColorCheckerClassic24.neutralPatches
            .mapIndexed { idx, patch ->
                val target24Idx = BundledReferenceTargets.ColorCheckerClassic24.patches.indexOf(patch)
                measured[target24Idx]
            }
        val gains = CalibrationMath.computeWbGains(neutrals)

        val corrected = measured.map { rgb ->
            floatArrayOf(
                (rgb[0] * gains.r).coerceIn(0f, 1f),
                (rgb[1] * gains.g).coerceIn(0f, 1f),
                (rgb[2] * gains.b).coerceIn(0f, 1f),
            )
        }
        val correctedMeanDe = meanDe2000(corrected, target)
        assertTrue(
            "WB-corrected dE = $correctedMeanDe should be much smaller than baseline $baselineMeanDe",
            correctedMeanDe < baselineMeanDe * 0.3,
        )
    }

    // ---------- Pinned: a small-rotation matrix is recoverable ----------

    @Test
    fun `CCM solver recovers a small hue rotation cleanly`() {
        // A small rotation in (a, b) plane simulates a tint shift that the WB
        // gains alone cannot fix; the CCM should pick it up.
        val theta = 0.05  // ~ 2.86 degrees
        val rotation = floatArrayOf(
            1f, 0f, 0f,
            0f, cos(theta).toFloat(), -sin(theta).toFloat(),
            0f, sin(theta).toFloat(), cos(theta).toFloat(),
        )
        val target = BundledReferenceTargets.ColorCheckerClassic24.patches.map { it.referenceRgb }
        val measured = target.map { applyMatrix(rotation, it) }

        val ccm = CalibrationMath.computeCcm(measured, target)
        val corrected = measured.map { applyCcm(ccm, it) }

        val meanDe = meanDe2000(corrected, target)
        assertTrue("rotation recovery dE = $meanDe should be < 0.1", meanDe < 0.1)
    }
}
