package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.util.Log
import android.view.Surface
import dev.pointandshoot.fleet.OnePlus13FleetPolicy

/**
 * ProShot-style AE precapture after [CameraCaptureSession.stopRepeating]: one or more preview-template
 * captures with [CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER] so the still fires with converged metering.
 */
object ProShotStillPrecapture {
    private const val TAG = "PNS.ProShotStill"
    private const val MAX_ROUNDS = 24
    private const val RETRY_DELAY_MS = 50L

    fun shouldRun(chars: CameraCharacteristics): Boolean =
        OnePlus13FleetPolicy.useProShotPureDngSave() &&
            StillCaptureIqPolicy.isLeafBackCharacteristics(chars)

    fun runAfterStopRepeating(
        session: CameraCaptureSession,
        camera: CameraDevice,
        previewSurface: Surface,
        chars: CameraCharacteristics,
        configurePreviewLikeStill: CaptureRequest.Builder.() -> Unit,
        bgHandler: Handler,
        onComplete: (TotalCaptureResult?) -> Unit,
    ) {
        if (!shouldRun(chars)) {
            onComplete(null)
            return
        }
        val keys = chars.availableCaptureRequestKeys ?: emptyList()
        if (!keys.contains(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)) {
            Log.i(TAG, "aePrecapture skipped (key not advertised)")
            onComplete(null)
            return
        }
        Log.i(TAG, "aePrecapture begin maxRounds=$MAX_ROUNDS")
        var lastResult: TotalCaptureResult? = null
        var round = 0
        lateinit var poll: Runnable
        poll =
            Runnable {
                if (round >= MAX_ROUNDS) {
                    Log.i(TAG, "aePrecapture timeout rounds=$MAX_ROUNDS proceedStill=true")
                    onComplete(lastResult)
                    return@Runnable
                }
                val req =
                    try {
                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            configurePreviewLikeStill()
                            if (round == 0) {
                                set(
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                                )
                            } else if (keys.contains(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)) {
                                set(
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                                )
                            }
                        }.build()
                    } catch (t: Throwable) {
                        Log.w(TAG, "aePrecapture build failed err=${t.message}")
                        onComplete(lastResult)
                        return@Runnable
                    }
                round++
                try {
                    session.capture(
                        req,
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult,
                            ) {
                                lastResult = result
                                val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                                val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                                val ready =
                                    ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                        ae == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                                        ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                                if (ready) {
                                    Log.i(
                                        TAG,
                                        "aePrecapture converged round=$round ae=$ae iso=$iso expNs=$exp",
                                    )
                                    onComplete(lastResult)
                                } else {
                                    bgHandler.postDelayed(poll, RETRY_DELAY_MS)
                                }
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure,
                            ) {
                                Log.w(
                                    TAG,
                                    "aePrecapture capture failed reason=${failure.reason} proceedStill=true",
                                )
                                onComplete(lastResult)
                            }
                        },
                        bgHandler,
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "aePrecapture submit failed err=${t.message}")
                    onComplete(lastResult)
                }
            }
        bgHandler.post(poll)
    }
}
