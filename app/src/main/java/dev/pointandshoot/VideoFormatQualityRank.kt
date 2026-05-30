package dev.pointandshoot

/**
 * Mathematical format ranking for Max presets and codec sort (M19.6).
 * Favors bit depth over raw Mbps when tradeoff exists.
 */
object VideoFormatQualityRank {
    private const val W_DEPTH = 0.45
    private const val W_CODEC = 0.30
    private const val W_BITRATE = 0.15
    private const val W_FPS = 0.10

    fun score(format: VideoFormat): Double {
        val depth =
            when {
                format.isDcg -> 100.0
                format.isTenBit -> 85.0
                format.codec == VideoCodec.AV1 -> 78.0
                format.codec == VideoCodec.H265 -> 72.0
                format.codec == VideoCodec.VP9 -> 68.0
                else -> 60.0
            }
        val codecTier = FormatQualityRegistry.forVideoCodec(format.codec)?.sortWeight?.toDouble() ?: 50.0
        val brNorm = (format.bitrate / 120_000_000.0).coerceIn(0.0, 1.0) * 100.0
        val fpsNorm = format.frameRate.toDouble().coerceAtMost(120.0) / 120.0 * 100.0
        return W_DEPTH * depth + W_CODEC * codecTier + W_BITRATE * brNorm + W_FPS * fpsNorm
    }

    fun compare(a: VideoFormat, b: VideoFormat): Int = score(b).compareTo(score(a))

    /** Best format in bucket for Max presets — prefers depth over HFR per product rule. */
    fun pickBest(formats: List<VideoFormat>): VideoFormat? =
        formats.maxWithOrNull(compareByDescending<VideoFormat> { score(it) }.thenBy { it.frameRate })

    enum class ResolutionBucket(val minWidth: Int, val label: String) {
        UHD8K(7680, "8K Max"),
        UHD4K(3840, "4K Max"),
        FHD(1920, "FHD Max"),
        ;

        fun matches(w: Int, h: Int): Boolean = w >= minWidth || h >= (minWidth * 9 / 16)
    }

    fun pickBestForBucket(formats: List<VideoFormat>, bucket: ResolutionBucket): VideoFormat? {
        val inBucket =
            formats.filter { bucket.matches(it.resolution.width, it.resolution.height) }
        return pickBest(inBucket)
    }
}
