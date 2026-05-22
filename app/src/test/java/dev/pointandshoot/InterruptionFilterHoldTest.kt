package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class InterruptionFilterHoldTest {
    @Before
    fun reset() {
        InterruptionFilterHold.resetForTests()
    }

    @Test
    fun releasePreviewHold_withoutAcquire_returnsFalse() {
        assertEquals(0, InterruptionFilterHold.previewRefCountForTests())
    }

    @Test
    fun releaseAllPreviewHolds_withoutAcquire_returnsFalse() {
        assertFalse(InterruptionFilterHold.previewRefCountForTests() > 0)
    }

    @Test
    fun resetForTests_clearsRefState() {
        InterruptionFilterHold.resetForTests()
        assertEquals(0, InterruptionFilterHold.refCountForTests())
        assertEquals(0, InterruptionFilterHold.previewRefCountForTests())
        assertEquals(0, InterruptionFilterHold.recordingRefCountForTests())
    }
}
