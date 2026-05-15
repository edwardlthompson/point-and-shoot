package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadoutExposureCatalogAwbOrderTest {

    @Test
    fun `CaptureRequest and CaptureResult AWB mode constants match`() {
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureResult.CONTROL_AWB_MODE_AUTO)
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_OFF, CaptureResult.CONTROL_AWB_MODE_OFF)
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_SHADE, CaptureResult.CONTROL_AWB_MODE_SHADE)
    }
    @Test
    fun `full standard set orders AWB then coldest to warmest then OFF`() {
        val avail =
            intArrayOf(
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
                CaptureRequest.CONTROL_AWB_MODE_OFF,
                CaptureRequest.CONTROL_AWB_MODE_SHADE,
                CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
                CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
            )
        val out = ReadoutExposureCatalog.awbChoicesFromAvailableModes(avail)
        val expected =
            listOf(
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
                CaptureRequest.CONTROL_AWB_MODE_SHADE,
                CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
                CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                CaptureRequest.CONTROL_AWB_MODE_OFF,
            )
        assertEquals(expected, out)
    }

    @Test
    fun `vendor mode follows kelvin block before OFF`() {
        val avail =
            intArrayOf(
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
                999,
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                CaptureRequest.CONTROL_AWB_MODE_OFF,
            )
        val out = ReadoutExposureCatalog.awbChoicesFromAvailableModes(avail)
        assertEquals(
            listOf(
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                999,
                CaptureRequest.CONTROL_AWB_MODE_OFF,
            ),
            out,
        )
    }

    @Test
    fun `empty available returns empty list`() {
        assertEquals(emptyList<Int>(), ReadoutExposureCatalog.awbChoicesFromAvailableModes(intArrayOf()))
    }
}
