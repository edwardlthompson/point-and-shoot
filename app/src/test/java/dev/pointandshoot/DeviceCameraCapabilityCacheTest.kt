package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCameraCapabilityCacheTest {

    @Test
    fun `fingerprintSha256Prefix is stable length`() {
        val p = DeviceCameraCapabilityCache.fingerprintSha256Prefix("test-fingerprint", prefixLen = 16)
        assertEquals(16, p.length)
    }

    @Test
    fun `hfrMaxAtSizeClasses returns null when no high speed`() {
        val triple = DeviceCameraCapabilityCache.hfrMaxAtSizeClasses(null)
        assertNull(triple.first)
        assertNull(triple.second)
        assertNull(triple.third)
    }
}
