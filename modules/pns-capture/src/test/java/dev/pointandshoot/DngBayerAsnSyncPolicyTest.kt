package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DngBayerAsnSyncPolicyTest {

    @Test
    fun hybridBayerRHalB_usesBayerRAndHalB() {
        val hal = floatArrayOf(0.6797f, 1f, 0.5977f)
        val out = DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 0.6053f, halAsn = hal)
        assertNotNull(out)
        assertEquals(0.6053f, out!![0], 1e-4f)
        assertEquals(1f, out[1], 1e-4f)
        assertEquals(0.5977f, out[2], 1e-4f)
    }

    @Test
    fun hybridBayerRHalB_invertsSuspectRgAboveOne() {
        val hal = floatArrayOf(0.55f, 1f, 0.69f)
        // Swapped CFA often reports R/G≈1/0.57≈1.75
        val out = DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 1f / 0.5715f, halAsn = hal)
        assertNotNull(out)
        assertEquals(0.5715f, out!![0], 1e-3f)
        assertEquals(1f, out[1], 1e-4f)
    }

    @Test
    fun hybridBayerRHalB_skipsWhenStillUntrusted() {
        val hal = floatArrayOf(0.55f, 1f, 0.69f)
        assertNull(DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 0.20f, halAsn = hal))
        assertNull(DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 0.10f, halAsn = hal))
        // 1.05 inverts to ~0.952 — outside 0.95 upper bound → skip
        assertNull(DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 1.05f, halAsn = hal))
    }

    @Test
    fun hybridBayerRHalB_nightUwTrusted() {
        val hal = floatArrayOf(0.55f, 1f, 0.69f)
        val out = DngBayerAsnSyncPolicy.hybridBayerRHalB(bayerRg = 0.5715f, halAsn = hal)
        assertNotNull(out)
        assertTrue(out!![0] < out[1])
        assertEquals(1f, maxOf(out[0], out[1], out[2]), 1e-5f)
    }

    @Test
    fun enabledByDefaultForUwAsnRoute() {
        assertTrue(DngBayerAsnSyncPolicy.ENABLED)
    }
}
