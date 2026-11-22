package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FossSmileEstimatorTest {

    @Test
    fun `bright center scores higher than uniform`() {
        val w = 12
        val h = 6
        val teeth = ByteArray(w * h) { i ->
            val x = i % w
            when {
                x in 4..7 -> 200.toByte()
                else -> 40.toByte()
            }
        }
        val uniform = ByteArray(w * h) { 80.toByte() }
        val smile = FossSmileEstimator.probabilityFromMouthLuma(teeth, w, h)
        val flat = FossSmileEstimator.probabilityFromMouthLuma(uniform, w, h)
        assertNotNull(smile)
        assertNotNull(flat)
        assertTrue(smile!! > flat!!)
        assertTrue(smile > 0.70f)
    }

    @Test
    fun `rejects tiny band`() {
        assertNull(FossSmileEstimator.probabilityFromMouthLuma(ByteArray(4), 2, 2))
    }

    @Test
    fun `uniform luma sits near the midpoint scale`() {
        val w = 9
        val h = 3
        val luma = ByteArray(w * h) { 100.toByte() }
        val p = FossSmileEstimator.probabilityFromMouthLuma(luma, w, h)
        assertNotNull(p)
        assertEquals(0.42f, p!!, 0.02f)
    }
}
