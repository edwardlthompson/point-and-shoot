package dev.pointandshoot

import android.hardware.camera2.CameraExtensionCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraExtensionSupportTest {

    @Test
    fun formatExtensionLabels_joinsKnownIds() {
        val ids =
            intArrayOf(
                CameraExtensionCharacteristics.EXTENSION_HDR,
                CameraExtensionCharacteristics.EXTENSION_NIGHT,
            )
        val s = CameraExtensionSupport.formatExtensionLabels(ids)
        assertTrue(s.contains("HDR"))
        assertTrue(s.contains("NIGHT"))
    }

    @Test
    fun formatExtensionLabels_emptyIsNone() {
        assertEquals("(none)", CameraExtensionSupport.formatExtensionLabels(intArrayOf()))
    }
}
