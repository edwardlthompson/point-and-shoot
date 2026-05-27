package dev.pointandshoot

/**
 * Sprint **15.16** — preview/recorder color intent (SDR / HLG / flat cine).
 */
enum class VideoColorProfile(val storageId: String, val label: String) {
    Sdr("sdr", "SDR"),
    Hlg("hlg", "HLG"),
    FlatCine("flat_cine", "Flat / Cine"),
    ;

    /** VUI tag for [MediaCodecVideoRecorder] logs / ffprobe expectations. */
    fun colorVuiTag(): String =
        when (this) {
            Sdr -> "bt709"
            Hlg -> "bt2020-hlg"
            FlatCine -> "bt709-flat"
        }

    fun requiresTenBitHevc(): Boolean = this == Hlg

    companion object {
        fun fromStorage(id: String?): VideoColorProfile =
            entries.firstOrNull { it.storageId == id } ?: Sdr
    }
}
