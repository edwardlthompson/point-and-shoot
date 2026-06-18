package dev.pointandshoot

/** App-side bridge to [CaptureStorage.CaptureKind] (stays in `:app`). */
fun ImagingProfile.toDngCaptureKind(): CaptureStorage.CaptureKind =
    when (this) {
        ImagingProfile.JpegOnly ->
            error("JPEG-only profile has no DNG kind - use hardware JPEG still capture path")
        else ->
            when (rawMode) {
                RawMode.LosslessCompressedDng -> CaptureStorage.CaptureKind.DngLossless
                RawMode.UncompressedRaw12Dng -> CaptureStorage.CaptureKind.DngRaw12
                RawMode.None -> error("unexpected RawMode.None on non-JpegOnly profile")
            }
    }
