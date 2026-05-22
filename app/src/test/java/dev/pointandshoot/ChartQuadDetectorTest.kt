package dev.pointandshoot

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartQuadDetectorTest {

    @Test
    fun detectFromArgb_findsWhiteQuadOnGrayField() {
        val w = 320
        val h = 240
        val pixels = IntArray(w * h) { 0xFF606060.toInt() }
        val left = 40
        val top = 30
        val right = 280
        val bottom = 210
        for (y in top..bottom) {
            for (x in left..right) {
                if (x == left || x == right || y == top || y == bottom) {
                    pixels[y * w + x] = 0xFFF0F0F0.toInt()
                } else {
                    pixels[y * w + x] = 0xFFE8E8E8.toInt()
                }
            }
        }
        val result = ChartQuadDetector.detectFromArgb(pixels, w, h)
        assertNotNull(result)
        val c = result!!.corners
        assertTrue(c.tl.x < c.tr.x)
        assertTrue(c.tl.y < c.br.y)
        assertTrue(result.confidence >= 0.35f)
        assertTrue(c.tl.x in 20f..60f)
        assertTrue(c.br.x in 260f..300f)
    }
}
