package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StillRgbLutTest {

    @Test
    fun `applyToRgb888InPlace is no-op for identity LUT at black and white`() {
        // Endpoints round-trip exactly through sRGB E/OETF + identity trilinear.
        val buf =
            byteArrayOf(
                0, 0, 0,
                -1, -1, -1, // 255
            )
        val copy = buf.copyOf()
        StillRgbLut.applyToRgb888InPlace(buf, 2, 1, Lut3D.identity(17))
        assertArrayEquals(copy, buf)
    }

    @Test
    fun `applyToRgb888InPlace accepts minimal buffer`() {
        val lut = Lut3D.identity(17)
        val buf = ByteArray(3) { it.toByte() }
        StillRgbLut.applyToRgb888InPlace(buf, 1, 1, lut)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `applyToRgb888InPlace rejects undersized buffer`() {
        StillRgbLut.applyToRgb888InPlace(ByteArray(2), 1, 1, Lut3D.identity(17))
    }
}
