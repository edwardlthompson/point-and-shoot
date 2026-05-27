package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppVideoRecordingSupportTest {
    @Test
    fun highSpeedFpsForEncodeSize_nullMap_returnsEmpty() {
        assertTrue(InAppVideoRecordingSupport.highSpeedFpsForEncodeSize(null, 1920, 1080).isEmpty())
    }

    @Test
    fun shortLabel_1080p() {
        assertEquals("1080p", InAppVideoRecordingSupport.shortLabelForDims(1920, 1080))
    }

    @Test
    fun isEightKSize_detects_uhd_tier() {
        assertTrue(InAppVideoRecordingSupport.isEightKSize(7680, 4320))
        assertTrue(!InAppVideoRecordingSupport.isEightKSize(3840, 2160))
    }
}
