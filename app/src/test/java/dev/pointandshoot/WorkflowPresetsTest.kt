package dev.pointandshoot

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowPresetsTest {

    @Test
    fun builtIn_containsStreetPortraitVideoLog() {
        val ids = WorkflowPresets.builtIn.map { it.id }.toSet()
        assertEquals(setOf("street", "portrait", "video_log"), ids)
    }

    @Test
    fun workflowPreset_jsonRoundTrip() {
        val original =
            WorkflowPreset(
                id = "custom_test",
                label = "Test",
                commandDialMode = CommandDialMode.H,
                imagingProfileId = ImagingProfile.StandardPro.id,
                primaryPhoto = true,
                fps = 30,
            )
        val parsed = WorkflowPreset.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(original.id, parsed!!.id)
        assertEquals(original.commandDialMode, parsed.commandDialMode)
        assertEquals(original.fps, parsed.fps)
    }

    @Test
    fun workflowPreset_fromJson_unknownDialFallsBackToAuto() {
        val bad =
            JSONObject()
                .put("id", "x")
                .put("label", "x")
                .put("commandDialMode", "NOT_A_MODE")
                .put("imagingProfileId", ImagingProfile.JpegOnly.id)
        val parsed = WorkflowPreset.fromJson(bad)
        assertNotNull(parsed)
        assertEquals(CommandDialMode.Auto, parsed!!.commandDialMode)
    }
}
