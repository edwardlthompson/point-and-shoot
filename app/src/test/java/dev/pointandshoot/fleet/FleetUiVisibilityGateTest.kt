package dev.pointandshoot.fleet

import dev.pointandshoot.HardwareCaps
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetUiVisibilityGateTest {

    private val fixtureMatrix: JSONObject by lazy {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        CameraCapabilityCatalogBuilder.attachTo(JSONObject(json))
    }

    private fun caps(hasFaceDetectFull: Boolean = false): HardwareCaps =
        HardwareCaps(
            hasRawCapability = false,
            has12BitDepth = false,
            has120FpsHfr = false,
            hasFaceDetectFull = hasFaceDetectFull,
            hasPreviewHistogram = false,
            aeCompensationStepsAvailable = 0,
            hasMacroMode = false,
            has10BitHdrPipeline = false,
        )

    private fun ctx(
        matrix: JSONObject? = fixtureMatrix,
        caps: HardwareCaps = caps(hasFaceDetectFull = true),
        rootGranted: Boolean = false,
        activeCameraId: String? = "2",
    ): FleetUiVisibilityGate.VisibilityContext =
        FleetUiVisibilityGate.VisibilityContext(
            matrix = matrix,
            caps = caps,
            rootGranted = rootGranted,
            activeCameraId = activeCameraId,
        )

    @Test
    fun eyeAf_hidden_whenFaceGateNotAdvertisedOnActiveCamera() {
        val tier = FleetUiVisibilityGate.tier("face.eye_af", ctx(activeCameraId = "3"))
        assertEquals(FleetUiVisibilityGate.Tier.Hidden, tier)
        assertFalse(FleetUiVisibilityGate.visible("face.eye_af", ctx(activeCameraId = "3")))
    }

    @Test
    fun eyeAf_visible_whenFaceGateAdvertisedOnActiveCamera() {
        val tier = FleetUiVisibilityGate.tier("face.eye_af", ctx(activeCameraId = "2"))
        assertEquals(FleetUiVisibilityGate.Tier.Visible, tier)
        assertTrue(FleetUiVisibilityGate.visible("face.eye_af", ctx(activeCameraId = "2")))
    }

    @Test
    fun eyeAf_hidden_whenNoMatrixAndNoFaceDetectFull() {
        val tier =
            FleetUiVisibilityGate.tier(
                "face.eye_af",
                ctx(matrix = null, caps = caps(hasFaceDetectFull = false), activeCameraId = "2"),
            )
        assertEquals(FleetUiVisibilityGate.Tier.Hidden, tier)
    }

    @Test
    fun fpsTier_rootOnly_whenRequiresRootAndSuNotGranted() {
        assertEquals(
            FleetUiVisibilityGate.Tier.RootOnly,
            FleetUiVisibilityGate.fpsTier(requiresRoot = true, ctx(rootGranted = false)),
        )
    }

    @Test
    fun fpsTier_visible_whenRequiresRootAndSuGranted() {
        assertEquals(
            FleetUiVisibilityGate.Tier.Visible,
            FleetUiVisibilityGate.fpsTier(requiresRoot = true, ctx(rootGranted = true)),
        )
    }

    @Test
    fun rootHfrUnlock_rootOnly_whenSuNotGranted() {
        val tier = FleetUiVisibilityGate.tier("root.hfr_unlock", ctx(rootGranted = false))
        assertEquals(FleetUiVisibilityGate.Tier.RootOnly, tier)
        assertTrue(FleetUiVisibilityGate.rootOnly("root.hfr_unlock", ctx(rootGranted = false)))
    }

    @Test
    fun rootHfrUnlock_visible_whenSuGrantedAndDeviceSupportsHfr() {
        val tier = FleetUiVisibilityGate.tier("root.hfr_unlock", ctx(rootGranted = true))
        assertEquals(FleetUiVisibilityGate.Tier.Visible, tier)
    }

    @Test
    fun unknownCatalogId_hiddenByDefault() {
        val tier = FleetUiVisibilityGate.tier("unknown.feature.id", ctx())
        assertEquals(FleetUiVisibilityGate.Tier.Hidden, tier)
        assertFalse(FleetUiVisibilityGate.visible("unknown.feature.id", ctx()))
    }
}
