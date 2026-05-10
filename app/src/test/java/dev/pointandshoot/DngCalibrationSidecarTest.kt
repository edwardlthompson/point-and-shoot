package dev.pointandshoot

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DngCalibrationSidecarTest {

    @Test
    fun `displayNameForSiblingDng swaps extension`() {
        assertEquals(
            "pns_20260101T120000Z_standard_pro_0001_bkt1of3-x.pns-calibration.json",
            DngCalibrationSidecar.displayNameForSiblingDng(
                "pns_20260101T120000Z_standard_pro_0001_bkt1of3-x.dng",
            ),
        )
    }

    @Test
    fun `encode contains magic version and float arrays`() {
        val profile =
            CalibrationProfile(
                wbGains = CalibrationProfile.WbGains(1.1f, 1f, 0.95f),
                ccm = CalibrationProfile.Ccm.Identity,
                bias = CalibrationProfile.Bias.Zero,
                mtf50Lpph = null,
                illuminant = CalibrationProfile.Illuminant.D65,
                capturedAtMs = 1L,
                cameraId = "0",
                targetId = "cc24",
            )
        val color = DngColorTags.forProfile(profile)
        val text =
            DngCalibrationSidecar.encode(profile, color, "/data/user/0/foo/calibration/D65_1.json")
        val o = JSONObject(text)
        assertEquals(DngCalibrationSidecar.MAGIC, o.getString("magic"))
        assertEquals(DngCalibrationSidecar.VERSION, o.getInt("version"))
        assertEquals("/data/user/0/foo/calibration/D65_1.json", o.getString("sourceProfilePath"))
        assertEquals(3, o.getJSONArray("asShotNeutral").length())
        assertEquals(9, o.getJSONArray("colorMatrix1").length())
        assertEquals(9, o.getJSONArray("forwardMatrix1").length())
        assertTrue(o.getInt("calibrationIlluminant1") > 0)
    }
}
