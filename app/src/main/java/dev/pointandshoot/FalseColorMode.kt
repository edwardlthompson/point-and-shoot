package dev.pointandshoot

/**
 * Sprint **15.21** — preview false-color / zebra modes.
 */
enum class FalseColorMode(val storageId: String) {
    Off("off"),
    ZebraOnly("zebra"),
    FalseColor("false_color"),
    ;

    fun wantsZebra(): Boolean = this != Off

    fun wantsFalseColor(): Boolean = this == FalseColor

    companion object {
        fun fromStorage(id: String?): FalseColorMode =
            entries.firstOrNull { it.storageId == id } ?: Off
    }
}
