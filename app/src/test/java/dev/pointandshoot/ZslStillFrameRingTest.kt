package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZslStillFrameRingTest {
    @Test
    fun ring_capsIncompleteSlots() {
        val ring = ZslStillFrameRing(capacity = 2)
        repeat(3) { ring.offerPlaceholder() }
        assertEquals(2, ring.size())
        assertEquals(0, ring.completeCount())
        assertNull(ring.peekBestForStill())
        assertNull(ring.takeBestForStill())
    }

    @Test
    fun clear_emptiesRing() {
        val ring = ZslStillFrameRing(capacity = 4)
        ring.offerPlaceholder()
        ring.offerPlaceholder()
        ring.clear(closeImages = false)
        assertEquals(0, ring.size())
    }
}
