package dev.pointandshoot

/**
 * Packed Y/U/V planes copied from [android.media.Image] on the camera [android.os.Handler].
 * Consumed on the GLES thread by [LutCameraPreviewRenderer].
 */
data class HfrYuvMonitorFrame(
    val width: Int,
    val height: Int,
    /** Clockwise rotation applied to texture coords ([HfrYuvTexCoord]) to match main preview. */
    val textureRotationDeg: Int,
    /** Center crop on the monitor buffer to approximate the record-camera FOV. */
    val textureCrop: HfrMonitorTextureCrop = HfrMonitorTextureCrop.FULL,
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
)
