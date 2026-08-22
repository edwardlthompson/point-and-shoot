package dev.pointandshoot

/** In-app list of what a new file can contain. */
object PnsPrivacyReceipt {
    val LINES: List<String> =
        listOf(
            "Location is written only when Save location is on.",
            "Artist / copyright go on JPEG only — never on DNG.",
            "Strip EXIF privacy tags removes GPS and camera identity from JPEG.",
            "Face / eye HUD is preview-only and is not written into files.",
            "Donate and update timestamps stay on this device (not backed up).",
            "GitHub update checks are opt-in per day and skip metered / Battery Saver.",
        )

    fun asParagraph(): String = LINES.joinToString("\n")
}
