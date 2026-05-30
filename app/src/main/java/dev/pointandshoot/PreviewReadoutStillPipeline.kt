package dev.pointandshoot

/**
 * Finder readout strip + **`PNS.ChromeUx`** `readoutCapture=` labels for the still pipeline.
 *
 * RAW and tonal tiers are **independent** (no hardware JPEG companion on the DNG request).
 * Tonal still uses a separate capture → JXL / AVIF / fallback JPEG file.
 */
object PreviewReadoutStillPipeline {

  private fun tonalSuffix(jpeg: ImgMenuTier): String =
      when (jpeg) {
          ImgMenuTier.Ultra -> "JXL"
          ImgMenuTier.Standard -> "AVIF"
          ImgMenuTier.Off -> ""
      }

    /** Short label on the readout strip RAW pipeline chip. */
    fun chipLabel(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        chipLabel(
            imagingProfile,
            wantsTonal = stillCaptureJpegCompanion,
            sessionTonalReady = sessionJpegCompanionReady,
            jpegTier = ImgMenuTier.Standard,
        )

    fun chipLabel(
        intent: ComposedStillIntent,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String {
        val wantsTonal = intent.wantsTonalStill()
        val sidecar = intent.wantsMatchedTierJpegSidecar()
        val jpegTier =
            if (wantsTonal) {
                intent.jpeg
            } else {
                ImgMenuTier.Off
            }
        return when (intent.raw) {
            ImgMenuTier.Off ->
                when (jpegTier) {
                    ImgMenuTier.Ultra -> "JXL"
                    ImgMenuTier.Standard -> "AVIF"
                    ImgMenuTier.Off -> "JPG"
                }
            ImgMenuTier.Ultra -> {
                val base = "DNG12"
                when {
                    sidecar && stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                        "$base+JPEG"
                    wantsTonal && stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                        "$base+${tonalSuffix(jpegTier)}"
                    else -> base
                }
            }
            ImgMenuTier.Standard -> {
                val base = "DNG"
                when {
                    sidecar && stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                        "$base+JPEG"
                    wantsTonal && stillCaptureJpegCompanion && sessionJpegCompanionReady ->
                        "$base+${tonalSuffix(jpegTier)}"
                    else -> base
                }
            }
        }
    }

    private fun chipLabel(
        imagingProfile: ImagingProfile,
        wantsTonal: Boolean,
        sessionTonalReady: Boolean,
        jpegTier: ImgMenuTier,
    ): String =
        when {
            imagingProfile is ImagingProfile.JpegOnly ->
                when (jpegTier) {
                    ImgMenuTier.Ultra -> "JXL"
                    ImgMenuTier.Standard -> "AVIF"
                    else -> "JPG"
                }
            imagingProfile is ImagingProfile.UltraMax ->
                if (wantsTonal && sessionTonalReady) {
                    "DNG12+${tonalSuffix(jpegTier)}"
                } else {
                    "DNG12"
                }
            wantsTonal && sessionTonalReady -> "DNG+${tonalSuffix(jpegTier)}"
            else -> "DNG"
        }

    /** Same string as [chipLabel]; emitted as `PNS.ChromeUx` `readoutCapture=` for gate scripts. */
    fun chromeUxLogValue(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        chipLabel(imagingProfile, stillCaptureJpegCompanion, sessionJpegCompanionReady)

    fun chromeUxLogValue(
        intent: ComposedStillIntent,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        chipLabel(intent, stillCaptureJpegCompanion, sessionJpegCompanionReady)

    fun chipContentDescription(
        imagingProfile: ImagingProfile,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        "Still capture pipeline. Opens IMG menu."

    fun chipContentDescription(
        intent: ComposedStillIntent,
        stillCaptureJpegCompanion: Boolean,
        sessionJpegCompanionReady: Boolean,
    ): String =
        when (intent.raw) {
            ImgMenuTier.Off ->
                "Still capture. Independent tonal still (IMG -JPEG- tier). Opens IMG menu."
            ImgMenuTier.Ultra ->
                if (intent.wantsMatchedTierJpegSidecar() && stillCaptureJpegCompanion && sessionJpegCompanionReady) {
                    "Still capture. Ultra RAW12 DNG plus JPEG sidecar (same capture). Opens IMG menu."
                } else if (intent.wantsTonalStill() && stillCaptureJpegCompanion && sessionJpegCompanionReady) {
                    "Still capture. Ultra RAW12 DNG plus separate tonal file. Opens IMG menu."
                } else {
                    "Still capture. Ultra RAW12 DNG only. Opens IMG menu."
                }
            ImgMenuTier.Standard ->
                if (intent.wantsMatchedTierJpegSidecar() && stillCaptureJpegCompanion && sessionJpegCompanionReady) {
                    "Still capture. Lossless DNG plus JPEG sidecar (same capture). Opens IMG menu."
                } else if (intent.wantsTonalStill() && stillCaptureJpegCompanion && sessionJpegCompanionReady) {
                    "Still capture. Lossless DNG plus separate tonal file. Opens IMG menu."
                } else {
                    "Still capture. Lossless DNG only. Opens IMG menu."
                }
        }
}
