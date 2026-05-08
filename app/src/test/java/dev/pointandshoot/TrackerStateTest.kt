package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerStateTest {

    @Test
    fun `id locks after acquireFrames consecutive presents`() {
        val tracker = TrackerState(acquireFrames = 3, keepAliveFrames = 2)

        val s1 = tracker.update(setOf(7))
        assertTrue(7 in s1.transient)
        assertFalse(7 in s1.locked)

        val s2 = tracker.update(setOf(7))
        assertTrue(7 in s2.transient)

        val s3 = tracker.update(setOf(7))
        assertTrue(7 in s3.locked)
        assertFalse(7 in s3.transient)
    }

    @Test
    fun `locked id survives keepAliveFrames absent before being dropped`() {
        val tracker = TrackerState(acquireFrames = 1, keepAliveFrames = 2)
        tracker.update(setOf(42)) // locks immediately (acquireFrames=1)

        val s1 = tracker.update(emptySet())
        assertTrue(42 in s1.locked)

        val s2 = tracker.update(emptySet())
        assertTrue(42 in s2.locked)

        val s3 = tracker.update(emptySet())
        assertFalse(42 in s3.locked)
        assertFalse(42 in s3.transient)
    }

    @Test
    fun `intermittent presence resets the absent streak and keeps the lock`() {
        val tracker = TrackerState(acquireFrames = 1, keepAliveFrames = 2)
        tracker.update(setOf(99))

        tracker.update(emptySet())     // absent 1
        tracker.update(setOf(99))      // present, resets absent
        tracker.update(emptySet())     // absent 1
        tracker.update(emptySet())     // absent 2 (still alive)
        val s = tracker.snapshot()
        assertTrue(99 in s.locked)
    }

    @Test
    fun `multiple ids tracked independently`() {
        val tracker = TrackerState(acquireFrames = 2, keepAliveFrames = 1)
        tracker.update(setOf(1, 2))
        val s = tracker.update(setOf(1, 2))
        assertEquals(setOf(1, 2), s.locked)

        val s2 = tracker.update(setOf(2)) // 1 absent once
        assertTrue(1 in s2.locked)        // still alive
        assertTrue(2 in s2.locked)

        val s3 = tracker.update(setOf(2)) // 1 absent twice -> drop
        assertFalse(1 in s3.locked)
        assertFalse(1 in s3.transient)
        assertTrue(2 in s3.locked)
    }

    @Test
    fun `reset clears all tracks`() {
        val tracker = TrackerState(acquireFrames = 1)
        tracker.update(setOf(1, 2, 3))
        tracker.reset()
        val s = tracker.snapshot()
        assertTrue(s.locked.isEmpty())
        assertTrue(s.transient.isEmpty())
    }

    @Test
    fun `acquireFrames must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackerState(acquireFrames = 0)
        }
    }

    @Test
    fun `keepAliveFrames may be zero (drop on first absence)`() {
        val tracker = TrackerState(acquireFrames = 1, keepAliveFrames = 0)
        tracker.update(setOf(7))
        val s = tracker.update(emptySet())
        assertFalse(7 in s.locked)
        assertFalse(7 in s.transient)
    }
}
