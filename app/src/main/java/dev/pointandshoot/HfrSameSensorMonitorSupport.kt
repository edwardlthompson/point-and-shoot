package dev.pointandshoot

import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Size

/**
 * Low-res finder on the **same** camera id as HFR encode — a second [android.view.Surface] in the
 * constrained high-speed session (not a second [android.hardware.camera2.CameraDevice]).
 */
object HfrSameSensorMonitorSupport {
    const val TAG = "PNS.HfrSameSensorMonitor"

    /** Minimum monitor area (~640×360) so the finder stays usable. */
    private const val MIN_MONITOR_PIXELS = 640 * 360

    /**
     * Smallest [StreamConfigurationMap.getHighSpeedVideoSizes] entry that supports [desiredFps],
     * preferring tiers no larger than [recordSize].
     */
    fun pickMonitorSize(
        map: StreamConfigurationMap?,
        recordSize: Size,
        desiredFps: Int,
        preferSmallest: Boolean = false,
    ): Size? {
        if (map == null) return null
        val hsSizes = map.highSpeedVideoSizes?.toList().orEmpty()
        if (hsSizes.isEmpty()) return null

        fun supportsFps(size: Size): Boolean {
            val ranges = map.getHighSpeedVideoFpsRangesFor(size) ?: return false
            return ranges.any { it.lower <= desiredFps && it.upper >= desiredFps } ||
                ranges.any { it.upper >= desiredFps }
        }

        val candidates = hsSizes.filter { supportsFps(it) }
        if (candidates.isEmpty()) {
            Log.w(TAG, "no HS size supports monitor fps=$desiredFps")
            return null
        }

        val capped =
            candidates.filter { it.width <= recordSize.width && it.height <= recordSize.height }
                .ifEmpty { candidates }
        val picked =
            if (preferSmallest) {
                capped
                    .filter { it.width * it.height >= MIN_MONITOR_PIXELS }
                    .minByOrNull { it.width.toLong() * it.height }
                    ?: capped.minByOrNull { it.width.toLong() * it.height }
            } else {
                val prefer720 = Size(1280, 720)
                capped.firstOrNull { it.width == prefer720.width && it.height == prefer720.height }
                    ?: capped
                        .filter { it.width * it.height >= MIN_MONITOR_PIXELS }
                        .minByOrNull { it.width.toLong() * it.height }
                    ?: capped.minByOrNull { it.width.toLong() * it.height }
            }
        Log.i(
            TAG,
            "pickMonitorSize record=${recordSize.width}x${recordSize.height} fps=$desiredFps " +
                "preferSmallest=$preferSmallest monitor=${picked?.width ?: 0}x${picked?.height ?: 0}",
        )
        return picked
    }
}
