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

/**
 * Optional **Open Camera–style** AF settle: after [CameraCaptureSession.stopRepeating], issue one or
 * more **preview-surface-only** [CameraDevice.TEMPLATE_PREVIEW] captures to let the HAL converge
 * before the high-res still. Gated in-app (no [adbValidationShotLabel]); flash must stay off.
 *
 * This is not a full OpenCamera state machine — only a narrow polling loop for fleet triage on
 * tele / tripod softness when [StillCaptureAfFreeze] alone is insufficient.
 */
object StillCaptureOpenCameraAfSettle {
    private const val TAG = "PNS.StillAfSettle"
    private const val MAX_ROUNDS = 14
    private const val RETRY_DELAY_MS = 55L

    /**
     * @param configurePreviewLikeStill same AF/AE/crop wiring as preview (typically
     * [PreviewController] `applyScalerCropAndMetering` + readout WB / exposure).
     */
    fun runIfEnabled(
        enabled: Boolean,
        session: CameraCaptureSession,
        camera: CameraDevice,
        previewSurface: Surface,
        chars: CameraCharacteristics,
        configurePreviewLikeStill: CaptureRequest.Builder.() -> Unit,
        bgHandler: Handler,
        onComplete: () -> Unit,
    ) {
        if (!enabled) {
            onComplete()
            return
        }
        Log.i(TAG, "beginAfSettleBeforeStill maxRounds=$MAX_ROUNDS")
        var round = 0
        lateinit var poll: Runnable
        poll =
            Runnable {
                if (round >= MAX_ROUNDS) {
                    Log.i(TAG, "afSettleTimeout rounds=$MAX_ROUNDS proceedStill=true")
                    onComplete()
                    return@Runnable
                }
                val req =
                    try {
                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            configurePreviewLikeStill()
                            val keys = chars.availableCaptureRequestKeys ?: emptyList()
                            if (keys.contains(CaptureRequest.CONTROL_AF_TRIGGER)) {
                                val trig =
                                    if (round == 0) {
                                        CaptureRequest.CONTROL_AF_TRIGGER_START
                                    } else {
                                        CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                                    }
                                set(CaptureRequest.CONTROL_AF_TRIGGER, trig)
                            }
                        }.build()
                    } catch (t: Throwable) {
                        Log.w(TAG, "afSettleBuildFailed err=${t.message}")
                        onComplete()
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
                                val af = result.get(CaptureResult.CONTROL_AF_STATE)
                                val ready =
                                    af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                                        af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                                if (ready) {
                                    Log.i(TAG, "afSettleConverged round=$round afState=$af")
                                    onComplete()
                                } else {
                                    bgHandler.postDelayed(poll, RETRY_DELAY_MS)
                                }
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure,
                            ) {
                                Log.w(TAG, "afSettleCaptureFailed reason=${failure.reason} proceedStill=true")
                                onComplete()
                            }
                        },
                        bgHandler,
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "afSettleCaptureSubmitFailed err=${t.message}")
                    onComplete()
                }
            }
        bgHandler.post(poll)
    }
}
