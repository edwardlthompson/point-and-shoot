package dev.pointandshoot

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class MulticamMeltThermalPolicyTest {
    @Test
    fun allowedCameraCount_respectsThermalAndHalCap() {
        assertEquals(
            1,
            MulticamMeltThermalPolicy.allowedCameraCount(
                PowerManager.THERMAL_STATUS_SEVERE,
                halMaxConcurrent = 4,
            ),
        )
        assertEquals(
            2,
            MulticamMeltThermalPolicy.allowedCameraCount(
                PowerManager.THERMAL_STATUS_MODERATE,
                halMaxConcurrent = 4,
            ),
        )
        assertEquals(
            3,
            MulticamMeltThermalPolicy.allowedCameraCount(
                PowerManager.THERMAL_STATUS_LIGHT,
                halMaxConcurrent = 4,
            ),
        )
        assertEquals(
            2,
            MulticamMeltThermalPolicy.allowedCameraCount(
                PowerManager.THERMAL_STATUS_NONE,
                halMaxConcurrent = 2,
            ),
        )
    }
}
