package dev.pointandshoot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraExtensionSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.ExtHandoff"

private const val EXTERNAL_CAMERA_APP_AWAIT_SEC = 8L
private const val EXT_SESSION_CONFIGURE_AWAIT_SEC = 12L
private const val FALLBACK_PREVIEW_WIDTH = 1280
private const val FALLBACK_PREVIEW_HEIGHT = 720

/**
 * Sprint **28.2** — isolated HDR [CameraExtensionSession] configure, then optional cold handoff back to
 * [PNS_SCREEN_PREVIEW] (no inline merge into [PreviewEngineScreen] Camera2 session).
 */
@SuppressLint("NewApi")
object ExtensionHandoffSpikeRunner {

    fun runBlocking(
        activity: ComponentActivity,
        preferredCameraId: String?,
        returnToPreview: Boolean,
        finishActivityWhenDone: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            logHandoff(activity, ok = false, reason = "api_lt_31", returnToPreview = returnToPreview)
            finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            return
        }
        val cm = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId =
            preferredCameraId?.trim()?.takeIf { it.isNotEmpty() }
                ?: cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                }
                ?: cm.cameraIdList.firstOrNull()
        if (cameraId == null) {
            logHandoff(activity, ok = false, reason = "no_camera_id", returnToPreview = returnToPreview)
            finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            return
        }
        val extIds = CameraExtensionSupport.supportedExtensionInts(cm, cameraId)
        if (extIds.isEmpty()) {
            logHandoff(
                activity,
                ok = false,
                reason = "no_extensions",
                cameraId = cameraId,
                returnToPreview = returnToPreview,
            )
            if (returnToPreview) {
                handoffToPreview(activity)
            } else {
                finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            }
            return
        }
        val pick =
            extIds.firstOrNull { it == CameraExtensionCharacteristics.EXTENSION_HDR }
                ?: extIds.firstOrNull { it == CameraExtensionCharacteristics.EXTENSION_NIGHT }
                ?: extIds[0]

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            logHandoff(
                activity,
                ok = false,
                reason = "no_camera_permission",
                cameraId = cameraId,
                extension = pick,
                returnToPreview = returnToPreview,
            )
            finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            return
        }

        val ht = HandlerThread("PNS.ExtHandoff")
        ht.start()
        val h = Handler(ht.looper)
        val exec = Executor { cmd -> h.post(cmd) }
        val openLatch = CountDownLatch(1)
        val deviceRef = AtomicReference<CameraDevice?>(null)
        val openError = AtomicReference<Throwable?>(null)
        var configureOk = false
        try {
            cm.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        deviceRef.set(device)
                        openLatch.countDown()
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        runCatching { device.close() }
                        openLatch.countDown()
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        runCatching { device.close() }
                        openError.set(IllegalStateException("CameraDevice error=$error"))
                        openLatch.countDown()
                    }
                },
                h,
            )
            if (!openLatch.await(EXTERNAL_CAMERA_APP_AWAIT_SEC, TimeUnit.SECONDS)) {
                logHandoff(
                    activity,
                    ok = false,
                    reason = "open_timeout",
                    cameraId = cameraId,
                    extension = pick,
                    returnToPreview = returnToPreview,
                )
                finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
                return
            }
            openError.get()?.let { e ->
                logHandoff(
                    activity,
                    ok = false,
                    reason = "open_err=${e.message}",
                    cameraId = cameraId,
                    extension = pick,
                    returnToPreview = returnToPreview,
                )
                finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
                return
            }
            val device = deviceRef.get() ?: run {
                logHandoff(
                    activity,
                    ok = false,
                    reason = "device_null",
                    cameraId = cameraId,
                    extension = pick,
                    returnToPreview = returnToPreview,
                )
                finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
                return
            }

            val cc = cm.getCameraCharacteristics(cameraId)
            val map = cc.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSize: Size =
                map?.getOutputSizes(SurfaceTexture::class.java)
                    ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
                    ?: Size(FALLBACK_PREVIEW_WIDTH, FALLBACK_PREVIEW_HEIGHT)

            val st = SurfaceTexture(0)
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surf = Surface(st)
            val configuredLatch = CountDownLatch(1)
            val oc = OutputConfiguration(surf)
            val extCfg =
                ExtensionSessionConfiguration(
                    pick,
                    listOf(oc),
                    exec,
                    object : CameraExtensionSession.StateCallback() {
                        override fun onConfigured(session: CameraExtensionSession) {
                            configureOk = true
                            runCatching { session.close() }
                            configuredLatch.countDown()
                        }

                        override fun onConfigureFailed(session: CameraExtensionSession) {
                            logHandoff(
                                activity,
                                ok = false,
                                reason = "configure_failed",
                                cameraId = cameraId,
                                extension = pick,
                                returnToPreview = returnToPreview,
                            )
                            runCatching { session.close() }
                            configuredLatch.countDown()
                        }
                    },
                )
            val createErr =
                runCatching {
                    device.createExtensionSession(extCfg)
                }.exceptionOrNull()
            if (createErr != null) {
                Log.w(TAG, "createExtensionSession threw: ${createErr.message}")
                logHandoff(
                    activity,
                    ok = false,
                    reason = "createThrows=${createErr::class.java.simpleName}",
                    cameraId = cameraId,
                    extension = pick,
                    returnToPreview = returnToPreview,
                )
            } else {
                configuredLatch.await(EXT_SESSION_CONFIGURE_AWAIT_SEC, TimeUnit.SECONDS)
                if (!configureOk) {
                    logHandoff(
                        activity,
                        ok = false,
                        reason = "configure_timeout",
                        cameraId = cameraId,
                        extension = pick,
                        returnToPreview = returnToPreview,
                    )
                }
            }
            runCatching { surf.release() }
            runCatching { st.release() }
            runCatching { device.close() }
        } catch (e: Throwable) {
            Log.w(TAG, "extension handoff failed", e)
            logHandoff(
                activity,
                ok = false,
                reason = "err=${e::class.java.simpleName}",
                cameraId = cameraId,
                extension = pick,
                returnToPreview = returnToPreview,
            )
            finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            return
        } finally {
            ht.quitSafely()
        }

        if (!configureOk) {
            finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
            return
        }

        val extLabel = CameraExtensionSupport.extensionLabel(pick)
        logHandoff(
            activity,
            ok = true,
            cameraId = cameraId,
            extension = pick,
            label = extLabel,
            returnToPreview = returnToPreview,
        )
        if (returnToPreview) {
            handoffToPreview(activity)
            return
        }
        finishUnlessHandoff(activity, returnToPreview = false, finishActivityWhenDone)
    }

    private fun logHandoff(
        activity: ComponentActivity,
        ok: Boolean,
        reason: String? = null,
        cameraId: String? = null,
        extension: Int? = null,
        label: String? = null,
        returnToPreview: Boolean,
    ) {
        val parts = mutableListOf<String>()
        parts += "ok=$ok"
        cameraId?.let { parts += "cameraId=$it" }
        extension?.let { parts += "extension=$it" }
        label?.let { parts += "label=$it" }
        parts += "returnToPreview=$returnToPreview"
        reason?.let { parts += "reason=$it" }
        PnsAdbLog.i(activity, "extensionHandoff ${parts.joinToString(" ")}")
    }

    private fun handoffToPreview(activity: ComponentActivity) {
        val next =
            Intent(activity, activity.javaClass).apply {
                putExtra(EXTRA_PNS_SCREEN, PNS_SCREEN_PREVIEW)
                putExtra(EXTRA_PNS_AFTER_EXTENSION_HANDOFF, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK,
                )
            }
        PnsAdbLog.i(activity, "extensionHandoff launching preview return route=extensionhandoff")
        activity.startActivity(next)
        activity.finishAffinity()
    }

    private fun finishUnlessHandoff(
        activity: ComponentActivity,
        returnToPreview: Boolean,
        finishActivityWhenDone: Boolean,
    ) {
        if (returnToPreview) return
        if (finishActivityWhenDone) {
            activity.finish()
        }
    }
}
