package dev.pointandshoot

import androidx.camera.extensions.ExtensionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXExtensionProbeTest {

    @Test
    fun extensionMatrix_summary_emptyWhenNoExtensions() {
        val matrix = CameraXExtensionProbe.ExtensionMatrix(emptyMap())
        assertFalse(matrix.hasAny())
        assertTrue(matrix.summary().contains("noExtensions=true"))
    }

    @Test
    fun extensionMatrix_isAvailable_perCamera() {
        val matrix =
            CameraXExtensionProbe.ExtensionMatrix(
                mapOf("0" to listOf(ExtensionMode.NIGHT, ExtensionMode.BOKEH)),
            )
        assertTrue(matrix.isAvailable("0", ExtensionMode.NIGHT))
        assertFalse(matrix.isAvailable("1", ExtensionMode.NIGHT))
        assertTrue(matrix.summary().contains("cam0="))
    }
}
