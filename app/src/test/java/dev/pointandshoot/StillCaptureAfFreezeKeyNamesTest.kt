package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class StillCaptureAfFreezeKeyNamesTest {
    @Test
    fun `metadata names match Android Camera2`() {
        assertEquals("android.control.afLock", StillCaptureAfFreeze.ANDROID_CONTROL_AF_LOCK)
        assertEquals("android.control.afLockAvailable", StillCaptureAfFreeze.ANDROID_CONTROL_AF_LOCK_AVAILABLE)
    }
}
