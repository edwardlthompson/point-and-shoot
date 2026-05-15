package dev.pointandshoot

/**
 * Finder readout strip + **`PNS.ChromeUx`** `readoutCapture=` labels for the still pipeline.
 *
 * Milestone **10.5** “RAW depth honesty”: **Ultra-Max** shows **DNG12** (uncompressed RAW12 DNG intent)
 * so the chip does not imply the same lossless path as **Standard Pro** **DNG**. **DNG+** / **DNG12+**
 * mean a hardware JPEG companion surface is actually attached (`PreviewController.previewUsesJpegCompanion`).
 */
object PreviewReadoutStillPipeline {

    /** Short label on the readout strip RAW pipeline chip. */
    fun chipLabel(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        when {
            imagingProfile is ImagingProfile.JpegOnly -> "JPG"
            imagingProfile is ImagingProfile.UltraMax ->
                if (stillCaptureJpegCompanion && sessionJpegCompanionReady) {
                    "DNG12+"
                } else {
                    "DNG12"
                }
            stillCaptureJpegCompanion && sessionJpegCompanionReady -> "DNG+"
            else -> "DNG"
        }

    /** Same string as [chipLabel]; emitted as `PNS.ChromeUx` `readoutCapture=` for gate scripts. */
    fun chromeUxLogValue(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        chipLabel(imagingProfile, stillCaptureJpegCompanion, sessionJpegCompanionReady)

    fun chipContentDescription(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        when {
            imagingProfile is ImagingProfile.JpegOnly ->
                "Still capture pipeline. JPG-only profile (hardware JPEG still, no DNG). Opens capture menu."
            imagingProfile is ImagingProfile.UltraMax ->
                when {
                    stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                        "Still capture pipeline. Ultra-Max uncompressed RAW12 DNG with hardware JPEG companion. Opens RAW menu."
                    stillCaptureJpegCompanion ->
                        "Still capture pipeline. Ultra-Max RAW12 DNG; JPEG companion requested but surface not active " +
                            "(RAW12+JPEG can be disabled on some devices). Opens RAW menu."
                    else ->
                        "Still capture pipeline. Ultra-Max uncompressed RAW12 DNG without JPEG companion. Opens RAW menu."
                }
            stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                "Still capture pipeline. Lossless DNG with hardware JPEG companion. Opens RAW menu."
            stillCaptureJpegCompanion ->
                "Still capture pipeline. DNG with JPEG requested but companion surface not active; " +
                    "DNG only until session supports it. Opens RAW menu."
            else ->
                "Still capture pipeline. Lossless DNG only. Opens RAW menu."
        }
}
