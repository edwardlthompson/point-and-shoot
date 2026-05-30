package dev.pointandshoot

/**
 * M19.6 — planned still export paths (scaffold only; no full mux yet).
 */
enum class StillExportKind(val catalogId: String, val label: String, val shipped: Boolean) {
    Jpeg("still.jpeg", "JPEG", shipped = true),
    Dng("still.dng", "DNG", shipped = true),
    Avif("still.avif", "AVIF", shipped = true),
    Heic("still.heic", "HEIC", shipped = false),
    MotionPhoto("still.motion_photo", "Motion Photo", shipped = false),
    Tiff16("still.tiff16", "TIFF 16-bit", shipped = false),
}

object StillExportScaffolds {
    fun availableKinds(): List<StillExportKind> = StillExportKind.entries

    fun isEnabled(kind: StillExportKind): Boolean = kind.shipped

    fun statusLabel(kind: StillExportKind): String =
        if (kind.shipped) "Shipped" else "Planned — M19 scaffold"
}
