package dev.pointandshoot

import android.content.Context
import android.util.Log

/**
 * Sprint **CC.3** — pro capture helpers (tether, picture profiles, calibration export).
 * In-app RAW/JPEG **editing** is intentionally out of scope (deferred).
 */
object ProCapture {
    private const val TAG = "PNS.ProCapture"

    fun bindTetherCallbacks(
        server: TetheredCaptureServer,
        controller: PreviewControllerBridge,
        onStillCapture: () -> Unit,
        onFlashMode: (PreviewFlashMode) -> Unit,
    ) {
        server.onCapture = onStillCapture
        server.onFlashMode = onFlashMode
        server.statusProvider = {
            TetheredCaptureServer.StatusSnapshot(
                canCaptureStill = controller.canCaptureStill(),
                primaryPhoto = controller.primaryPhoto(),
                cameraId = controller.selectedCameraId(),
                fps = controller.selectedFps(),
                flashMode = controller.previewFlashMode().name,
            )
        }
    }

    fun startTether(server: TetheredCaptureServer) {
        server.start()
        Log.i(TAG, "tetherServer start port=${TetheredCaptureServer.DEFAULT_PORT}")
    }

    fun stopTether(server: TetheredCaptureServer) {
        server.stop()
        Log.i(TAG, "tetherServer stop")
    }

    fun applyPictureProfile(
        context: Context,
        profile: ProPictureProfile,
        hudState: HudSettingsState,
        onImagingProfile: ((ImagingProfile) -> Unit)? = null,
    ) {
        val next = profile.applyToHud(hudState.current)
        hudState.update(next)
        HudSettings.save(context.applicationContext, next)
        profile.imagingProfile?.let { imaging -> onImagingProfile?.invoke(imaging) }
        Log.i(TAG, "pictureProfile applied id=${profile.id} lut=${profile.stillsLut.name}")
    }

    /** Narrow surface so [ProCapture] does not depend on private [PreviewController]. */
    interface PreviewControllerBridge {
        fun canCaptureStill(): Boolean
        fun primaryPhoto(): Boolean
        fun selectedCameraId(): String?
        fun selectedFps(): Int
        fun previewFlashMode(): PreviewFlashMode
    }
}
