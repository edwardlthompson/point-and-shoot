package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewAeLockTest {
    @Test
    fun requestAeLockValue_lockedAndAvailable_isTrue() {
        assertTrue(PreviewAeLock.requestAeLockValue(locked = true, lockAvailable = true))
    }

    @Test
    fun requestAeLockValue_lockedUnavailable_isFalse() {
        assertFalse(PreviewAeLock.requestAeLockValue(locked = true, lockAvailable = false))
    }

    @Test
    fun requestAeLockValue_unlocked_isFalse() {
        assertFalse(PreviewAeLock.requestAeLockValue(locked = false, lockAvailable = true))
    }
}
