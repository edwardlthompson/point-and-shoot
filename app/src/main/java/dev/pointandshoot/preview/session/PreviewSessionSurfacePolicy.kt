package dev.pointandshoot.preview.session

import dev.pointandshoot.ImagingProfile
import dev.pointandshoot.VideoEncodeLane
import dev.pointandshoot.VideoRecordingController

/** Inputs for [PreviewSessionSurfacePolicy.wantsRawVideoLane]. */
data class RawVideoLaneInput(
    val adbForceRawVideoLane: Boolean,
    val videoEncodeLane: VideoEncodeLane,
    val fleetSupportsRawVideo: Boolean,
)

/** Inputs for [PreviewSessionSurfacePolicy.wantsRawStillSurfacesInSession]. */
data class RawStillSurfacesInput(
    val dualVideoActive: Boolean,
    val wantsRawVideoLane: Boolean,
    val videoRecorderPresent: Boolean,
    val imagingProfile: ImagingProfile,
    val desiredFps: Int,
    val adbPendingRawStillAutomationCount: Int,
    val adbScriptedStillAutomationActive: Boolean,
    val videoPrimarySession: Boolean,
    val inAppVideoRecordingArmed: Boolean,
)

/**
 * Pure session surface policy extracted from [dev.pointandshoot.PreviewEngineScreen]
 * [PreviewController] — decides when RAW still / RAW video lanes attach surfaces in
 * [createSession] without pulling the full orchestration path.
 */
object PreviewSessionSurfacePolicy {
    fun wantsRawVideoLane(input: RawVideoLaneInput): Boolean {
        if (input.adbForceRawVideoLane) return true
        if (input.videoEncodeLane != VideoEncodeLane.Raw) return false
        return input.fleetSupportsRawVideo
    }

    /** True when [createSession] would attach RAW (+ JPEG companion) still surfaces. */
    fun wantsRawStillSurfacesInSession(input: RawStillSurfacesInput): Boolean {
        if (input.dualVideoActive) return false
        if (input.wantsRawVideoLane) return true
        if (input.videoRecorderPresent) return false
        if (input.imagingProfile is ImagingProfile.JpegOnly) return false
        if (input.desiredFps >= VideoRecordingController.HFR_THRESHOLD_FPS) return false
        val rawAutomationPending =
            input.adbPendingRawStillAutomationCount > 0 || input.adbScriptedStillAutomationActive
        if (rawAutomationPending) return true
        if (input.videoPrimarySession && !input.inAppVideoRecordingArmed) return false
        return true
    }
}
