package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketBurstSupportTest {

    private val burstOnly: IntArray =
        intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)

    @Test
    fun mayUseSingleCaptureBurst_falseWhenManualSensor() {
        assertFalse(
            BracketBurstSupport.mayUseSingleCaptureBurst(
                availableCapabilities = burstOnly,
                shotCount = 3,
                readerMaxImages = 4,
                manualSensorBracket = true,
            ),
        )
    }

    @Test
    fun mayUseSingleCaptureBurst_falseWhenShotCountExceedsReaderDepth() {
        assertFalse(
            BracketBurstSupport.mayUseSingleCaptureBurst(
                availableCapabilities = burstOnly,
                shotCount = 5,
                readerMaxImages = 4,
                manualSensorBracket = false,
            ),
        )
    }

    @Test
    fun mayUseSingleCaptureBurst_falseWithoutBurstCapability() {
        assertFalse(
            BracketBurstSupport.mayUseSingleCaptureBurst(
                availableCapabilities = intArrayOf(),
                shotCount = 3,
                readerMaxImages = 4,
                manualSensorBracket = false,
            ),
        )
    }

    @Test
    fun mayUseSingleCaptureBurst_trueForThreeShotWhenBurstAdvertised() {
        assertTrue(
            BracketBurstSupport.mayUseSingleCaptureBurst(
                availableCapabilities = burstOnly,
                shotCount = 3,
                readerMaxImages = 4,
                manualSensorBracket = false,
            ),
        )
    }

    @Test
    fun mayUseSingleCaptureBurst_falseWhenCapabilitiesNull() {
        assertFalse(
            BracketBurstSupport.mayUseSingleCaptureBurst(
                availableCapabilities = null,
                shotCount = 3,
                readerMaxImages = 4,
                manualSensorBracket = false,
            ),
        )
    }
}
