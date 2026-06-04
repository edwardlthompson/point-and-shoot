package dev.pointandshoot.fleet

import android.graphics.ImageFormat
import dev.pointandshoot.BackCameraRoleResolver
import dev.pointandshoot.BracketPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyFleetPolicyTest {

    @Test
    fun canonicalRolesWhen_applies_mapsDodgeIds() {
        val ids = listOf("0", "1", "2", "3", "4")
        val r = LegacyFleetPolicy.canonicalRolesWhen(deviceApplies = true, ids)!!
        assertEquals("2", r.wide)
        assertEquals("3", r.ultraWide)
        assertEquals("4", r.tele)
        assertNull(r.longTele)
    }

    @Test
    fun canonicalRolesWhen_missingTele_returnsNull() {
        val r = LegacyFleetPolicy.canonicalRolesWhen(deviceApplies = true, listOf("0", "2", "3"))
        assertNull(r)
    }

    @Test
    fun mergeRoles_usesCanonicalWhenDeviceApplies() {
        val enumerated = BackCameraRoleResolver.Roles(wide = "0", ultraWide = "3", tele = "4", longTele = null)
        val ids = listOf("0", "1", "2", "3", "4")
        val merged = LegacyFleetPolicy.mergeRoles(enumerated, ids)
        if (LegacyFleetPolicy.appliesToDevice()) {
            assertEquals("2", merged.wide)
            assertEquals("3", merged.ultraWide)
            assertEquals("4", merged.tele)
        } else {
            assertEquals(enumerated, merged)
        }
    }

    @Test
    fun applyProfileDefaults_leaf_enablesLensShadingOnStill() {
        val tele =
            FleetCameraProfile(
                cameraId = "4",
                role = FleetCameraRole.TELE,
                physicalCameraIds = emptyList(),
                focalLengthsMm = emptyList(),
                rawFormatsAdvertised = listOf(ImageFormat.RAW_SENSOR),
                prefersRawSensor = true,
                lensShadingMapOnStill = false,
                shadingModes = emptySet(),
                supportsDcgSession = false,
                hfrMaxFps = null,
                activeArrayWidth = 4096,
                activeArrayHeight = 3072,
                largestRawSensorWxH = "4096x3072",
                largestRaw12WxH = null,
            )
        if (!LegacyFleetPolicy.appliesToDevice()) return
        assertEquals(true, LegacyFleetPolicy.applyProfileDefaults(tele).lensShadingMapOnStill)
    }

    @Test
    fun legacy_deviceShipped_proShotPureDng_noPostSaveTiffReconcile() {
        if (!LegacyFleetPolicy.appliesToDevice()) return
        assertEquals(true, LegacyFleetPolicy.useReferenceAppPureDngSave())
        assertEquals(false, LegacyFleetPolicy.useLegacyAsnReconcileOnly())
        assertEquals(false, LegacyFleetPolicy.useHalColorCalibrationReconcile())
    }

    @Test
    fun stillDngBackendWhen_legacy_device_isFrameworkReferenceApp() {
        assertEquals(
            StillDngBackend.FRAMEWORK_REFERENCEAPP,
            LegacyFleetPolicy.stillDngBackendWhen(deviceApplies = true),
        )
        assertEquals(
            StillDngBackend.FRAMEWORK_REFERENCEAPP,
            LegacyFleetPolicy.stillDngBackendWhen(deviceApplies = false),
        )
    }

    @Test
    fun shipped13_3g_bisectFlags_defaultOff() {
        assertFalse(LegacyFleetPolicy.useWideLeafCalibrationForAuxDng())
        assertFalse(LegacyFleetPolicy.useReferenceAppStillPrecapture())
        assertFalse(LegacyFleetPolicy.useLegacyAsnReconcileOnly())
        assertFalse(LegacyFleetPolicy.useHalColorCalibrationReconcile())
        assertFalse(
            LegacyFleetPolicy.proShotLatchManualExposureOnStill(LegacyFleetPolicy.CANONICAL_TELE),
        )
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = false,
            ),
        )
    }

    @Test
    fun hdrStillBracket_isThreeShotsOneEv() {
        assertEquals(BracketPattern.Three, LegacyFleetPolicy.hdrStillBracketPattern())
        assertEquals(3, LegacyFleetPolicy.hdrStillShotCount())
        assertEquals(1.0, LegacyFleetPolicy.hdrStillEvStep(), 0.001)
    }

    @Test
    fun zslStillRingCapacity_whenOp13_isSix() {
        if (!LegacyFleetPolicy.appliesToDevice()) {
            assertEquals(4, LegacyFleetPolicy.zslStillRingCapacity())
        } else {
            assertEquals(6, LegacyFleetPolicy.zslStillRingCapacity())
        }
    }

    @Test
    fun leafRawFormatOrder_matchesReferenceAppSequence() {
        assertEquals(
            listOf(
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW10,
                ImageFormat.RAW12,
                ImageFormat.RAW_PRIVATE,
            ),
            LegacyFleetPolicy.LEAF_RAW_FORMAT_ORDER,
        )
    }
}
