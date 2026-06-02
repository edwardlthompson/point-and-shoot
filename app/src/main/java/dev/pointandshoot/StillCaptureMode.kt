package dev.pointandshoot

/**
 * Milestone **13.8** — product still strategies sharing one [android.hardware.camera2.DngCreator] path.
 * **Standard** and **ZslStill** (13.8b) use the ReferenceCam [android.hardware.camera2.DngCreator] path.
 * [HdrStill] ships in 13.8c as a capped EV bracket (burst of DNGs).
 */
enum class StillCaptureMode {
    /** ReferenceCam-class single still (default). */
    Standard,

    /** MotionCam-inspired: pick best frame from a preview ring buffer. */
    ZslStill,

    /** MotionCam-inspired: multi-frame EV bracket → one DNG (or burst). */
    HdrStill,
}

object StillCaptureModePolicy {
    fun parseAdbExtra(raw: String?): StillCaptureMode? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "standard", "std", "s" -> StillCaptureMode.Standard
            "zsl", "zsl_still", "zslstill" -> StillCaptureMode.ZslStill
            "hdr", "hdr_still", "hdrstill" -> StillCaptureMode.HdrStill
            else -> null
        }
    }

    fun effectiveForCapture(requested: StillCaptureMode): StillCaptureMode =
        when (requested) {
            StillCaptureMode.Standard -> StillCaptureMode.Standard
            StillCaptureMode.ZslStill ->
                if (isZslStillImplemented()) {
                    StillCaptureMode.ZslStill
                } else {
                    StillCaptureMode.Standard
                }
            StillCaptureMode.HdrStill ->
                if (isHdrStillImplemented()) {
                    StillCaptureMode.HdrStill
                } else {
                    StillCaptureMode.Standard
                }
        }

    fun isZslStillImplemented(): Boolean = true

    fun isHdrStillImplemented(): Boolean = true

    fun isImplemented(requested: StillCaptureMode): Boolean =
        when (requested) {
            StillCaptureMode.Standard -> true
            StillCaptureMode.ZslStill -> isZslStillImplemented()
            StillCaptureMode.HdrStill -> isHdrStillImplemented()
        }
}
