package dev.pointandshoot

import android.util.Log
import androidx.compose.ui.unit.IntSize

/**
 * Maps face/eye HUD landmarks from **preview-buffer** pixels to **preview-content** pixels.
 *
 * Uses the same **fit × ST** transform as [LutCameraPreviewRenderer] / [TexturePreviewFit.composeExternalOesPreviewMatrix]
 * when [surfaceTransformColumnMajor4x4] is supplied (raw [SurfaceTexture.getTransformMatrix]).
 */
object FaceHudOverlayMapping {
    private var lastDiagKey: String? = null

    fun mapBufferPointToTile(
        bufferX: Float,
        bufferY: Float,
        tileW: Int,
        tileH: Int,
        bufferW: Int,
        bufferH: Int,
        coverCrop: Boolean,
        surfaceTransformColumnMajor4x4: FloatArray? = null,
    ): Pair<Float, Float> {
        val viewW = tileW.coerceAtLeast(1)
        val viewH = tileH.coerceAtLeast(1)
        if (bufferW <= 0 || bufferH <= 0) {
            return bufferX to bufferY
        }
        if (surfaceTransformColumnMajor4x4 != null && surfaceTransformColumnMajor4x4.size >= 16) {
            return TexturePreviewFit.mapBufferToViewWithExternalOesPreview(
                bufferX,
                bufferY,
                viewW,
                viewH,
                bufferW,
                bufferH,
                coverCrop,
                surfaceTransformColumnMajor4x4,
            )
        }
        return TexturePreviewFit.mapBufferToView(
            bufferX,
            bufferY,
            viewW,
            viewH,
            bufferW,
            bufferH,
            coverCrop = coverCrop,
        )
    }

    fun logViewportDiagOnce(
        finderPx: IntSize,
        contentPx: IntSize,
        bufferSize: IntSize?,
        coverCrop: Boolean,
    ) {
        val buf = bufferSize ?: return
        if (contentPx.width <= 0 || contentPx.height <= 0 || buf.width <= 0 || buf.height <= 0) return
        val key =
            "${finderPx.width}x${finderPx.height}|${contentPx.width}x${contentPx.height}|" +
                "${buf.width}x${buf.height}|$coverCrop"
        if (key == lastDiagKey) return
        lastDiagKey = key
        Log.i(
            FaceOverlayCalibration.TAG,
            "faceHudMap finder=${finderPx.width}x${finderPx.height} " +
                "content=${contentPx.width}x${contentPx.height} buf=${buf.width}x${buf.height} " +
                "coverCrop=$coverCrop (cropLinear→buffer, mapBufferToView on content)",
        )
    }
}
