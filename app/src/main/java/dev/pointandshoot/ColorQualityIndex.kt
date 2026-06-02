package dev.pointandshoot

/**
 * Color Quality Index (CQI) for still + video color-space picker rows (M19.6).
 *
 * CQI = round(0.55 × gamut + 0.35 × bitDepth + 0.10 × transfer)
 */
object ColorQualityIndex {
    enum class Gamut(val gamutScore: Int) {
        Rec2020(100),
        ProPhotoRgb(97),
        AdobeRgb1998(85),
        DisplayP3(80),
        SrgbRec709(65),
    }

    enum class Transfer(val transferScore: Int) {
        Hdr10Pq(100),
        Hlg(90),
        Bt2020Sdr(85),
        Gamma709(70),
        FlatCine(75),
    }

    /** Video color-space step — filters downstream codec rows in [VideoFormatPickerSheet]. */
    data class VideoColorSpace(
        val id: String,
        val displayName: String,
        val cqi: Int,
        val allowedCodecs: Set<VideoCodec>,
    )

    fun bitDepthScore(bitDepth: Int): Int =
        when {
            bitDepth >= 12 -> 100
            bitDepth >= 10 -> 85
            else -> 65
        }

    fun compute(gamut: Gamut, bitDepth: Int, transfer: Transfer): Int {
        val bd = bitDepthScore(bitDepth)
        val raw = 0.55 * gamut.gamutScore + 0.35 * bd + 0.10 * transfer.transferScore
        return raw.toInt().coerceIn(0, 100)
    }

    fun label(name: String, cqi: Int): String = "$name · CQI $cqi"

    /** Stills — default ordering high → low. */
    fun stillSpacesForPicker(): List<Pair<ColorSpaceTarget, Int>> =
        listOf(
            ColorSpaceTarget.Rec2020 to compute(Gamut.Rec2020, 10, Transfer.Hdr10Pq),
            // Keep true output-max path (Rec2020 HDR) at the top for picker MAX actions.
            ColorSpaceTarget.ProPhotoRgb to compute(Gamut.ProPhotoRgb, 10, Transfer.Gamma709),
            ColorSpaceTarget.AdobeRgb1998 to compute(Gamut.AdobeRgb1998, 10, Transfer.Gamma709),
            ColorSpaceTarget.DisplayP3 to compute(Gamut.DisplayP3, 10, Transfer.Gamma709),
            ColorSpaceTarget.SrgbRec709 to compute(Gamut.SrgbRec709, 8, Transfer.Gamma709),
        ).sortedByDescending { it.second }

    /** Video — HDR10 / HLG / Rec709 SDR / flat log-ish. */
    fun videoSpacesForPicker(): List<VideoColorSpace> =
        listOf(
            VideoColorSpace(
                id = "hdr10",
                displayName = label("HDR10 PQ", compute(Gamut.Rec2020, 10, Transfer.Hdr10Pq)),
                cqi = compute(Gamut.Rec2020, 10, Transfer.Hdr10Pq),
                allowedCodecs = setOf(VideoCodec.DCG, VideoCodec.H265_10BIT),
            ),
            VideoColorSpace(
                id = "hlg",
                displayName = label("HLG", compute(Gamut.Rec2020, 10, Transfer.Hlg)),
                cqi = compute(Gamut.Rec2020, 10, Transfer.Hlg),
                allowedCodecs = setOf(VideoCodec.H265_10BIT, VideoCodec.H265),
            ),
            VideoColorSpace(
                id = "rec709",
                displayName = label("Rec.709 SDR", compute(Gamut.SrgbRec709, 8, Transfer.Gamma709)),
                cqi = compute(Gamut.SrgbRec709, 8, Transfer.Gamma709),
                allowedCodecs =
                    setOf(
                        VideoCodec.H264,
                        VideoCodec.H265,
                        VideoCodec.AV1,
                        VideoCodec.VP9,
                    ),
            ),
            VideoColorSpace(
                id = "flat",
                displayName = label("Flat / log-ish", compute(Gamut.DisplayP3, 10, Transfer.FlatCine)),
                cqi = compute(Gamut.DisplayP3, 10, Transfer.FlatCine),
                allowedCodecs = setOf(VideoCodec.H265_10BIT, VideoCodec.H265, VideoCodec.H264),
            ),
        ).sortedByDescending { it.cqi }

    fun filterVideoFormats(formats: List<VideoFormat>, space: VideoColorSpace?): List<VideoFormat> {
        if (space == null) return formats
        return formats.filter { it.codec in space.allowedCodecs }
    }

    fun resolveVideoColorSpace(ordinal: Int): VideoColorSpace? =
        videoSpacesForPicker().getOrNull(ordinal)

    fun resolveStillColorSpace(ordinal: Int): ColorSpaceTarget? =
        stillSpacesForPicker().map { it.first }.getOrNull(ordinal)
}
