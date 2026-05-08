package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalibrationToLutTest {

    @Test
    fun `identity profile produces an identity LUT`() {
        val profile = CalibrationProfile.identity(cameraId = "cam0", targetId = "generic24")
        val lut = CalibrationToLut.toLut3D(profile)
        assertTrue(lut.isIdentity(tolerance = 1e-4f))
    }

    @Test
    fun `identity profile cube text round-trips through parseCube`() {
        val profile = CalibrationProfile.identity(cameraId = "cam0", targetId = "generic24")
        val text = CalibrationToLut.toCube(profile)
        val parsed = LutPipeline.parseCube(text)
        assertTrue(parsed.isIdentity(tolerance = 1e-4f))
    }

    @Test
    fun `WB-only profile shifts neutrals predictably`() {
        // Apply r=1.25 (warm), b=0.833 (cool blue dampened): a 50% gray cell
        // should now read warmer (R > G > B).
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains(r = 1.25f, g = 1f, b = 0.833f),
            ccm = CalibrationProfile.Ccm.Identity,
            bias = CalibrationProfile.Bias.Zero,
            mtf50Lpph = null,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 1700000000000L,
            cameraId = "cam0",
            targetId = "generic24",
        )
        val lut = CalibrationToLut.toLut3D(profile, size = 33)
        val out = LutPipeline.applyTrilinear(floatArrayOf(0.5f, 0.5f, 0.5f), lut)
        assertTrue("R should be lifted (got ${out.toList()})", out[0] > out[1])
        assertTrue("B should be dampened (got ${out.toList()})", out[1] > out[2])
        // Green should remain near 0.5 (G gain = 1).
        assertEquals("G untouched", 0.5f, out[1], 5e-3f)
    }

    @Test
    fun `CCM profile rotates RGB primaries toward target colors`() {
        // A CCM that swaps R and G channels.
        val swapRG = CalibrationProfile.Ccm(
            m00 = 0f, m01 = 1f, m02 = 0f,
            m10 = 1f, m11 = 0f, m12 = 0f,
            m20 = 0f, m21 = 0f, m22 = 1f,
        )
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains.Identity,
            ccm = swapRG,
            bias = CalibrationProfile.Bias.Zero,
            mtf50Lpph = null,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 0L,
            cameraId = "cam0",
            targetId = "generic24",
        )
        val lut = CalibrationToLut.toLut3D(profile)
        // Pure red input should come out as pure green.
        val redIn = LutPipeline.applyTrilinear(floatArrayOf(1f, 0f, 0f), lut)
        assertEquals(0f, redIn[0], 1e-3f)
        assertEquals(1f, redIn[1], 1e-3f)
        assertEquals(0f, redIn[2], 1e-3f)
        // Pure green input should come out as pure red.
        val greenIn = LutPipeline.applyTrilinear(floatArrayOf(0f, 1f, 0f), lut)
        assertEquals(1f, greenIn[0], 1e-3f)
        assertEquals(0f, greenIn[1], 1e-3f)
        assertEquals(0f, greenIn[2], 1e-3f)
    }

    @Test
    fun `LUT round-trip preserves WB+CCM apply within trilinear precision`() {
        val truth = CalibrationProfile.Ccm(
            m00 = 1.10f, m01 = -0.05f, m02 = -0.05f,
            m10 = -0.10f, m11 = 1.20f, m12 = -0.10f,
            m20 = 0.00f, m21 = -0.10f, m22 = 1.10f,
        )
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains(r = 1.05f, g = 1f, b = 0.95f),
            ccm = truth,
            bias = CalibrationProfile.Bias(r = 0.01f, g = 0f, b = -0.01f),
            mtf50Lpph = 980f,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 1700000000000L,
            cameraId = "wide",
            targetId = "colorchecker24",
        )
        val lut = CalibrationToLut.toLut3D(profile, size = 33)
        // For a few interior samples, applying the LUT should match applying the profile directly.
        val samples = listOf(
            floatArrayOf(0.2f, 0.4f, 0.6f),
            floatArrayOf(0.7f, 0.3f, 0.5f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
            floatArrayOf(0.1f, 0.8f, 0.2f),
        )
        for (rgb in samples) {
            val direct = profile.apply(rgb)
            val viaLut = LutPipeline.applyTrilinear(rgb, lut)
            for (ch in 0..2) {
                val diff = abs(direct[ch] - viaLut[ch])
                // Trilinear of a smooth profile should match within ~ 1 LSB on 8-bit.
                assertTrue(
                    "channel $ch direct=${direct[ch]} viaLut=${viaLut[ch]} diff=$diff",
                    diff < 0.01f,
                )
            }
        }
    }

    @Test
    fun `mtf50 metadata is preserved on the profile (not encoded into the LUT)`() {
        val profile = CalibrationProfile(
            wbGains = CalibrationProfile.WbGains.Identity,
            ccm = CalibrationProfile.Ccm.Identity,
            bias = CalibrationProfile.Bias.Zero,
            mtf50Lpph = 1100f,
            illuminant = CalibrationProfile.Illuminant.D65,
            capturedAtMs = 0L,
            cameraId = "cam0",
            targetId = "generic24",
        )
        // mtf50 is sharpness, not color: it doesn't change the LUT.
        val lut = CalibrationToLut.toLut3D(profile)
        assertTrue(lut.isIdentity(tolerance = 1e-4f))
        assertEquals(1100f, profile.mtf50Lpph!!, 0f)
    }

    @Test
    fun `default cube title encodes provenance`() {
        val profile = CalibrationProfile.identity(cameraId = "cam0", targetId = "colorchecker24",
            illuminant = CalibrationProfile.Illuminant.D55)
        val text = CalibrationToLut.toCube(profile)
        val firstLine = text.lineSequence().first()
        assertTrue("missing illuminant in '$firstLine'", firstLine.contains("D55"))
        assertTrue("missing cameraId in '$firstLine'", firstLine.contains("cam0"))
        assertTrue("missing targetId in '$firstLine'", firstLine.contains("colorchecker24"))
    }

    @Test
    fun `cube TITLE override wins over default`() {
        val profile = CalibrationProfile.identity(cameraId = "cam0", targetId = "generic24")
        val text = CalibrationToLut.toCube(profile, title = "user-supplied")
        val firstLine = text.lineSequence().first()
        assertEquals("TITLE \"user-supplied\"", firstLine)
        // Default title not present anywhere.
        assertNotEquals(true, text.lineSequence().any { it.contains("Point & Shoot calibration") })
    }
}
