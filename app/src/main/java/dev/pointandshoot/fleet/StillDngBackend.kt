package dev.pointandshoot.fleet

import dev.pointandshoot.DngSaveBisectState

/**
 * Still DNG encode / IQ strategy (Milestone **13.3g**).
 *
 * - [FRAMEWORK_PROSHOT] — Java Camera2 + [android.hardware.camera2.DngCreator] (ProShot-aligned).
 * - [MOTIONCAM_INSPIRED] — Same writer; RAW size + still IQ aligned with MotionCam device profile
 *   until [MOTIONCAM_NATIVE] lands.
 * - [MOTIONCAM_NATIVE] — Future JNI + native DNG encode (MotionCam `RawEncoder` class); not shipped.
 */
enum class StillDngBackend {
    FRAMEWORK_PROSHOT,
    MOTIONCAM_INSPIRED,
    MOTIONCAM_NATIVE,
}

object StillDngBackendPolicy {

    fun active(): StillDngBackend =
        DngSaveBisectState.stillDngBackendOverride ?: OnePlus13FleetPolicy.stillDngBackend()

    fun usesFrameworkDngCreator(backend: StillDngBackend): Boolean =
        backend != StillDngBackend.MOTIONCAM_NATIVE
}
