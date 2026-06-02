package dev.pointandshoot

/**
 * M19.6 — planned still export paths (scaffold only; no full mux yet).
 */
enum class StillExportKind(val catalogId: String, val label: String, val shipped: Boolean) {
    Jpeg("still.jpeg", "JPEG", shipped = true),
    Dng("still.dng", "DNG", shipped = true),
    Avif("still.avif", "AVIF", shipped = true),
    Heic("still.heic", "HEIC", shipped = true),
    MotionPhoto("still.motion_photo", "Motion Photo", shipped = true),
    Tiff16("still.tiff16", "TIFF 16-bit", shipped = true),
    JpegXl("still.jxl", "JPEG XL", shipped = true),
}

object StillExportScaffolds {
    fun availableKinds(): List<StillExportKind> = StillExportKind.entries

    fun isEnabled(kind: StillExportKind): Boolean = kind.shipped

    fun tonalKinds(): List<StillExportKind> = availableKinds().filter { it != StillExportKind.Dng }

    fun fromOrdinal(ordinal: Int): StillExportKind? = availableKinds().getOrNull(ordinal)

    fun toOrdinal(kind: StillExportKind?): Int = kind?.ordinal ?: -1

    fun fromAdbValue(raw: String?): StillExportKind? {
        val s = raw?.trim()?.lowercase() ?: return null
        return when (s) {
            "heic" -> StillExportKind.Heic
            "motion_photo", "motionphoto", "motion" -> StillExportKind.MotionPhoto
            "tiff16", "tiff", "tif16" -> StillExportKind.Tiff16
            "jxl", "jpegxl" -> StillExportKind.JpegXl
            "avif" -> StillExportKind.Avif
            "jpeg", "jpg" -> StillExportKind.Jpeg
            else -> null
        }
    }

    fun statusLabel(kind: StillExportKind): String =
        if (kind.shipped) "Shipped" else "Planned — M19 scaffold"

    fun supportsColorSpace(kind: StillExportKind, color: ColorSpaceTarget?): Boolean =
        when (kind) {
            StillExportKind.Dng -> true
            StillExportKind.JpegXl,
            StillExportKind.Heic,
            StillExportKind.Tiff16,
            -> color == ColorSpaceTarget.Rec2020
            StillExportKind.Avif,
            StillExportKind.Jpeg,
            StillExportKind.MotionPhoto,
            -> color != ColorSpaceTarget.Rec2020
        }

    fun maxKindForColor(color: ColorSpaceTarget?): StillExportKind =
        if (color == ColorSpaceTarget.Rec2020) {
            StillExportKind.Tiff16
        } else {
            StillExportKind.Avif
        }
}
