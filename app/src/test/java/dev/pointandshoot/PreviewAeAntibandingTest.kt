package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewAeAntibandingTest {

    @Test
    fun pick_prefersAutoThen50Then60() {
        val only60 =
            intArrayOf(
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ,
            )
        assertEquals(
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ,
            PreviewAeAntibanding.pickAntibandingMode(only60),
        )
        val auto50 =
            intArrayOf(
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ,
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO,
            )
        assertEquals(
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO,
            PreviewAeAntibanding.pickAntibandingMode(auto50),
        )
    }

    @Test
    fun pick_emptyIsNull() {
        assertNull(PreviewAeAntibanding.pickAntibandingMode(intArrayOf()))
    }

    @Test
    fun pick_unknownFallsBackToFirst() {
        val custom = intArrayOf(42, 43)
        assertEquals(42, PreviewAeAntibanding.pickAntibandingMode(custom))
    }
}
