package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ProShot / ReferenceCam `C0353b0.L6` / `f6` / `i4` / `j4` AE precapture — **process** match.
 *
 * Decompile:
 * - `i4` = [CameraCaptureSession.capture] (one-shot)
 * - `j4` = [CameraCaptureSession.setRepeatingRequest]
 * - L6 sets [CONTROL_AE_PRECAPTURE_TRIGGER] START on the **preview builder**, then `i4`;
 *   repeating continues with the same builder state via subsequent `j4` updates.
 *
 * Prefer this over **stopRepeating-then-one-shot** (older P&S path) — OP13 midtones improved;
 * CPH2583 USB 2026-07-13: capture OK, mosaic healthy (`frac&lt;bl≈0`).
 *
 * Full PS01 bisect extras (skip AE_LOCK / skip ASN / map ON) stay behind
 * [DngSaveBisectState.useProShotCapturePipeline] only.
 */
object ProShotStyleAePrecapture {
    private const val TAG = "PNS.ProShotPipeline"
    private const val MAX_WAIT_MS = 1200L

    fun shouldRun(chars: CameraCharacteristics): Boolean {
        val keys = chars.availableCaptureRequestKeys ?: return false
        if (!keys.contains(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)) return false
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }

    /**
     * Caller must **not** have called [CameraCaptureSession.stopRepeating] yet.
     * On complete, caller stops repeating then fires [TEMPLATE_STILL_CAPTURE].
     */
    fun runWhileRepeating(
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
        Log.i(TAG, "aePrecapture begin mode=repeating+capture (ProShot L6/i4/j4) maxWaitMs=$MAX_WAIT_MS")
        val finished = AtomicBoolean(false)
        var lastResult: TotalCaptureResult? = null

        fun completeOnce(source: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(
                TAG,
                "aePrecapture done source=$source iso=${lastResult?.get(CaptureResult.SENSOR_SENSITIVITY)} " +
                    "ae=${lastResult?.get(CaptureResult.CONTROL_AE_STATE)}",
            )
            try {
                val cancelReq =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        configurePreviewLikeStill()
                        set(
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL,
                        )
                    }.build()
                val idleReq =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        configurePreviewLikeStill()
                        set(
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
                        )
                    }.build()
                // ProShot f6: i4(cancel) then j4(repeating idle)
                session.capture(
                    cancelReq,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult,
                        ) {
                            lastResult = result
                            runCatching {
                                session.setRepeatingRequest(
                                    idleReq,
                                    object : CameraCaptureSession.CaptureCallback() {},
                                    bgHandler,
                                )
                            }
                            onComplete(lastResult)
                        }

                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure,
                        ) {
                            Log.w(TAG, "aePrecapture cancel failed reason=${failure.reason}")
                            onComplete(lastResult)
                        }
                    },
                    bgHandler,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "aePrecapture cancel failed err=${t.message}")
                onComplete(lastResult)
            }
        }

        fun aeReady(result: TotalCaptureResult): Boolean {
            val ae = result.get(CaptureResult.CONTROL_AE_STATE)
            return ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                ae == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
        }

        val watchCb =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    lastResult = result
                    if (aeReady(result)) {
                        completeOnce("repeatingOrCapture")
                    }
                }
            }

        try {
            val startBuilder =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    configurePreviewLikeStill()
                    set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                    )
                }
            val startReq = startBuilder.build()
            // ProShot j4: keep repeating with START on the preview builder.
            session.setRepeatingRequest(startReq, watchCb, bgHandler)
            // ProShot i4: also one-shot the same request.
            session.capture(startReq, watchCb, bgHandler)
        } catch (t: Throwable) {
            Log.w(TAG, "aePrecapture start failed err=${t.message}")
            onComplete(lastResult)
            return
        }

        bgHandler.postDelayed(
            {
                if (!finished.get()) {
                    Log.i(TAG, "aePrecapture timeout proceedStill=true lastAe=${lastResult?.get(CaptureResult.CONTROL_AE_STATE)}")
                    completeOnce("timeout")
                }
            },
            MAX_WAIT_MS,
        )
    }
}
