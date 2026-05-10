package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class StillCaptureMetadataTest {

    @Test
    fun `exposure fraction for sub-second`() {
        assertEquals("1/125", StillCaptureMetadata.exposureTimeExifString(8_000_000L))
    }

    @Test
    fun `exposure decimal for multi-second`() {
        assertEquals("2", StillCaptureMetadata.exposureTimeExifString(2_000_000_000L))
    }
}
