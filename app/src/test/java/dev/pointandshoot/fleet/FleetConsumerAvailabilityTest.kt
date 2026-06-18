package dev.pointandshoot.fleet

import dev.pointandshoot.HardwareCaps
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetConsumerAvailabilityTest {

    private val fixtureMatrix: JSONObject by lazy {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        JSONObject(json)
    }

    private fun caps(): HardwareCaps =
        HardwareCaps(
            hasRawCapability = false,
            has12BitDepth = false,
            has120FpsHfr = false,
            hasFaceDetectFull = false,
            hasPreviewHistogram = false,
            aeCompensationStepsAvailable = 0,
            hasMacroMode = false,
            has10BitHdrPipeline = false,
        )

    private fun ctx(cameraId: String): FleetUiVisibilityGate.VisibilityContext =
        FleetUiVisibilityGate.VisibilityContext(
            matrix = fixtureMatrix,
            caps = caps(),
            rootGranted = false,
            activeCameraId = cameraId,
        )

    @Test
    fun hfr_notSelectable_whenSessionOkFalse() {
        assertFalse(FleetConsumerAvailability.consumerSelectable("video.hfr", ctx("2")))
    }

    @Test
    fun hfr_selectable_whenSessionOkTrue() {
        assertTrue(FleetConsumerAvailability.consumerSelectable("video.hfr", ctx("3")))
    }

    @Test
    fun raw_selectable_whenSessionOkTrue() {
        assertTrue(FleetConsumerAvailability.consumerSelectable("raw.dng", ctx("2")))
    }

    @Test
    fun visibilityGate_hidesHfrOnCamera2() {
        assertFalse(FleetUiVisibilityGate.visible("video.hfr", ctx("2")))
    }
}
