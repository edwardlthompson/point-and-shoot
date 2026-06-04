package dev.pointandshoot.fleet

import dev.pointandshoot.DngBayerAsShotNeutral
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAppReferenceCalibrationTest {

    @Test
    fun parseSlot_hasNineMatricesAndAsn() {
        val json =
            """
            {
              "schema": "legacy_device_referenceapp_calibration.v1",
              "slots": {
                "3": {
                  "asn_rational_nd": [1, 1000000, 1, 1000000, 1, 1000000],
                  "color_matrix1_srational_nd": [1,1,0,1,0,1,0,1,1,1,0,1,0,1,0,1,1,1],
                  "color_matrix2_srational_nd": [2,1,0,1,0,1,0,1,1,1,0,1,0,1,0,1,1,1],
                  "forward_matrix1_srational_nd": [3,1,0,1,0,1,0,1,1,1,0,1,0,1,0,1,1,1],
                  "forward_matrix2_srational_nd": [4,1,0,1,0,1,0,1,1,1,0,1,0,1,0,1,1,1],
                  "bayer_rg": 0.94,
                  "bayer_bg": 1.20
                }
              }
            }
            """.trimIndent()
        val slot = ReferenceAppReferenceCalibration.parse(JSONObject(json))["3"]!!
        assertEquals(6, slot.asnRationalNd.size)
        assertEquals(18, slot.colorMatrix1Nd.size)
        assertEquals(2, slot.colorMatrix2Nd[0])
        assertEquals(4, slot.forwardMatrix2Nd[0])
        assertEquals(0.94f, slot.bayerRg, 0.001f)
    }

    @Test
    fun adjustAsnToTargetBayerRatios_shiftsTowardTarget() {
        val asn = floatArrayOf(0.9f, 1f, 1.1f)
        val out =
            DngBayerAsShotNeutral.adjustAsnToTargetBayerRatios(
                asn,
                currentRg = 1.0f,
                currentBg = 1.1f,
                targetRg = 0.9f,
                targetBg = 1.0f,
            )
        assertEquals(1f, maxOf(out[0], out[1], out[2]), 0.001f)
        val outRg = out[0] / out[1]
        val inRg = asn[0] / asn[1]
        assertTrue(outRg < inRg)
    }

    @Test
    fun leafReconcile_proShotReference_uwAndTeleNotWide() {
        for (cam in listOf("3", "4")) {
            assertTrue(
                LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                    deviceApplies = true,
                    backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                    sessionCameraId = cam,
                    proShotPureDngSave = true,
                    proShotReferenceCalibration = true,
                    uwReferenceAppAsnReconcile = false,
                ),
            )
        }
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = "2",
                proShotPureDngSave = true,
                proShotReferenceCalibration = true,
                uwReferenceAppAsnReconcile = false,
            ),
        )
    }
}
