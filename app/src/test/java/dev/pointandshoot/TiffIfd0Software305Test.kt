package dev.pointandshoot

import org.junit.Assert.assertSame
import org.junit.Test

class TiffIfd0Software305Test {

    @Test
    fun `non-tiff buffer returned unchanged by reference`() {
        val b = ByteArray(64)
        val out = TiffIfd0Software305.patchSoftwarePreservingLength(b, "Point & Shoot")
        assertSame(b, out)
    }
}
