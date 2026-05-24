package dev.pointandshoot

import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.util.Size

/**
 * Video format configuration.
 *
 * Supports multiple encoding profiles:
 * - H.264 (AVC) 8-bit: Standard compatibility
 * - H.265 (HEVC) 8-bit: Better compression
 * - H.265 (HEVC) 10-bit: "RAW-like" high quality / HLG10
 * - DCG (Dual Conversion Gain): Vendor-specific HDR (HEVC Main10HDR10)
 * - AV1: Next-gen codec (device permitting)
 */
enum class VideoCodec {
    H264,       // AVC 8-bit baseline
    H265,       // HEVC 8-bit efficiency
    H265_10BIT, // HEVC 10-bit "RAW-like" / HLG10
    DCG,        // Vendor HDR mode (requires HEVC Main10HDR10)
    AV1,        // AV1 8-bit (c2.qti.av1.encoder where available)
}

data class VideoFormat(
    val codec: VideoCodec,
    val resolution: Size,
    val frameRate: Int,
    val bitrate: Int,
    val isTenBit: Boolean = false,
    val isDcg: Boolean = false
) {
    fun getMediaRecorderVideoEncoder(): Int = when (codec) {
        VideoCodec.H264 -> MediaRecorder.VideoEncoder.H264
        VideoCodec.H265, VideoCodec.H265_10BIT, VideoCodec.DCG, VideoCodec.AV1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                MediaRecorder.VideoEncoder.HEVC
            } else {
                MediaRecorder.VideoEncoder.H264 // Fallback
            }
        }
    }

    fun getLabel(): String = when (codec) {
        VideoCodec.H264 -> "H.264"
        VideoCodec.H265 -> "H.265"
        VideoCodec.H265_10BIT -> "H.265 10-bit"
        VideoCodec.DCG -> "DCG"
        VideoCodec.AV1 -> "AV1"
    }

    fun getBitrateLabel(): String = "${bitrate / 1_000_000} Mbps"

    fun getQualityHint(): String = when (codec) {
        VideoCodec.H264 -> "Standard compatibility"
        VideoCodec.H265 -> "25% smaller files"
        VideoCodec.H265_10BIT -> "Best quality — HLG10"
        VideoCodec.DCG -> "Maximum dynamic range — HDR10"
        VideoCodec.AV1 -> "Next-gen — best compression"
    }

    /** True when this format requires the MediaCodec path (HFR, 10-bit, DCG, or AV1). */
    val requiresMediaCodec: Boolean
        get() =
            codec == VideoCodec.AV1 ||
                isTenBit ||
                isDcg ||
                frameRate > VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS
}

object VideoFormatPresets {

    /**
     * All resolution tiers offered to the user when "all hardware tiers" is enabled.
     * Each tier is probed against [MediaCodecCapabilityProbe] at runtime; only those with
     * a confirmed performance-point are surfaced (others are hidden, not grayed out).
     */
    val ALL_TIERS: List<Size> = listOf(
        // 16:9
        Size(1280, 720),    // 720p HD
        Size(1920, 1080),   // 1080p FHD
        Size(2560, 1440),   // 1440p QHD
        Size(3840, 2160),   // 4K UHD
        Size(7680, 4320),   // 8K UHD
        // 4:3
        Size(1440, 1080),   // 1080 4:3
        Size(2160, 1620),   // ~1620p 4:3
        Size(2880, 2160),   // 4K 4:3
        // 4K DCI / 17:9
        Size(4096, 2160),   // 4K DCI
        // 21:9 cinematic
        Size(2560, 1080),   // 2K 21:9
        Size(3840, 1644),   // 4K 21:9 (common on tall-sensor phones)
        // 1:1 square
        Size(1080, 1080),   // 1080 square
        Size(2160, 2160),   // 4K square
    )

    /**
     * Bitrate table (fixed targets validated against performance-point guarantees).
     *
     * Base values per codec at each resolution × fps; capped conservatively so thermal
     * stability is maintained over long recordings.
     *
     * 4K@120fps: 120 Mbps  (verified: c2.qti.hevc.encoder performance-point)
     * 8K@30fps:  200 Mbps  (upper conservative bound; thermal limited)
     */
    @Suppress("MagicNumber")
    fun calculateBitrate(width: Int, height: Int, fps: Int, codec: VideoCodec): Int {
        val bpp = when (codec) {
            VideoCodec.H264 -> 0.10
            VideoCodec.H265 -> 0.07
            VideoCodec.H265_10BIT -> 0.15
            VideoCodec.DCG -> 0.18
            VideoCodec.AV1 -> 0.05  // AV1 achieves better quality at lower bitrate
        }
        val computed = (width.toLong() * height * fps * bpp).toInt()
        // Absolute caps to prevent absurd values at extreme fps × resolution combos
        return when {
            width >= 7680 -> computed.coerceAtMost(200_000_000)  // 8K cap: 200 Mbps
            width >= 3840 -> computed.coerceAtMost(120_000_000)  // 4K cap: 120 Mbps
            width >= 1920 -> computed.coerceAtMost(50_000_000)   // 1080p cap: 50 Mbps
            else -> computed.coerceAtMost(20_000_000)            // 720p cap: 20 Mbps
        }
    }

    /**
     * Return all codec formats available for a given [resolution] and [fps].
     *
     * Filters based on device capabilities from [MediaCodecCapabilityProbe] when available.
     * The [fps] value must already be probe-validated by the caller (e.g. from
     * [MediaCodecCapabilityProbe.fpsOptionsForResolution]).
     */
    @Suppress("LongParameterList")
    fun getAvailableFormats(
        resolution: Size,
        fps: Int,
        supportsHevc: Boolean = true,
        supportsTenBit: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        supportsDcg: Boolean = false,
        supportsAv1: Boolean = false,
    ): List<VideoFormat> {
        val formats = mutableListOf<VideoFormat>()

        // H.264 — always available (MediaRecorder path for ≤60fps; MediaCodec for HFR)
        formats.add(VideoFormat(
            codec = VideoCodec.H264,
            resolution = resolution,
            frameRate = fps,
            bitrate = calculateBitrate(resolution.width, resolution.height, fps, VideoCodec.H264),
        ))

        if (supportsHevc && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            !VideoRecordingController.lacksTrueHfrUniqueFrames(fps, VideoCodec.H265)
        ) {
            formats.add(VideoFormat(
                codec = VideoCodec.H265,
                resolution = resolution,
                frameRate = fps,
                bitrate = calculateBitrate(resolution.width, resolution.height, fps, VideoCodec.H265),
            ))

            if (supportsTenBit &&
                !VideoRecordingController.lacksTrueHfrUniqueFrames(fps, VideoCodec.H265_10BIT)
            ) {
                formats.add(VideoFormat(
                    codec = VideoCodec.H265_10BIT,
                    resolution = resolution,
                    frameRate = fps,
                    bitrate = calculateBitrate(resolution.width, resolution.height, fps, VideoCodec.H265_10BIT),
                    isTenBit = true,
                ))

                // Sprint 13.5: DCG and HFR are mutually exclusive — Qualcomm ISP cannot sustain
                // HDR10 dynamic range profiles (EnableHDRDCGMode session parameter) at >60fps.
                // Cap DCG at 60fps to avoid session create failures / silent fallback to SDR.
                val dcgMaxFps = 60
                if (supportsDcg && fps <= dcgMaxFps) {
                    formats.add(VideoFormat(
                        codec = VideoCodec.DCG,
                        resolution = resolution,
                        frameRate = fps,
                        bitrate = calculateBitrate(resolution.width, resolution.height, fps, VideoCodec.DCG),
                        isTenBit = true,
                        isDcg = true,
                    ))
                }
            }
        }

        if (supportsAv1 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !VideoRecordingController.lacksTrueHfrUniqueFrames(fps, VideoCodec.AV1)
        ) {
            val av1HfrOk = fps < 120 || MediaCodecCapabilityProbe.supportsHardwareAv1Encoder()
            if (av1HfrOk) {
                formats.add(
                    VideoFormat(
                        codec = VideoCodec.AV1,
                        resolution = resolution,
                        frameRate = fps,
                        bitrate = calculateBitrate(resolution.width, resolution.height, fps, VideoCodec.AV1),
                    ),
                )
            }
        }

        return formats
    }

    /**
     * Picker resolution tiers: HAL HS sizes + exact encoder perf sizes + MediaRecorder outputs,
     * intersected with [ALL_TIERS] so odd HAL sizes do not flood the matrix.
     */
    fun catalogTierSizes(
        highSpeedMap: StreamConfigurationMap?,
        mediaRecorderSizes: List<Size>? = null,
    ): List<Size> {
        val probe = MediaCodecCapabilityProbe.probeSync()
        val keys = HashSet<Long>()
        fun add(w: Int, h: Int) {
            if (w > 0 && h > 0) keys += w.toLong() * 1_000_000L + h
        }
        runCatching { highSpeedMap?.highSpeedVideoSizes?.forEach { add(it.width, it.height) } }
        mediaRecorderSizes?.forEach { add(it.width, it.height) }
        probe.performancePoints.forEach { add(it.width, it.height) }
        probe.h264PerformancePoints.forEach { add(it.width, it.height) }
        val tiers =
            ALL_TIERS.filter { tier -> keys.contains(tier.width.toLong() * 1_000_000L + tier.height) }
        if (tiers.isNotEmpty()) {
            return tiers.sortedByDescending { it.width.toLong() * it.height }
        }
        return listOf(Size(1920, 1080), Size(1280, 720))
    }

    /**
     * Return all (resolution, fps) pairs supported as performance-points on this device.
     * Uses [MediaCodecCapabilityProbe] cache; falls back to conservative defaults if probe not run.
     * Results are sorted resolution-descending then fps-ascending for display.
     */
    fun getHardwareTiers(
        supportsDcg: Boolean = false,
        supportsAv1: Boolean = false,
        highSpeedMap: StreamConfigurationMap? = null,
        mediaRecorderSizes: List<Size>? = null,
    ): List<VideoFormat> {
        val probe = MediaCodecCapabilityProbe
        val result = mutableListOf<VideoFormat>()

        for (tier in catalogTierSizes(highSpeedMap, mediaRecorderSizes)) {
            val fpsOptions = probe.fpsOptionsForResolution(tier.width, tier.height, highSpeedMap)
            val tenBit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                probe.probeSync().supportsMain10
            for (fps in fpsOptions) {
                result.addAll(
                    getAvailableFormats(
                        resolution = tier,
                        fps = fps,
                        supportsTenBit = tenBit,
                        supportsDcg = supportsDcg,
                        supportsAv1 = supportsAv1,
                    )
                )
            }
        }

        return result.distinctBy { "${it.resolution.width}x${it.resolution.height}@${it.frameRate}_${it.codec}" }
            .sortedWith(compareByDescending<VideoFormat> { it.resolution.width }
                .thenByDescending { it.resolution.height }
                .thenBy { it.frameRate }
                .thenBy { it.codec.ordinal })
    }

    /**
     * Legacy helper: 1080p@60fps "RAW-like" bitrate used by Sprint 13.2.
     */
    fun getRawLikeBitrate(resolution: Size, fps: Int): Int = when {
        resolution.width >= 3840 -> 50_000_000
        resolution.width >= 1920 -> 20_000_000
        else -> 10_000_000
    }
}
