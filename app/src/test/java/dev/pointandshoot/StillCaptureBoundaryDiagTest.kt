package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StillCaptureBoundaryDiagTest {

    @Test
    fun summarizeRegions_empty() {
        assertEquals("-", StillCaptureBoundaryDiag.summarizeRegions(null))
        assertEquals("-", StillCaptureBoundaryDiag.summarizeRegions(emptyArray()))
    }

    @Test
    fun hypothesisHint_ispDelta() {
        val p = baseSnap().copy(edgeMode = CaptureResult.EDGE_MODE_HIGH_QUALITY)
        val s = baseSnap().copy(edgeMode = CaptureResult.EDGE_MODE_FAST)
        assertTrue(StillCaptureBoundaryDiag.hypothesisHint(p, s).contains("isp_mode_delta"))
    }

    @Test
    fun hypothesisHint_eisDelta() {
        val p = baseSnap().copy(videoStabilizationMode = CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        val s = baseSnap().copy(videoStabilizationMode = CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        assertTrue(StillCaptureBoundaryDiag.hypothesisHint(p, s).contains("eis_delta"))
    }

    @Test
    fun hypothesisHint_focusDistanceJump() {
        val p = baseSnap().copy(lensFocusDistance = 2.0f)
        val s = baseSnap().copy(lensFocusDistance = 2.1f)
        assertTrue(StillCaptureBoundaryDiag.hypothesisHint(p, s).contains("focus_distance_jump"))
    }

    @Test
    fun hypothesisHint_stableSubset() {
        val p = baseSnap()
        val s = baseSnap()
        assertEquals("stable_subset", StillCaptureBoundaryDiag.hypothesisHint(p, s))
    }

    @Test
    fun hypothesisHint_noPreview() {
        val s = baseSnap()
        assertEquals("no_preview_snapshot", StillCaptureBoundaryDiag.hypothesisHint(null, s))
    }

    private fun baseSnap() =
        StillCaptureBoundaryDiag.Snapshot(
            wallElapsedMs = 0L,
            frameNumber = 1L,
            afMode = CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            afState = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
            afRegions = "-",
            aeState = CaptureResult.CONTROL_AE_STATE_CONVERGED,
            aeLocked = false,
            lensState = CaptureResult.LENS_STATE_STATIONARY,
            lensFocusDistance = 1.0f,
            edgeMode = CaptureResult.EDGE_MODE_FAST,
            nrMode = CaptureResult.NOISE_REDUCTION_MODE_FAST,
            tonemapMode = CaptureResult.TONEMAP_MODE_FAST,
            colorCorrectionMode = CaptureResult.COLOR_CORRECTION_MODE_FAST,
            videoStabilizationMode = CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            scalerCrop = "0,0-100,100",
            sensitivity = 100,
            exposureNs = 10_000_000L,
            statisticsFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF,
        )
}
