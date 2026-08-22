package dev.pointandshoot

enum class PnsGeotagMode(val storageId: String, val label: String) {
    Off("off", "Off"),
    Coarse("coarse", "Coarse"),
    Precise("precise", "Precise"),
    ;

    companion object {
        fun fromStorage(id: String?, legacyOn: Boolean): PnsGeotagMode =
            entries.firstOrNull { it.storageId == id }
                ?: if (legacyOn) Precise else Off
    }
}
