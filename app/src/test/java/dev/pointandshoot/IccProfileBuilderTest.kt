package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class IccProfileBuilderTest {
    @Test
    fun icc_header_magic_acsp() {
        val bytes = IccProfileBuilder.buildSrgbDisplayProfile()
        assertEquals('a'.code.toByte(), bytes[0])
        assertEquals('c'.code.toByte(), bytes[1])
        assertEquals('s'.code.toByte(), bytes[2])
        assertEquals('p'.code.toByte(), bytes[3])
    }
}
