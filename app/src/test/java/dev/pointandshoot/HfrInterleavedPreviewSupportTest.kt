package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HfrInterleavedPreviewSupportTest {
    @Test
    fun wantsInterleaved_requires120McNonDual() {
        assertTrue(
            HfrInterleavedPreviewSupport.wantsInterleavedSession(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                dualVideoActive = false,
            ),
        )
        assertFalse(
            HfrInterleavedPreviewSupport.wantsInterleavedSession(
                desiredFps = 60,
                wantsMediaCodecPath = true,
                dualVideoActive = false,
            ),
        )
        assertFalse(
            HfrInterleavedPreviewSupport.wantsInterleavedSession(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                dualVideoActive = true,
            ),
        )
    }

    @Test
    fun sessionOutputs_nullWhenInvalid() {
        assertNull(HfrInterleavedPreviewSupport.sessionOutputs(null, null))
    }
}
