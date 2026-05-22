package dev.pointandshoot

import android.content.Context

/**
 * Post-decode color for hardware JPEG stills: creative [LutCatalog] wins; otherwise
 * apply the newest chart [CalibrationProfile] when present ([LutCatalog.None] path).
 */
object StillCaptureColorApply {

    fun applyToRgb888InPlace(
        appContext: Context,
        rgb888: ByteArray,
        width: Int,
        height: Int,
        stillsLut: LutCatalog,
    ) {
        if (stillsLut != LutCatalog.None) {
            StillRgbLut.applyToRgb888InPlace(
                rgb888,
                width,
                height,
                stillsLut.load(BuiltInLuts.DEFAULT_SIZE),
            )
            return
        }
        val profile = CalibrationProfileStorage.loadNewest(appContext.applicationContext) ?: return
        StillRgbLut.applyCalibrationProfileInPlace(rgb888, width, height, profile)
    }
}
