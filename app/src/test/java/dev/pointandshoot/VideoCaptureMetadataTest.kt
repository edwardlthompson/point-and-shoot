package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCaptureMetadataTest {
    @Test
    fun parseDescription_extractsFpsAndCodec() {
        val parsed =
            VideoCaptureMetadata.parseDescription(
                "Point & Shoot · H.264 · 120fps",
            )
        assertEquals(120, parsed.captureFps)
        assertEquals("H.264", parsed.codecLabel)
    }
}
