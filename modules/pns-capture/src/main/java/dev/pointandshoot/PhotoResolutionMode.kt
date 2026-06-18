package dev.pointandshoot

enum class PhotoResolutionMode(
    val storageId: String,
    val label: String,
) {
    Binned("binned", "Binned"),
    MaxResolution("max_resolution", "Max resolution"),
    ;

    companion object {
        fun fromStorage(raw: String?): PhotoResolutionMode =
            entries.firstOrNull { it.storageId.equals(raw, ignoreCase = true) } ?: Binned
    }
}
