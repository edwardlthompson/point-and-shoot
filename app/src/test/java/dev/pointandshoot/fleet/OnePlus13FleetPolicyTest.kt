package dev.pointandshoot.fleet

import android.graphics.ImageFormat
import dev.pointandshoot.BackCameraRoleResolver
import dev.pointandshoot.BracketPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnePlus13FleetPolicyTest {

    @Test
    fun canonicalRolesWhen_applies_mapsDodgeIds() {
        val ids = listOf("0", "1", "2", "3", "4")
        val r = OnePlus13FleetPolicy.canonicalRolesWhen(deviceApplies = true, ids)!!
        assertEquals("2", r.wide)
        assertEquals("3", r.ultraWide)
        assertEquals("4", r.tele)
        assertNull(r.longTele)
    }

    @Test
    fun canonicalRolesWhen_missingTele_returnsNull() {
        val r = OnePlus13FleetPolicy.canonicalRolesWhen(deviceApplies = true, listOf("0", "2", "3"))
        assertNull(r)
    }

    @Test
    fun mergeRoles_usesCanonicalWhenDeviceApplies() {
        val enumerated = BackCameraRoleResolver.Roles(wide = "0", ultraWide = "3", tele = "4", longTele = null)
        val ids = listOf("0", "1", "2", "3", "4")
        val merged = OnePlus13FleetPolicy.mergeRoles(enumerated, ids)
        if (OnePlus13FleetPolicy.appliesToDevice()) {
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
        if (!OnePlus13FleetPolicy.appliesToDevice()) return
        assertEquals(true, OnePlus13FleetPolicy.applyProfileDefaults(tele).lensShadingMapOnStill)
    }

    @Test
    fun op13Shipped_proShotPureDng_noPostSaveTiffReconcile() {
        if (!OnePlus13FleetPolicy.appliesToDevice()) return
        assertEquals(true, OnePlus13FleetPolicy.useProShotPureDngSave())
        assertEquals(false, OnePlus13FleetPolicy.useOp13AsnReconcileOnly())
        assertEquals(false, OnePlus13FleetPolicy.useHalColorCalibrationReconcile())
    }

    @Test
    fun stillDngBackendWhen_op13_isFrameworkProShot() {
        assertEquals(
            StillDngBackend.FRAMEWORK_PROSHOT,
            OnePlus13FleetPolicy.stillDngBackendWhen(deviceApplies = true),
        )
        assertEquals(
            StillDngBackend.FRAMEWORK_PROSHOT,
            OnePlus13FleetPolicy.stillDngBackendWhen(deviceApplies = false),
        )
    }

    @Test
    fun shipped13_3g_bisectFlags_defaultOff() {
        assertFalse(OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng())
        assertFalse(OnePlus13FleetPolicy.useProShotStillPrecapture())
        assertFalse(OnePlus13FleetPolicy.useOp13AsnReconcileOnly())
        assertFalse(OnePlus13FleetPolicy.useHalColorCalibrationReconcile())
        assertFalse(
            OnePlus13FleetPolicy.proShotLatchManualExposureOnStill(OnePlus13FleetPolicy.CANONICAL_TELE),
        )
        assertFalse(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = false,
            ),
        )
    }

    @Test
    fun hdrStillBracket_isThreeShotsOneEv() {
        assertEquals(BracketPattern.Three, OnePlus13FleetPolicy.hdrStillBracketPattern())
        assertEquals(3, OnePlus13FleetPolicy.hdrStillShotCount())
        assertEquals(1.0, OnePlus13FleetPolicy.hdrStillEvStep(), 0.001)
    }

    @Test
    fun zslStillRingCapacity_whenOp13_isSix() {
        if (!OnePlus13FleetPolicy.appliesToDevice()) {
            assertEquals(4, OnePlus13FleetPolicy.zslStillRingCapacity())
        } else {
            assertEquals(6, OnePlus13FleetPolicy.zslStillRingCapacity())
        }
    }

    @Test
    fun leafRawFormatOrder_matchesProShotSequence() {
        assertEquals(
            listOf(
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW10,
                ImageFormat.RAW12,
                ImageFormat.RAW_PRIVATE,
            ),
            OnePlus13FleetPolicy.LEAF_RAW_FORMAT_ORDER,
        )
    }
}
