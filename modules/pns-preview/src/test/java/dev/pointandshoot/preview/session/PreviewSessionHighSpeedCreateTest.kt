package dev.pointandshoot.preview.session

import android.util.Range
import android.util.Size
import dev.pointandshoot.PreviewVideoConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionHighSpeedCreateTest {

    @Test
    fun shouldUseHighSpeed_whenTargetAndFpsAtThreshold() {
        assertTrue(
            PreviewSessionHighSpeedCreate.shouldUseHighSpeedSession(
                highSpeedTarget = Size(1920, 1080) to Range.create(120, 120),
                desiredFps = PreviewVideoConstants.HFR_THRESHOLD_FPS,
            ),
        )
    }

    @Test
    fun shouldUseHighSpeed_falseWithoutTarget() {
        assertFalse(
            PreviewSessionHighSpeedCreate.shouldUseHighSpeedSession(
                highSpeedTarget = null,
                desiredFps = 120,
            ),
        )
    }

    @Test
    fun shouldUseHighSpeed_falseBelowThreshold() {
        assertFalse(
            PreviewSessionHighSpeedCreate.shouldUseHighSpeedSession(
                highSpeedTarget = Size(1920, 1080) to Range.create(60, 60),
                desiredFps = 60,
            ),
        )
    }

    @Test
    fun shouldForceEncoderSdr_singleEncoderOutput() {
        assertTrue(
            PreviewSessionHighSpeedCreate.shouldForceEncoderOutputSdr(
                inAppVideoRecordingArmed = true,
                recorderPresent = true,
                wantsMediaCodecPath = true,
                hfrOutputCount = 1,
            ),
        )
    }

    @Test
    fun isSurfaceAbandonedError_matchesIllegalArgument() {
        assertTrue(
            PreviewSessionHighSpeedCreate.isSurfaceAbandonedError(
                IllegalArgumentException("Surface was abandoned"),
            ),
        )
    }

    @Test
    fun shouldScheduleSurfaceAbandonRetry_underCap() {
        assertTrue(PreviewSessionHighSpeedCreate.shouldScheduleSurfaceAbandonRetry(0))
        assertFalse(
            PreviewSessionHighSpeedCreate.shouldScheduleSurfaceAbandonRetry(
                PreviewSessionHighSpeedCreate.SURFACE_ABANDON_RETRY_CAP,
            ),
        )
    }

    @Test
    fun shouldStartMediaCodecBeforeHfrRepeating_whenArmedAndNotStarted() {
        assertTrue(
            PreviewSessionHighSpeedCreate.shouldStartMediaCodecBeforeHfrRepeating(
                inAppVideoRecordingArmed = true,
                recorderPresent = true,
                wantsMediaCodecPath = true,
                recorderStarted = false,
            ),
        )
    }
}
