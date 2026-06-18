package dev.pointandshoot.preview.session

import android.os.Handler
import android.view.Surface

/**
 * Up-front validation before [android.hardware.camera2.CameraDevice.createCaptureSession]
 * (H.CRI-5 slice 5) — extracted from `PreviewEngineScreen.createSession`.
 */
object PreviewSessionCreateEntry {
    sealed class Result {
        data class Ready(
            val previewSurface: Surface,
        ) : Result()

        data object AbortNoHandler : Result()

        data object AbortNoPreviewSurface : Result()

        data object AbortInvalidPreviewSurface : Result()
    }

    fun validate(
        handler: Handler?,
        previewSurface: Surface?,
    ): Result {
        if (handler == null) return Result.AbortNoHandler
        val surf = previewSurface ?: return Result.AbortNoPreviewSurface
        if (!surf.isValid) return Result.AbortInvalidPreviewSurface
        return Result.Ready(surf)
    }
}
