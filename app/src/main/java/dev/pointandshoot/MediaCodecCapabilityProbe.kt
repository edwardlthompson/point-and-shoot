package dev.pointandshoot

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime probe of the device's MediaCodec HEVC encoder capabilities.
 *
 * Queries [MediaCodecList.ALL_CODECS] at first use and builds a [CapabilityMatrix] covering:
 * - Every supported resolution × fps combination that is a **performance-point** guarantee
 * - Profile / level support (Main / Main10 / Main10HDR10 / Main10HDR10Plus)
 * - 10-bit (YUVP010) color format availability
 *
 * Results are logged to `PNS.VideoCapProbe` for ADB gate scripts.
 *
 * Thread-safety: [probe] is safe to call from any thread; heavy work runs on [Dispatchers.IO].
 * The result is cached after the first call.
 */
object MediaCodecCapabilityProbe {

    private const val TAG = "PNS.VideoCapProbe"
    private const val MIME_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC
    private const val MIME_AV1 = MediaFormat.MIMETYPE_VIDEO_AV1

    @Volatile private var cached: CapabilityMatrix? = null

    /**
     * A resolution + fps combination confirmed as a hardware performance-point guarantee.
     */
    data class PerformancePoint(
        val width: Int,
        val height: Int,
        val fps: Int,
        val encoderName: String,
    ) {
        val label: String get() = "${width}x${height}@${fps}fps"
    }

    /**
     * The complete capability matrix for this device's HEVC encoders.
     */
    data class CapabilityMatrix(
        val encoders: List<EncoderInfo>,
        val performancePoints: List<PerformancePoint>,
        val supportsMain10: Boolean,
        val supportsHdr10: Boolean,
        val supportsHdr10Plus: Boolean,
        val supportsYuvP010: Boolean,
        val maxFps1080p: Int,
        val maxFps4k: Int,
        val maxFps8k: Int,
        val supports4k: Boolean,
        val supports8k: Boolean,
        /** Sprint **VF.1** — at least one hardware [MIME_AV1] encoder (e.g. `c2.qti.av1.encoder`). */
        val supportsAv1: Boolean = false,
        val av1EncoderNames: List<String> = emptyList(),
    ) {
        fun summary(): String = buildString {
            append("encoders=${encoders.size} ")
            append("main10=$supportsMain10 hdr10=$supportsHdr10 hdr10plus=$supportsHdr10Plus ")
            append("yuvp010=$supportsYuvP010 av1=$supportsAv1 ")
            append("1080p_max=${maxFps1080p}fps 4k_max=${maxFps4k}fps 8k_max=${maxFps8k}fps ")
            append("perf_points=${performancePoints.size}")
        }
    }

    data class EncoderInfo(
        val name: String,
        val maxWidth: Int,
        val maxHeight: Int,
        val maxFps: Int,
        val profiles: List<Int>,
        val supportsYuvP010: Boolean,
        val performancePoints: List<PerformancePoint>,
    )

    /**
     * Return the cached [CapabilityMatrix], running the probe on [Dispatchers.IO] if needed.
     */
    suspend fun probe(): CapabilityMatrix = cached ?: withContext(Dispatchers.IO) {
        runProbe().also { cached = it }
    }

    /**
     * Synchronous probe — call only from a background thread.
     */
    fun probeSync(): CapabilityMatrix = cached ?: runProbe().also { cached = it }

    @Suppress("LongMethod", "TooGenericExceptionCaught", "MagicNumber")
    private fun runProbe(): CapabilityMatrix {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        val hevcEncoders = list.codecInfos
            .filter { it.isEncoder && !it.isAlias }
            .filter { MIME_HEVC in it.supportedTypes }

        val encoderInfos = mutableListOf<EncoderInfo>()
        val allPerformancePoints = mutableListOf<PerformancePoint>()

        var supportsMain10 = false
        var supportsHdr10 = false
        var supportsHdr10Plus = false
        var supportsYuvP010 = false
        var maxFps1080p = 0
        var maxFps4k = 0
        var maxFps8k = 0

        // Candidate resolution × fps tiers to probe via covers()
        val probeTiers: List<Triple<Int, Int, Int>> = listOf(
            Triple(7680, 4320, 24), Triple(7680, 4320, 30), Triple(7680, 4320, 48),
            Triple(3840, 2160, 30), Triple(3840, 2160, 60), Triple(3840, 2160, 120),
            Triple(1920, 1080, 60), Triple(1920, 1080, 120), Triple(1920, 1080, 240),
            Triple(1920, 1080, 480),
            Triple(1280, 720, 60), Triple(1280, 720, 120),
        )

        for (codec in hevcEncoders) {
            val caps = try {
                codec.getCapabilitiesForType(MIME_HEVC)
            } catch (e: Exception) {
                Log.w(TAG, "getCapabilitiesForType failed for ${codec.name}: ${e.message}")
                continue
            }
            val vidCaps = caps.videoCapabilities ?: continue

            val profiles = caps.profileLevels.map { it.profile }.distinct()
            val hasMain10 = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 in profiles
            val hasHdr10 = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 in profiles
            val hasHdr10Plus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus in profiles
            } else false
            val hasYuvP010 = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010 in caps.colorFormats

            if (hasMain10) supportsMain10 = true
            if (hasHdr10) supportsHdr10 = true
            if (hasHdr10Plus) supportsHdr10Plus = true
            if (hasYuvP010) supportsYuvP010 = true

            val maxW = vidCaps.supportedWidths.upper
            val maxH = vidCaps.supportedHeights.upper

            val encPerfPoints = mutableListOf<PerformancePoint>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val perfPoints = vidCaps.supportedPerformancePoints
                    if (perfPoints != null) {
                        for ((w, h, fps) in probeTiers) {
                            if (w > maxW || h > maxH) continue
                            // covers() returns true if any performance-point covers this resolution+fps
                            val covered = perfPoints.any { pp ->
                                runCatching {
                                    pp.covers(android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint(w, h, fps))
                                }.getOrDefault(false)
                            }
                            if (covered) {
                                val point = PerformancePoint(w, h, fps, codec.name)
                                encPerfPoints.add(point)
                                allPerformancePoints.add(point)
                                when {
                                    w >= 7680 -> if (fps > maxFps8k) maxFps8k = fps
                                    w >= 3840 -> if (fps > maxFps4k) maxFps4k = fps
                                    w >= 1920 -> if (fps > maxFps1080p) maxFps1080p = fps
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "supportedPerformancePoints failed for ${codec.name}: ${e.message}")
                }
            } else {
                // Pre-Q fallback: use getSupportedFrameRatesFor to estimate tier support
                for ((w, h, fps) in probeTiers) {
                    if (w > maxW || h > maxH) continue
                    val fpsRange = runCatching { vidCaps.getSupportedFrameRatesFor(w, h) }.getOrNull()
                    if (fpsRange != null && fps <= fpsRange.upper) {
                        val point = PerformancePoint(w, h, fps, codec.name)
                        encPerfPoints.add(point)
                        allPerformancePoints.add(point)
                    }
                }
            }

            val maxFpsForEncoder = try {
                vidCaps.getSupportedFrameRatesFor(minOf(maxW, 1920), minOf(maxH, 1080)).upper.toInt()
            } catch (e: Exception) { 0 }

            encoderInfos.add(
                EncoderInfo(
                    name = codec.name,
                    maxWidth = maxW,
                    maxHeight = maxH,
                    maxFps = maxFpsForEncoder,
                    profiles = profiles,
                    supportsYuvP010 = hasYuvP010,
                    performancePoints = encPerfPoints.toList(),
                )
            )
        }

        val av1EncoderNames =
            list.codecInfos
                .filter { it.isEncoder && !it.isAlias && MIME_AV1 in it.supportedTypes }
                .map { it.name }
                .distinct()
                .sorted()
        val supportsAv1 = av1EncoderNames.isNotEmpty()

        val matrix = CapabilityMatrix(
            encoders = encoderInfos,
            performancePoints = allPerformancePoints.distinctBy { "${it.width}x${it.height}@${it.fps}" },
            supportsMain10 = supportsMain10,
            supportsHdr10 = supportsHdr10,
            supportsHdr10Plus = supportsHdr10Plus,
            supportsYuvP010 = supportsYuvP010,
            maxFps1080p = maxFps1080p,
            maxFps4k = maxFps4k,
            maxFps8k = maxFps8k,
            supports4k = maxFps4k > 0,
            supports8k = maxFps8k > 0,
            supportsAv1 = supportsAv1,
            av1EncoderNames = av1EncoderNames,
        )

        Log.i(TAG, "capProbeResult ${matrix.summary()}")
        if (supportsAv1) {
            Log.i(TAG, "av1Encoders=${av1EncoderNames.joinToString(",")}")
        }
        for (enc in matrix.encoders) {
            Log.i(TAG, "encoder name=${enc.name} maxRes=${enc.maxWidth}x${enc.maxHeight} " +
                "profiles=${enc.profiles.size} yuvp010=${enc.supportsYuvP010} " +
                "perfPoints=${enc.performancePoints.size}")
        }
        for (pp in matrix.performancePoints.sortedWith(compareByDescending<PerformancePoint> { it.width }.thenByDescending { it.fps })) {
            Log.i(TAG, "perfPoint ${pp.label} encoder=${pp.encoderName}")
        }

        return matrix
    }

    /**
     * Return tier FPS options for [resolution] based on performance-point guarantees.
     * Falls back to a conservative default if the probe has not been run yet.
     */
    fun fpsOptionsForResolution(width: Int, height: Int): List<Int> {
        val matrix = cached ?: return defaultFpsOptions(width, height)
        val matching = matrix.performancePoints
            .filter { it.width >= width && it.height >= height }
            .map { it.fps }
            .distinct()
            .sorted()
        return matching.ifEmpty { defaultFpsOptions(width, height) }
    }

    private fun defaultFpsOptions(width: Int, height: Int): List<Int> = when {
        width >= 7680 -> listOf(30)
        width >= 3840 -> listOf(30, 60)
        else -> listOf(30, 60, 120)
    }

    /**
     * True if [width]x[height]@[fps] is a confirmed performance-point on this device.
     */
    fun isPerformancePoint(width: Int, height: Int, fps: Int): Boolean {
        val matrix = cached ?: return false
        return matrix.performancePoints.any { it.width >= width && it.height >= height && it.fps >= fps }
    }

    /** Sprint **VF.1** — hardware AV1 encoder advertised in [MediaCodecList]. */
    fun supportsAv1Encoder(): Boolean = cached?.supportsAv1 == true

    /** QTI HW AV1 (e.g. `c2.qti.av1.encoder`) — required for HFR AV1; SW encoder cannot sustain 120fps. */
    fun supportsHardwareAv1Encoder(): Boolean =
        cached?.av1EncoderNames?.any { it.contains("qti", ignoreCase = true) } == true
}
