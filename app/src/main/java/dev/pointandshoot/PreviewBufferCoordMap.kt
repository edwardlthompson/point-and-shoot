package dev.pointandshoot

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset

/**
 * Shared active-array ↔ preview-buffer mapping for tap-to-focus, face/eye HUD, and metering.
 *
 * Preview buffer pixels are normalized against the session [SCALER_CROP_REGION] linearly — the
 * same space [TexturePreviewFit.mapBufferToView] / [mapViewToBuffer] use for GLES
 * [LutCameraPreviewRenderer.setGeometry]. Do not apply a separate sensor quarter-turn here.
 */
object PreviewBufferCoordMap {
    fun activeArrayToPreviewBuffer(
        ax: Int,
        ay: Int,
        cropRect: Rect,
        bufW: Int,
        bufH: Int,
        mirrorHorizontally: Boolean,
    ): Offset {
        val cw = cropRect.width().coerceAtLeast(1)
        val ch = cropRect.height().coerceAtLeast(1)
        val nx = ((ax - cropRect.left).toFloat() / cw).coerceIn(0f, 1f)
        val ny = ((ay - cropRect.top).toFloat() / ch).coerceIn(0f, 1f)
        var x = nx * bufW
        val y = ny * bufH
        if (mirrorHorizontally) {
            x = bufW - x
        }
        return Offset(x, y)
    }
}
