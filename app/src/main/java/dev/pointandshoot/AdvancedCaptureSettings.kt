package dev.pointandshoot

/**
 * Sprint **CC.1** — burst / intervalometer / pre-capture buffer prefs (HUD + ADB).
 */
object AdvancedCaptureSettings {
    val burstCountOptions: List<Int> = listOf(3, 5, 10, 15, 20)

    val burstIntervalMsOptions: List<Int> = listOf(150, 350, 800)

    /** Sprint **15.29** — NightScape stack depth (Night dial). */
    val nightScapeFrameCountOptions: List<Int> = listOf(4, 6, 8)

    /** 0 = intervalometer off. */
    val intervalometerSecOptions: List<Int> = listOf(0, 1, 2, 5, 10, 30, 60)

    fun normalizeBurstCount(raw: Int): Int =
        burstCountOptions.minByOrNull { kotlin.math.abs(it - raw) } ?: 5

    fun normalizeBurstIntervalMs(raw: Int): Int =
        burstIntervalMsOptions.minByOrNull { kotlin.math.abs(it - raw) } ?: 350

    fun normalizeNightScapeFrameCount(raw: Int): Int =
        nightScapeFrameCountOptions.minByOrNull { kotlin.math.abs(it - raw) } ?: 6

    fun normalizeIntervalometerSec(raw: Int): Int =
        intervalometerSecOptions.minByOrNull { kotlin.math.abs(it - raw) } ?: 0
}

/**
 * Burst quality policy for photo long-press / burst shutter:
 * faster rates trade fidelity for throughput, slower rates restore quality.
 */
enum class BurstPhotoQualityProfile(
    val storageId: String,
    val label: String,
) {
    Auto("auto", "Auto"),
    ProcessedOnly("processed_only", "Processed only"),
    RawOnly("raw_only", "RAW burst"),
    RawPlusProcessed("raw_plus_processed", "RAW + processed"),
    ;

    companion object {
        fun fromStorage(raw: String?): BurstPhotoQualityProfile =
            entries.firstOrNull { it.storageId.equals(raw, ignoreCase = true) } ?: Auto
    }
}
