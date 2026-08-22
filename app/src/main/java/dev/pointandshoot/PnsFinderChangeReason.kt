package dev.pointandshoot

/** User-facing sentence when preview FPS or resolution is silently capped. */
object PnsFinderChangeReason {
    fun sentence(reason: String?): String? {
        val raw = reason?.lowercase().orEmpty()
        if (raw.isBlank()) return null
        return when {
            raw.contains("thermal") -> "Finder slowed because the phone is hot."
            raw.contains("battery") -> "Finder slowed to save battery."
            raw.contains("endurance") || raw.contains("balanced") ->
                "Finder capped by the power profile."
            raw.contains("storage") -> "Finder changed because storage is low."
            else -> "Finder changed: $reason"
        }
    }
}
