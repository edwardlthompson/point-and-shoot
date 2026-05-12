package dev.pointandshoot

import android.annotation.SuppressLint
import android.content.Context
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.ExtSessionSmoke"

private const val OPEN_CAMERA_AWAIT_SEC = 8L
private const val EXT_SESSION_CONFIGURE_AWAIT_SEC = 12L
private const val FALLBACK_PREVIEW_WIDTH = 1280
private const val FALLBACK_PREVIEW_HEIGHT = 720

/**
 * One-shot **`CameraDevice.createExtensionSession`** smoke (Milestone 4): opens the first back camera,
 * picks a supported extension (prefers **HDR** then **NIGHT**), builds a minimal preview
 * [OutputConfiguration], and logs **`PNS.AdbValidation`** **`cameraExtensionSession …`** then closes.
 */
@SuppressLint("NewApi")
object CameraExtensionSessionSmokeRunner {

    fun runBlocking(activity: ComponentActivity, preferredCameraId: String?, finishActivityWhenDone: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PnsAdbLog.i(activity, "cameraExtensionSession skipped api=${Build.VERSION.SDK_INT}")
            if (finishActivityWhenDone) activity.finish()
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
            PnsAdbLog.i(activity, "cameraExtensionSession skipped no_camera_id")
            if (finishActivityWhenDone) activity.finish()
            return
        }
        val extIds = CameraExtensionSupport.supportedExtensionInts(cm, cameraId)
        if (extIds.isEmpty()) {
            PnsAdbLog.i(activity, "cameraExtensionSession skipped cameraId=$cameraId extensions=(none)")
            if (finishActivityWhenDone) activity.finish()
            return
        }
        val pick =
            extIds.firstOrNull { it == CameraExtensionCharacteristics.EXTENSION_HDR }
                ?: extIds.firstOrNull { it == CameraExtensionCharacteristics.EXTENSION_NIGHT }
                ?: extIds[0]

        val ht = HandlerThread("PNS.ExtSmoke")
        ht.start()
        val h = Handler(ht.looper)
        val exec = Executor { cmd -> h.post(cmd) }
        val openLatch = CountDownLatch(1)
        val deviceRef = AtomicReference<CameraDevice?>(null)
        val openError = AtomicReference<Throwable?>(null)
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
            if (!openLatch.await(OPEN_CAMERA_AWAIT_SEC, TimeUnit.SECONDS)) {
                PnsAdbLog.i(activity, "cameraExtensionSession skipped cameraId=$cameraId open_timeout")
                if (finishActivityWhenDone) activity.finish()
                return
            }
            openError.get()?.let { e ->
                PnsAdbLog.i(activity, "cameraExtensionSession skipped cameraId=$cameraId open_err=${e.message}")
                if (finishActivityWhenDone) activity.finish()
                return
            }
            val device = deviceRef.get() ?: run {
                PnsAdbLog.i(activity, "cameraExtensionSession skipped cameraId=$cameraId device_null")
                if (finishActivityWhenDone) activity.finish()
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
            var configureOk = false
            val oc = OutputConfiguration(surf)
            val extCfg =
                ExtensionSessionConfiguration(
                    pick,
                    listOf(oc),
                    exec,
                    object : CameraExtensionSession.StateCallback() {
                        override fun onConfigured(session: CameraExtensionSession) {
                            configureOk = true
                            val extLabel = CameraExtensionSupport.extensionLabel(pick)
                            PnsAdbLog.i(
                                activity,
                                "cameraExtensionSession cameraId=$cameraId extension=$pick " +
                                    "label=$extLabel onConfigured=true",
                            )
                            runCatching { session.close() }
                            configuredLatch.countDown()
                        }

                        override fun onConfigureFailed(session: CameraExtensionSession) {
                            PnsAdbLog.i(
                                activity,
                                "cameraExtensionSession cameraId=$cameraId extension=$pick onConfigured=false",
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
                PnsAdbLog.i(
                    activity,
                    "cameraExtensionSession cameraId=$cameraId extension=$pick createThrows=${createErr::class.java.simpleName}",
                )
            } else {
                configuredLatch.await(EXT_SESSION_CONFIGURE_AWAIT_SEC, TimeUnit.SECONDS)
                if (!configureOk) {
                    PnsAdbLog.i(activity, "cameraExtensionSession cameraId=$cameraId extension=$pick configure_timeout")
                }
            }
            runCatching { surf.release() }
            runCatching { st.release() }
            runCatching { device.close() }
        } catch (e: Throwable) {
            Log.w(TAG, "extension smoke failed", e)
            PnsAdbLog.i(activity, "cameraExtensionSession failed err=${e::class.java.simpleName}")
        } finally {
            ht.quitSafely()
        }
        if (finishActivityWhenDone) {
            activity.finish()
        }
    }
}
