package dev.pointandshoot

import android.graphics.SurfaceTexture
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.util.Range
import android.util.Size
import kotlin.math.abs
import kotlin.math.roundToInt

/** Helpers for in-app [MediaRecorder] sizing / labeling (Milestone 11 Sprint 11.4). */
object InAppVideoRecordingSupport {
    private val Fallback720p = Size(1280, 720)
    const val EIGHT_K_WIDTH = 7680
    const val EIGHT_K_HEIGHT = 4320

    fun isEightKSize(width: Int, height: Int): Boolean =
        width >= EIGHT_K_WIDTH || height >= EIGHT_K_HEIGHT

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

    /**
     * HS fps values advertised for an exact encode size (e.g. 1280×720 @ 480).
     * Used with encoder performance-points so the format picker is not limited to HEVC probe tiers.
     */
    fun highSpeedFpsForEncodeSize(
        map: StreamConfigurationMap?,
        width: Int,
        height: Int,
    ): List<Int> {
        if (map == null || width <= 0 || height <= 0) return emptyList()
        val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        val size = sizes.firstOrNull { it.width == width && it.height == height } ?: return emptyList()
        val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(size) }.getOrNull().orEmpty()
        return ranges.map { it.upper }.distinct().sorted()
    }

    /** True when [width]×[height] is listed at exactly [fps] in the camera constrained HS table. */
    fun hasExactHighSpeedFps(
        map: StreamConfigurationMap?,
        width: Int,
        height: Int,
        fps: Int,
    ): Boolean = fps in highSpeedFpsForEncodeSize(map, width, height)

    /**
     * True when constrained HS can run at [fps] for a 4K encode tier (capture may be 1080p/720p).
     */
    fun supportsHighSpeedCaptureFor4KEncode(
        map: StreamConfigurationMap?,
        fps: Int,
        preferredEncodeSize: Size = Size(3840, 2160),
    ): Boolean = pickHighSpeedVideoTarget(map, fps, preferredEncodeSize) != null

    /** [MediaRecorder] path: camera must advertise this exact output size. */
    fun supportsMediaRecorderOutputSize(
        map: StreamConfigurationMap?,
        width: Int,
        height: Int,
    ): Boolean {
        if (map == null || width <= 0 || height <= 0) return false
        val sizes =
            runCatching { map.getOutputSizes(MediaRecorder::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        return sizes.any { it.width == width && it.height == height }
    }

    fun supportsSurfaceTextureOutputSize(
        map: StreamConfigurationMap?,
        width: Int,
        height: Int,
    ): Boolean {
        if (map == null || width <= 0 || height <= 0) return false
        val sizes =
            runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        return sizes.any { it.width == width && it.height == height }
    }

    /** HAL must expose 8K on MR and/or preview [SurfaceTexture] outputs (Sprint **15.4**). */
    fun supportsEightKCameraOutputs(map: StreamConfigurationMap?): Boolean =
        supportsMediaRecorderOutputSize(map, EIGHT_K_WIDTH, EIGHT_K_HEIGHT) ||
            supportsSurfaceTextureOutputSize(map, EIGHT_K_WIDTH, EIGHT_K_HEIGHT)

    /**
     * Align preview buffer size with record size when both are in the HAL table so
     * REGULAR + MediaCodec encoder surfaces can configure (Sprint **15.4**).
     */
    fun pickPreviewSizeAlignedToRecord(
        map: StreamConfigurationMap?,
        recordSize: Size,
    ): Size? {
        if (map == null || recordSize.width <= 0 || recordSize.height <= 0) return null
        val w = recordSize.width
        val h = recordSize.height
        if (supportsSurfaceTextureOutputSize(map, w, h)) return recordSize
        val aspect = w.toFloat() / h.toFloat()
        val stSizes =
            runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        return stSizes
            .filter { s ->
                val a = s.width.toFloat() / s.height.toFloat()
                abs(a - aspect) < 0.02f && s.width <= w && s.height <= h
            }
            .maxByOrNull { it.width.toLong() * it.height }
    }

    /**
     * Pick constrained high-speed preview + record size for [desiredFps] (Sprint **13V.16**).
     *
     * When [preferredEncodeSize] is **4K** (e.g. 3840×2160), prefer that tier if the HAL lists it
     * as a high-speed size before falling back to 1080p / 720p defaults.
     */
    fun pickHighSpeedVideoTarget(
        map: StreamConfigurationMap?,
        desiredFps: Int,
        preferredEncodeSize: Size? = null,
        /** After HFR [onConfigureFailed], retry with sub-4K HS before 4K HS. */
        preferSub4kCapture: Boolean = false,
    ): Pair<Size, Range<Int>>? {
        if (map == null) return null
        val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null

        fun matchesSize(want: Size): Boolean =
            sizes.any { it.width == want.width && it.height == want.height }

        fun fpsRangeFor(size: Size): Range<Int>? {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(size) }.getOrNull() ?: return null
            return ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }
                ?: ranges.firstOrNull { it.lower == it.upper && it.upper >= desiredFps }
                ?: ranges.firstOrNull { it.upper == desiredFps }
        }

        val pref = preferredEncodeSize?.takeIf { it.width > 0 && it.height > 0 }
        if (pref != null && pref.width >= 3840) {
            val atFps =
                sizes.mapNotNull { s -> fpsRangeFor(s)?.let { range -> s to range } }
            if (!preferSub4kCapture && matchesSize(pref)) {
                fpsRangeFor(pref)?.let { return pref to it }
            }
            atFps
                .filter { it.first.width < 3840 }
                .maxByOrNull { it.first.width.toLong() * it.first.height }
                ?.let { return it }
            if (matchesSize(pref)) {
                fpsRangeFor(pref)?.let { return pref to it }
            }
            atFps
                .filter { it.first.width >= 3840 && it.first.height >= 2160 }
                .maxByOrNull { it.first.width.toLong() * it.first.height }
                ?.let { return it }
            atFps.maxByOrNull { it.first.width.toLong() * it.first.height }?.let { return it }
        }
        if (pref != null && matchesSize(pref)) {
            fpsRangeFor(pref)?.let { return pref to it }
        }

        val preferredOrder = listOf(Size(1920, 1080), Size(1280, 720))
        val candidateSizes =
            (preferredOrder.filter { matchesSize(it) } + sizes).distinctBy { "${it.width}x${it.height}" }

        for (s in candidateSizes) {
            if (matchesSize(s)) {
                fpsRangeFor(s)?.let { range -> return s to range }
            }
        }
        return null
    }

    /** HS AE for interleaved preview+record — same [recordFps] fixed range as the file. */
    fun pickInterleavedHighSpeedVideoTarget(
        map: StreamConfigurationMap?,
        recordFps: Int,
        preferredEncodeSize: Size? = null,
    ): Pair<Size, Range<Int>>? =
        pickHighSpeedVideoTarget(map, recordFps, preferredEncodeSize, preferSub4kCapture = false)
}
