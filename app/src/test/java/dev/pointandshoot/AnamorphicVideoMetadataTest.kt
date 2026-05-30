package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnamorphicVideoMetadataTest {
    @Test
    fun fromSqueezeFactor_buildsSar() {
        val sar = AnamorphicVideoMetadata.fromSqueezeFactor(2.0)
        assertEquals(2000, sar.horizontal)
        assertEquals(1000, sar.vertical)
        assertEquals(2.0, sar.squeezeFactor, 0.001)
    }

    @Test
    fun paspPayload_isEightBytes() {
        val sar = AnamorphicVideoMetadata.fromSqueezeFactor(1.5)
        val payload = AnamorphicVideoMetadata.paspPayload(sar)
        assertNotNull(payload)
        assertEquals(8, payload!!.size)
    }
}
