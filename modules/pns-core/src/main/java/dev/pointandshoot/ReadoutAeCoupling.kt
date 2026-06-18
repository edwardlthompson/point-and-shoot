package dev.pointandshoot

/**
 * Readout AE axis coupling (Sprint **14.7**).
 * Full sensor manual uses **both** readout chips locked (AE off).
 */
enum class ReadoutAeCoupling {
    AUTO,
    LOCKED_ISO_AUTO_SS,
    LOCKED_SS_AUTO_ISO,
    MANUAL_BOTH,
    ;

    companion object {
        fun fromOverrides(
            iso: Int?,
            exposureNs: Long?,
        ): ReadoutAeCoupling =
            when {
                iso != null && exposureNs != null -> MANUAL_BOTH
                iso != null -> LOCKED_ISO_AUTO_SS
                exposureNs != null -> LOCKED_SS_AUTO_ISO
                else -> AUTO
            }
    }
}
