package dev.pointandshoot.fleet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import dev.pointandshoot.SWEEP_SIGNAL_TAG
import dev.pointandshoot.buildSessionConfigurationCompat
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime session-configuration probe (Milestone **16.1**).
 *
 * Extracted from [dev.pointandshoot.SessionMatrixProbeScreen].
 */
object SessionMatrixProbeCore {
    private const val TAG = "PNS.SessionMatrix"

    data class CameraSessionResult(
        val cameraJson: JSONObject,
        val openCameraMs: Long?,
    )

    suspend fun probe(
        appContext: Context,
        onProgress: suspend (String) -> Unit = {},
    ): JSONObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onProgress("API < 28: SessionConfiguration not supported; skipping.")
            return JSONObject().apply {
                put("generatedAt", Instant.now().toString())
                put("note", "requires API 28+")
                put("cameras", JSONArray())
            }
        }

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        onProgress("Cameras: ${cameraIds.joinToString(",")} (${cameraIds.size})")

        val root =
            JSONObject().apply {
                put("generatedAt", Instant.now().toString())
                put("phase", 2)
                put("probe", "session_configuration_matrix")
                put(
                    "device",
                    JSONObject().apply {
                        put("manufacturer", Build.MANUFACTURER)
                        put("model", Build.MODEL)
                        put("sdkInt", Build.VERSION.SDK_INT)
                    },
                )
            }

        val camsOut = JSONArray()
        for ((idx, cameraId) in cameraIds.withIndex()) {
            onProgress("━━ Camera $cameraId (${idx + 1}/${cameraIds.size}) ━━")
            val result = probeCamera(cm, cameraId, onProgress)
            camsOut.put(result.cameraJson)
        }

        root.put("cameras", camsOut)
        return root
    }

    internal suspend fun probeCamera(
        cm: CameraManager,
        cameraId: String,
        onProgress: suspend (String) -> Unit = {},
    ): CameraSessionResult {
        val camJson = JSONObject().put("cameraId", cameraId)
        val testsArr = JSONArray()
        val cc = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull()
        if (cc == null) {
            camJson.put("error", "getCameraCharacteristics_failed")
            return CameraSessionResult(camJson, null)
        }

        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes =
            runCatching {
                map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())

        fun pickSize(targetW: Int, targetH: Int): Size? =
            previewSizes.minByOrNull { abs(it.width - targetW) + abs(it.height - targetH) }

        val regularCases = mutableListOf<Pair<String, Size>>()
        pickSize(640, 480)?.let { regularCases += "regular_640x480" to it }
        pickSize(1280, 720)?.let { regularCases += "regular_1280x720" to it }
        pickSize(1920, 1080)?.let { regularCases += "regular_1920x1080" to it }
        pickSize(3840, 2160)?.let { regularCases += "regular_3840x2160" to it }

        val hsSizes = runCatching { map?.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        val hsFirst = hsSizes.minByOrNull { it.width * it.height }

        val ht = HandlerThread("PNS.SessMx-$cameraId")
        ht.start()
        val h = Handler(ht.looper)
        var device: CameraDevice? = null
        var openMs: Long? = null
        try {
            val openLatch = CountDownLatch(1)
            val openStart = System.nanoTime()
            @SuppressLint("MissingPermission")
            cm.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(cd: CameraDevice) {
                        openMs = (System.nanoTime() - openStart) / 1_000_000L
                        device = cd
                        openLatch.countDown()
                    }

                    override fun onDisconnected(cd: CameraDevice) {
                        runCatching { cd.close() }
                        openLatch.countDown()
                    }

                    override fun onError(cd: CameraDevice, error: Int) {
                        runCatching { cd.close() }
                        openLatch.countDown()
                    }
                },
                h,
            )
            if (!openLatch.await(6, TimeUnit.SECONDS) || device == null) {
                camJson.put("openError", "timeout_or_failed")
                return CameraSessionResult(camJson, openMs)
            }
            val d = device!!
            if (openMs != null) {
                camJson.put("openCameraMs", openMs)
            }

            for ((name, sz) in regularCases) {
                val supported = testSessionSupport(d, SessionConfiguration.SESSION_REGULAR, listOf(sz))
                testsArr.put(
                    JSONObject().apply {
                        put("name", name)
                        put("sessionType", "REGULAR")
                        put("w", sz.width)
                        put("h", sz.height)
                        put("supported", supported)
                    },
                )
                Log.i(SWEEP_SIGNAL_TAG, "SESS_CFG cam=$cameraId $name REGULAR supported=$supported ${sz.width}x${sz.height}")
                onProgress("$cameraId $name REGULAR -> $supported")
            }

            if (hsFirst != null) {
                val supportedHs = testSessionSupport(d, SessionConfiguration.SESSION_HIGH_SPEED, listOf(hsFirst))
                testsArr.put(
                    JSONObject().apply {
                        put("name", "high_speed_first_advertised")
                        put("sessionType", "HIGH_SPEED")
                        put("w", hsFirst.width)
                        put("h", hsFirst.height)
                        put("supported", supportedHs)
                    },
                )
                Log.i(
                    SWEEP_SIGNAL_TAG,
                    "SESS_CFG cam=$cameraId high_speed_first HS supported=$supportedHs ${hsFirst.width}x${hsFirst.height}",
                )
                onProgress("$cameraId high_speed ${hsFirst.width}x${hsFirst.height} -> $supportedHs")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val maxMap = cc.get(SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                val maxJpeg =
                    maxMap?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                if (maxJpeg != null) {
                    val supportedMax = testSessionSupport(d, SessionConfiguration.SESSION_REGULAR, listOf(maxJpeg))
                    testsArr.put(
                        JSONObject().apply {
                            put("name", "max_resolution_map_jpeg")
                            put("sessionType", "REGULAR")
                            put("w", maxJpeg.width)
                            put("h", maxJpeg.height)
                            put("supported", supportedMax)
                        },
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "SESS_CFG cam=$cameraId max_resolution_map_jpeg supported=$supportedMax ${maxJpeg.width}x${maxJpeg.height}",
                    )
                    onProgress("$cameraId max_resolution_map ${maxJpeg.width}x${maxJpeg.height} -> $supportedMax")
                }
            }

            camJson.put("tests", testsArr)
            return CameraSessionResult(camJson, openMs)
        } finally {
            runCatching { device?.close() }
            runCatching { ht.quitSafely() }
        }
    }

    internal fun testSessionSupport(
        device: CameraDevice,
        sessionType: Int,
        sizes: List<Size>,
    ): Boolean {
        val holders = mutableListOf<Triple<SurfaceTexture, Surface, OutputConfiguration>>()
        return try {
            val outputs = mutableListOf<OutputConfiguration>()
            for (sz in sizes) {
                val st = SurfaceTexture(0)
                st.setDefaultBufferSize(sz.width, sz.height)
                val surf = Surface(st)
                val oc = OutputConfiguration(surf)
                holders += Triple(st, surf, oc)
                outputs += oc
            }
            val config = buildSessionConfigurationCompat(sessionType, outputs)
            device.isSessionConfigurationSupported(config)
        } catch (e: Throwable) {
            Log.w(TAG, "isSessionConfigurationSupported threw: ${e.message}")
            false
        } finally {
            for ((st, surf, _) in holders) {
                runCatching { surf.release() }
                runCatching { st.release() }
            }
        }
    }

    fun sessionTestSupported(sessionCam: JSONObject?, name: String): Boolean {
        val tests = sessionCam?.optJSONArray("tests") ?: return false
        for (i in 0 until tests.length()) {
            val t = tests.optJSONObject(i) ?: continue
            if (t.optString("name") == name) return t.optBoolean("supported", false)
        }
        return false
    }

    fun highSpeedSessionOk(sessionCam: JSONObject?): Boolean =
        sessionTestSupported(sessionCam, "high_speed_first_advertised")
}
