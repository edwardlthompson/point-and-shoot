package dev.pointandshoot

import android.graphics.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawCaptureSupportTest {

    /**
     * JVM unit tests cannot construct [android.util.Size] (android.jar stubs throw [RuntimeException]).
     * **RAW12 → RAW10 → RAW_SENSOR** ordering is validated on-device via probe **`rawPickEffective=`** lines.
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
    fun rawPickEffectiveLabel_mapsFormats() {
        assertEquals("RAW12", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW12))
        assertEquals("RAW10", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW10))
        assertEquals("RAW_SENSOR", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW_SENSOR))
        assertEquals("null", RawCaptureSupport.rawPickEffectiveLabel(null))
    }
}
