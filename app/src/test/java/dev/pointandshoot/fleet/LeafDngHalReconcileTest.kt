package dev.pointandshoot.fleet

import dev.pointandshoot.DngForwardMatrixFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafDngHalReconcileTest {

    @Test
    fun shouldReconcileWhen_proShotBackend_uwAndTele_notWide_andNotPureDng() {
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = "3",
                proShotPureDngSave = false,
            ),
        )
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = "4",
                proShotPureDngSave = false,
            ),
        )
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = "2",
                proShotPureDngSave = false,
            ),
        )
    }

    @Test
    fun proShotPureDng_uwAndTeleAsnFm_whenM15AuxColorFlag() {
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = true,
            ),
        )
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_TELE,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = true,
            ),
        )
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = false,
            ),
        )
    }

    @Test
    fun shouldReconcile_auxWhen_wideLeafCalibration_evenIfPureDngFlag() {
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_TELE,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            ),
        )
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_WIDE,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            ),
        )
    }

    @Test
    fun shouldReconcileWhen_motionCamInspiredBackend_disabled() {
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.ALTREFERENCEAPP_INSPIRED,
                sessionCameraId = "3",
            ),
        )
    }

    @Test
    fun normalizeAsShotNeutralTriplet_normalizesToMaxOne() {
        val asn = LeafDngHalReconcile.normalizeAsShotNeutralTriplet(0.5f, 1f, 0.25f)
        assertEquals(0.5f, asn[0], 0.001f)
        assertEquals(1f, asn[1], 0.001f)
        assertEquals(0.25f, asn[2], 0.001f)
    }

    @Test
    fun halWbCorrectionTable_teleAndUwRegisteredForCph2655() {
        val tele =
            DngForwardMatrixFix.getWbCorrection("legacy_sku", LegacyFleetPolicy.CANONICAL_TELE)
        assertNotNull(tele)
        assertEquals(1.602f, tele!!.scaleR, 0.001f)
        assertEquals(1.147f, tele.scaleB, 0.001f)
        val uw =
            DngForwardMatrixFix.getWbCorrection("legacy_sku", LegacyFleetPolicy.CANONICAL_UW)
        assertNotNull(uw)
        assertEquals(1.147f, uw!!.scaleR, 0.001f)
    }
}
