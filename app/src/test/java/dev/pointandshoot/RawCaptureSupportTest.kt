package dev.pointandshoot

import android.graphics.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawCaptureSupportTest {

    /**
     * JVM unit tests cannot construct [android.util.Size] (android.jar stubs throw [RuntimeException]).
     * [RawStreamPreference.Default] follows **RAW12 → RAW_SENSOR → RAW10** (fleet scripted-DNG default);
     * use [RawStreamPreference.RawSensorFirst] when probing HALs that need SENSOR before RAW12/RAW10.
     */

    @Test
    fun pickRawOutputFromMaps_returnsNullWhenEmpty() {
        assertNull(
            RawCaptureSupport.pickRawOutputFromMaps(
                raw12 = emptyList(),
                raw10 = emptyList(),
                rawSensor = emptyList(),
            ),
        )
    }

    @Test
    fun pickRawOutputFromMaps_raw10Only_returnsNullWhenNoRaw10() {
        assertNull(
            RawCaptureSupport.pickRawOutputFromMaps(
                raw12 = emptyList(),
                raw10 = emptyList(),
                rawSensor = emptyList(),
                preference = RawStreamPreference.Raw10Only,
            ),
        )
    }

    @Test
    fun rawPickEffectiveLabel_mapsFormats() {
        assertEquals("RAW12", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW12))
        assertEquals("RAW10", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW10))
        assertEquals("RAW_SENSOR", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW_SENSOR))
        assertEquals("null", RawCaptureSupport.rawPickEffectiveLabel(null))
    }
}
