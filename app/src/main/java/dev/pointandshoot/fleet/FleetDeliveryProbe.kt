package dev.pointandshoot.fleet

import dev.pointandshoot.UltraHd60RecordSupport
import kotlin.math.abs

/**
 * Full-mode delivery verification — requested vs actual resolution and fps (M18.6).
 */
object FleetDeliveryProbe {
    enum class MismatchReason {
        FPS_LOW,
        FPS_HIGH,
        RESOLUTION_MISMATCH,
        CODEC_MISMATCH,
        PREVIEW_ENCODER_GAP,
    }

    data class Requested(
        val width: Int,
        val height: Int,
        val fps: Int,
    )

    data class Actual(
        val width: Int,
        val height: Int,
        val fps: Double,
    )

    fun classify(
        requested: Requested,
        actual: Actual,
        isUhd60: Boolean = false,
    ): FleetParitySweep.DeliveryProbe {
        val fpsOk = fpsMatches(requested.fps, actual.fps, isUhd60)
        val resOk =
            requested.width == actual.width &&
                requested.height == actual.height
        val matchOk = fpsOk && resOk
        val reason =
            when {
                matchOk -> null
                !resOk -> MismatchReason.RESOLUTION_MISMATCH.name.lowercase()
                !fpsOk && actual.fps < requested.fps -> MismatchReason.FPS_LOW.name.lowercase()
                !fpsOk -> MismatchReason.FPS_HIGH.name.lowercase()
                else -> MismatchReason.PREVIEW_ENCODER_GAP.name.lowercase()
            }
        return FleetParitySweep.DeliveryProbe(
            requestedWidth = requested.width,
            requestedHeight = requested.height,
            requestedFps = requested.fps,
            actualWidth = actual.width,
            actualHeight = actual.height,
            actualFps = actual.fps,
            matchOk = matchOk,
            mismatchReason = reason,
        )
    }

    /** Tolerances aligned with `pns_mediacodec_hfr_verify.ps1`. */
    fun fpsMatches(requestedFps: Int, actualFps: Double, isUhd60: Boolean = false): Boolean {
        if (actualFps <= 0.0) return false
        if (isUhd60 || requestedFps == UltraHd60RecordSupport.TARGET_FPS) {
            return actualFps >= UltraHd60RecordSupport.MIN_MEASURED_UNIQUE_FPS
        }
        if (requestedFps >= 120) {
            val minFps = maxOf(45.0, requestedFps * 0.75)
            return actualFps >= minFps
        }
        return abs(actualFps - requestedFps) <= 3.0
    }
}
