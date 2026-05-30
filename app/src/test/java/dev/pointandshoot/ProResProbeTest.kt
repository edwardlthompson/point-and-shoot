package dev.pointandshoot

import org.junit.Assert.assertNotNull
import org.junit.Test

class ProResProbeTest {
    @Test
    fun probeSync_returnsResult() {
        val result = ProResProbe.probeSync()
        assertNotNull(result.detail)
    }
}
