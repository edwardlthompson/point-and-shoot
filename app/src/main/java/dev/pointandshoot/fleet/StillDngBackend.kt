package dev.pointandshoot.fleet

import dev.pointandshoot.DngSaveBisectState

/**
 * Still DNG encode / IQ strategy (Milestone **13.3g**).
 *
 * - [FRAMEWORK_REFERENCEAPP] — Java Camera2 + [android.hardware.camera2.DngCreator] (ReferenceCam-aligned).
 * - [ALTREFERENCEAPP_INSPIRED] — Same writer; RAW size + still IQ aligned with AltReferenceApp device profile
 *   until [ALTREFERENCEAPP_NATIVE] lands.
 * - [ALTREFERENCEAPP_NATIVE] — Future JNI + native DNG encode (AltReferenceApp `RawEncoder` class); not shipped.
 */
enum class StillDngBackend {
    FRAMEWORK_REFERENCEAPP,
    ALTREFERENCEAPP_INSPIRED,
    ALTREFERENCEAPP_NATIVE,
}

object StillDngBackendPolicy {

    fun active(): StillDngBackend =
        DngSaveBisectState.stillDngBackendOverride ?: LegacyFleetPolicy.stillDngBackend()

    fun usesFrameworkDngCreator(backend: StillDngBackend): Boolean =
        backend != StillDngBackend.ALTREFERENCEAPP_NATIVE
}
