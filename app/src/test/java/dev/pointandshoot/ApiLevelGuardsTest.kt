package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiLevelGuardsTest {
    @Test
    fun thermalLabels_useGuardConstants_withoutPowerManagerFields() {
        assertEquals("OK", PreviewThermalLabels.labelForStatus(ApiLevelGuards.THERMAL_STATUS_NONE))
        assertEquals("HOT", PreviewThermalLabels.labelForStatus(ApiLevelGuards.THERMAL_STATUS_MODERATE))
        assertFalse(PreviewThermalLabels.isThermalWarning(ApiLevelGuards.THERMAL_STATUS_LIGHT))
        assertEquals(
            PreviewThermalLabels.THERMAL_WARNING_MIN_STATUS,
            ApiLevelGuards.THERMAL_STATUS_MODERATE,
        )
    }

    @Test
    fun logicalMultiCameraKeyName_matchesSdkConstantWhenPresent() {
        assertEquals(
            "android.logical.multiCamera.activePhysicalId",
            ApiLevelGuards.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID_NAME,
        )
    }
}
