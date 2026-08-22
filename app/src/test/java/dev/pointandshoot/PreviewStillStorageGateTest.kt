package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewStillStorageGateTest {
    @Test
    fun hasRoomForStill_failsClosedOnlyWhenBytesKnownAndLow() {
        assertTrue(PreviewStillStorageGate.hasRoomForStill(null))
        assertTrue(PreviewStillStorageGate.hasRoomForStill(PreviewStillStorageGate.MIN_FREE_BYTES))
        assertFalse(PreviewStillStorageGate.hasRoomForStill(PreviewStillStorageGate.MIN_FREE_BYTES - 1L))
    }

    @Test
    fun hasRoomForStill_scalesWithFrameCount() {
        val two = PreviewStillStorageGate.requiredBytes(2)
        assertTrue(PreviewStillStorageGate.hasRoomForStill(two, 2))
        assertFalse(PreviewStillStorageGate.hasRoomForStill(two - 1L, 2))
        assertTrue(PreviewStillStorageGate.hasRoomForIntervalometer(two))
        assertFalse(PreviewStillStorageGate.hasRoomForIntervalometer(two - 1L))
    }

    @Test
    fun plannedFrameCount_prefersHdrThenBurst() {
        assertEquals(3, PreviewStillStorageGate.plannedFrameCount(true, 3, true, 8))
        assertEquals(5, PreviewStillStorageGate.plannedFrameCount(false, 3, true, 5))
        assertEquals(1, PreviewStillStorageGate.plannedFrameCount(false, 3, false, 5))
        assertEquals(
            7,
            PreviewStillStorageGate.plannedFrameCount(
                hdrStill = false,
                hdrShotCount = 3,
                burstEnabled = false,
                burstCount = 5,
                bracketEnabled = true,
                bracketCount = 7,
            ),
        )
        assertEquals(
            5,
            PreviewStillStorageGate.plannedFrameCount(
                hdrStill = false,
                hdrShotCount = 3,
                burstEnabled = true,
                burstCount = 5,
                bracketEnabled = true,
                bracketCount = 7,
            ),
        )
    }

    @Test
    fun fewStillsWarning_isBetweenOneAndThreeFloors() {
        val one = PreviewStillStorageGate.requiredBytes(1)
        assertFalse(PreviewStillStorageGate.isFewStillsWarning(null))
        assertFalse(PreviewStillStorageGate.isFewStillsWarning(one - 1L))
        assertTrue(PreviewStillStorageGate.isFewStillsWarning(one))
        assertTrue(PreviewStillStorageGate.isFewStillsWarning(one * 3L - 1L))
        assertFalse(PreviewStillStorageGate.isFewStillsWarning(one * 3L))
    }

    @Test
    fun remainingShots_dividesFloor() {
        val one = PreviewStillStorageGate.requiredBytes(1)
        assertEquals(null, PreviewStillStorageGate.remainingShots(null))
        assertEquals(3, PreviewStillStorageGate.remainingShots(one * 3L))
        assertEquals(0, PreviewStillStorageGate.remainingShots(one - 1L))
    }

    @Test
    fun holdBurstFrameBudget_isTwoWhenRawPlusJpeg() {
        assertEquals(1, PreviewStillStorageGate.holdBurstFrameBudget(false))
        assertEquals(2, PreviewStillStorageGate.holdBurstFrameBudget(true))
    }

    @Test
    fun intervalometerTickFrames_scalesPhotoAndKeepsVideoAtOne() {
        assertEquals(
            1,
            PreviewStillStorageGate.intervalometerTickFrames(
                videoMode = true,
                nightScapeEnabled = true,
                nightScapeCount = 8,
                hdrStill = true,
                hdrShotCount = 3,
                burstEnabled = true,
                burstCount = 5,
                bracketEnabled = true,
                bracketCount = 7,
            ),
        )
        assertEquals(
            8,
            PreviewStillStorageGate.intervalometerTickFrames(
                videoMode = false,
                nightScapeEnabled = true,
                nightScapeCount = 8,
                hdrStill = true,
                hdrShotCount = 3,
                burstEnabled = false,
                burstCount = 5,
                bracketEnabled = false,
                bracketCount = 3,
            ),
        )
        assertEquals(
            3,
            PreviewStillStorageGate.intervalometerTickFrames(
                videoMode = false,
                nightScapeEnabled = false,
                nightScapeCount = 8,
                hdrStill = true,
                hdrShotCount = 3,
                burstEnabled = false,
                burstCount = 5,
                bracketEnabled = true,
                bracketCount = 7,
            ),
        )
    }
}
