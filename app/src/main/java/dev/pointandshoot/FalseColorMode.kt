package dev.pointandshoot

/**
 * Sprint **15.21** — preview false-color / zebra modes.
 */
enum class FalseColorMode(val storageId: String, val label: String) {
    Off("off", "Off"),
    ZebraOnly("zebra", "Zebra only"),
    FalseColor("false_color", "False color"),
    ;

    fun wantsZebra(): Boolean = this != Off

    fun wantsFalseColor(): Boolean = this == FalseColor

    /** QS tile short-press cycle: off → zebra → exposure bands → off. */
    fun cycleNext(): FalseColorMode =
        when (this) {
            Off -> ZebraOnly
            ZebraOnly -> FalseColor
            FalseColor -> Off
        }

    companion object {
        fun fromStorage(id: String?): FalseColorMode =
            entries.firstOrNull { it.storageId == id } ?: Off
    }
}
