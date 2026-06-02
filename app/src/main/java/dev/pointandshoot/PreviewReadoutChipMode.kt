package dev.pointandshoot

/**
 * Sprint **14.1** — single source of truth for which readout chips appear in photo vs video tray mode.
 *
 * **Invariant:** [`PreviewEngineScreen`] must pass the live [`primaryPhoto`] flag into
 * [`PreviewReadoutStrip`]. Do not rely on the strip default (`primaryPhoto = true`).
 *
 * Spec: [`docs/M14_READOUT_STATUS_BAR.md`](docs/M14_READOUT_STATUS_BAR.md)
 */
object PreviewReadoutChipMode {
    fun isVideoMode(primaryPhoto: Boolean): Boolean = !primaryPhoto

    fun showStillLutChip(primaryPhoto: Boolean): Boolean = primaryPhoto

    fun showImgChip(primaryPhoto: Boolean): Boolean = false

    fun showVideoLutChip(primaryPhoto: Boolean): Boolean = !primaryPhoto

    fun readoutModeLogValue(primaryPhoto: Boolean): String = if (primaryPhoto) "photo" else "video"
}

/** Video format picker lives on the bottom tray FAB, not the readout chip row. */
object PreviewTrayVideoChrome {
    fun showVideoFormatFab(primaryPhoto: Boolean): Boolean = !primaryPhoto
}
