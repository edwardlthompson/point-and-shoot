package dev.pointandshoot

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneVendorHintProbeTest {

    @Test
    fun sceneHintMatrix_summaryForReadout_nullWhenEmpty() {
        assertNull(SceneVendorHintProbe.SceneHintMatrix(emptyList()).summaryForReadout())
    }

    @Test
    fun sceneHintMatrix_summaryForReadout_joinsVendorKeys() {
        val matrix =
            SceneVendorHintProbe.SceneHintMatrix(
                listOf(
                    SceneVendorHintProbe.CameraSceneHints(
                        cameraId = "3",
                        requestKeys = listOf("com.vendor.media_quality.mode"),
                        resultKeys = listOf("com.vendor.eva.scene_hint"),
                    ),
                ),
            )
        val summary = matrix.summaryForReadout()
        assertNotNull(summary)
        assertTrue(summary!!.isNotBlank())
        // summary uses short token after last '.' (max 24 chars per key)
        assertTrue(summary.contains("mode") || summary.contains("scene_hint") || summary.contains("media"))
    }
}
