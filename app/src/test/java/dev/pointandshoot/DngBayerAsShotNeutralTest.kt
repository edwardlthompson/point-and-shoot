package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DngBayerAsShotNeutralTest {

    @Test
    fun `asnFromChannelMeans matches structural_verify tele rawpy means`() {
        val meanR = 67.886414f
        val meanG = 158.27716f
        val meanB = 69.74891f
        val asn = DngBayerAsShotNeutral.asnFromChannelMeans(meanR, meanG, meanB)
        val wbR = 1f / asn[0]
        val wbB = 1f / asn[2]
        val rawWbR = meanG / meanR
        val rawWbB = meanG / meanB
        assertEquals(rawWbR, wbR, 0.02f)
        assertEquals(rawWbB, wbB, 0.02f)
        assertTrue(asn.max() <= 1.0001f)
    }
}
