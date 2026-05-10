package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewReadoutFormatTest {
    @Test
    fun formatShutter_fractionForFastExposure() {
        val ns = (1_000_000_000.0 / 120.0).toLong()
        assertEquals("1/120", PreviewReadoutFormat.formatShutter(ns))
    }

    @Test
    fun formatShutter_secondsForLongExposure() {
        val ns = 2_000_000_000L
        assertEquals("2.0s", PreviewReadoutFormat.formatShutter(ns))
    }

    @Test
    fun formatShutter_nullOrInvalid() {
        assertEquals("—", PreviewReadoutFormat.formatShutter(null))
        assertEquals("—", PreviewReadoutFormat.formatShutter(0L))
    }

    @Test
    fun awbModeLabel_mapsAuto() {
        assertEquals("AWB", PreviewReadoutFormat.awbModeLabel(CaptureResult.CONTROL_AWB_MODE_AUTO))
    }
}
