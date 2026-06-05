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

    @Test
    fun prefersInterleavedOverEncoderOnly_for4kHs() {
        assertTrue(
            HfrInterleavedPreviewSupport.prefersInterleavedOverEncoderOnlyFor4KEncode(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                encodePrefWidth = 3840,
                encodePrefHeight = 2160,
                hsCaptureWidth = 3840,
                hsCaptureHeight = 2160,
                preferSub4kCapture = false,
                forceInterleavedAfterConfigureFail = false,
            ),
        )
        assertTrue(
            HfrInterleavedPreviewSupport.prefersInterleavedOverEncoderOnlyFor4KEncode(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                encodePrefWidth = 3840,
                encodePrefHeight = 2160,
                hsCaptureWidth = 1920,
                hsCaptureHeight = 1080,
                preferSub4kCapture = false,
                forceInterleavedAfterConfigureFail = false,
            ),
        )
        assertFalse(
            HfrInterleavedPreviewSupport.prefersInterleavedOverEncoderOnlyFor4KEncode(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                encodePrefWidth = 1920,
                encodePrefHeight = 1080,
                hsCaptureWidth = 3840,
                hsCaptureHeight = 2160,
                preferSub4kCapture = false,
                forceInterleavedAfterConfigureFail = false,
            ),
        )
    }

    @Test
    fun prefersInterleaved_encoderPriorityFallbackSkipsInterleaved() {
        assertFalse(
            HfrInterleavedPreviewSupport.prefersInterleavedOverEncoderOnlyFor4KEncode(
                desiredFps = 120,
                wantsMediaCodecPath = true,
                encodePrefWidth = 3840,
                encodePrefHeight = 2160,
                hsCaptureWidth = 3840,
                hsCaptureHeight = 2160,
                preferSub4kCapture = false,
                forceInterleavedAfterConfigureFail = false,
                encoderPriorityFallback = true,
            ),
        )
    }
}
