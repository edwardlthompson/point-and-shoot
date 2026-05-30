package dev.pointandshoot

/**
 * Registry entry for format picker quality disclosure (M19.6).
 * Catalog gate fails if a shipped codec/container lacks a descriptor.
 */
data class FormatQualityDescriptor(
    val catalogId: String,
    val containerLabel: String,
    val bitDepthLabel: String,
    val compressionLabel: String,
    val sortWeight: Int,
    val bitrateMbpsHint: Int? = null,
)

object FormatQualityRegistry {
    private val videoDescriptors: Map<VideoCodec, FormatQualityDescriptor> =
        mapOf(
            VideoCodec.DCG to
                FormatQualityDescriptor(
                    catalogId = "video.dcg_hdr",
                    containerLabel = "MP4 (HEVC HDR10)",
                    bitDepthLabel = "10-bit PQ",
                    compressionLabel = "HEVC Main10 HDR",
                    sortWeight = 100,
                ),
            VideoCodec.H265_10BIT to
                FormatQualityDescriptor(
                    catalogId = "video.hevc",
                    containerLabel = "MP4 (HEVC)",
                    bitDepthLabel = "10-bit HLG",
                    compressionLabel = "HEVC Main10",
                    sortWeight = 90,
                ),
            VideoCodec.H265 to
                FormatQualityDescriptor(
                    catalogId = "video.hevc",
                    containerLabel = "MP4 (HEVC)",
                    bitDepthLabel = "8-bit",
                    compressionLabel = "HEVC Main",
                    sortWeight = 75,
                ),
            VideoCodec.AV1 to
                FormatQualityDescriptor(
                    catalogId = "video.av1",
                    containerLabel = "WebM (AV1)",
                    bitDepthLabel = "8-bit",
                    compressionLabel = "AV1",
                    sortWeight = 80,
                ),
            VideoCodec.VP9 to
                FormatQualityDescriptor(
                    catalogId = "video.vp9",
                    containerLabel = "WebM (VP9)",
                    bitDepthLabel = "8-bit",
                    compressionLabel = "VP9",
                    sortWeight = 70,
                ),
            VideoCodec.H264 to
                FormatQualityDescriptor(
                    catalogId = "video.h264",
                    containerLabel = "MP4 (H.264)",
                    bitDepthLabel = "8-bit",
                    compressionLabel = "AVC",
                    sortWeight = 60,
                ),
        )

    fun forVideoCodec(codec: VideoCodec): FormatQualityDescriptor? = videoDescriptors[codec]

    fun sortVideoCodecs(codecs: List<VideoCodec>): List<VideoCodec> =
        codecs.sortedByDescending { videoDescriptors[it]?.sortWeight ?: 0 }
}
