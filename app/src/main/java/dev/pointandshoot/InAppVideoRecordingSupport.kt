package dev.pointandshoot

import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.util.Size
import kotlin.math.roundToInt

/** Helpers for in-app [MediaRecorder] sizing / labeling (Milestone 11 Sprint 11.4). */
object InAppVideoRecordingSupport {
    private val Fallback720p = Size(1280, 720)

    /** Largest-first menu order (common presets land near the top after stable sort). */
    fun sortedMediaRecorderSizes(map: StreamConfigurationMap?): List<Size> {
        val raw = map?.getOutputSizes(MediaRecorder::class.java) ?: return emptyList()
        return raw.distinctBy { it.width * 100_000L + it.height }
            .sortedWith(compareByDescending<Size> { it.height }.thenByDescending { it.width })
    }

    /**
     * Picks an encoder output size: prefers persisted **[prefWidth]×[prefHeight]** when valid,
     * else prefers **720p** → **1080p** → largest area (matches legacy behavior).
     */
    fun pickOutputSize(map: StreamConfigurationMap?, prefWidth: Int, prefHeight: Int): Size {
        val sizes = map?.getOutputSizes(MediaRecorder::class.java)?.distinctBy { it.width * 100_000L + it.height }
        if (sizes.isNullOrEmpty()) return Fallback720p
        if (prefWidth > 0 && prefHeight > 0) {
            sizes.firstOrNull { it.width == prefWidth && it.height == prefHeight }?.let { return it }
        }
        sizes.firstOrNull { it.width == 1280 && it.height == 720 }?.let { return it }
        sizes.firstOrNull { it.width == 1920 && it.height == 1080 }?.let { return it }
        return sizes.maxByOrNull { it.width.toLong() * it.height } ?: Fallback720p
    }

    fun shortLabel(size: Size): String = shortLabelForDims(size.width, size.height)

    fun shortLabelForDims(width: Int, height: Int): String =
        when {
            width == 3840 && height == 2160 -> "4K"
            height == 1080 && width >= 1920 -> "1080p"
            height == 720 && width >= 1280 -> "720p"
            height == 480 -> "480p"
            else -> "${width}×${height}"
        }

    fun bitrateForSize(width: Int, height: Int, referenceBitrate: Int, referencePixels: Int = 1920 * 1080): Int {
        val px = width.toLong() * height.toLong()
        val ref = referencePixels.coerceAtLeast(1).toLong()
        return (referenceBitrate.toDouble() * px / ref.toDouble()).roundToInt().coerceIn(2_000_000, 50_000_000)
    }

    /**
     * Check if camera supports high-speed video recording (HFR).
     * Returns true if [StreamConfigurationMap.getHighSpeedVideoSizes] returns non-empty array.
     */
    fun supportsHighSpeedVideoRecording(map: StreamConfigurationMap?): Boolean {
        if (map == null) return false
        val highSpeedSizes = map.highSpeedVideoSizes
        return highSpeedSizes != null && highSpeedSizes.isNotEmpty()
    }

    /**
     * Pick best high-speed video output size for target FPS.
     * Prefers largest size that supports the requested FPS range.
     * Returns null if no high-speed configuration available.
     */
    fun pickHighSpeedOutputSize(map: StreamConfigurationMap?, desiredFps: Int): Size? {
        if (map == null) return null
        val highSpeedSizes = map.highSpeedVideoSizes
        if (highSpeedSizes == null || highSpeedSizes.isEmpty()) return null

        // Collect all size + maxFps pairs
        val candidates = ArrayList<Pair<Size, Int>>()
        for (size in highSpeedSizes) {
            val fpsRanges = map.getHighSpeedVideoFpsRangesFor(size)
            if (fpsRanges != null && fpsRanges.isNotEmpty()) {
                var maxFps = 0
                for (range in fpsRanges) {
                    if (range.upper > maxFps) maxFps = range.upper
                }
                candidates.add(Pair(size, maxFps))
            }
        }
        if (candidates.isEmpty()) return null

        // Find best candidate supporting desiredFps
        var bestSize: Size? = null
        var bestPixels = 0
        for ((size, maxFps) in candidates) {
            if (maxFps >= desiredFps) {
                val pixels = size.width * size.height
                if (pixels > bestPixels) {
                    bestPixels = pixels
                    bestSize = size
                }
            }
        }
        if (bestSize != null) return bestSize

        // Fallback: largest size overall
        bestPixels = 0
        for ((size, _) in candidates) {
            val pixels = size.width * size.height
            if (pixels > bestPixels) {
                bestPixels = pixels
                bestSize = size
            }
        }
        return bestSize
    }

    /**
     * Get available high-speed FPS options (for UI menu).
     * Returns sorted unique upper FPS values from all high-speed ranges.
     */
    fun availableHighSpeedFpsOptions(map: StreamConfigurationMap?): List<Int> {
        if (map == null) return emptyList()
        val highSpeedSizes = map.highSpeedVideoSizes
        if (highSpeedSizes == null || highSpeedSizes.isEmpty()) return emptyList()

        val allFps = HashSet<Int>()
        for (size in highSpeedSizes) {
            val ranges = map.getHighSpeedVideoFpsRangesFor(size)
            if (ranges != null) {
                for (range in ranges) {
                    allFps.add(range.upper)
                }
            }
        }
        return allFps.sortedDescending()
    }
}
