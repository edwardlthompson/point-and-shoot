package dev.pointandshoot

/**
 * Last observed [android.hardware.camera2.CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID]
 * for engineering UI (hub no longer shows it on the main readout strip).
 */
object PreviewLogicalPhysicalDebugBridge {
    @Volatile private var lastActivePhysicalId: String? = null

    fun updateFromCaptureResult(id: String?) {
        if (id.isNullOrBlank()) return
        lastActivePhysicalId = id
    }

    fun clear() {
        lastActivePhysicalId = null
    }

    fun snapshot(): String? = lastActivePhysicalId
}
