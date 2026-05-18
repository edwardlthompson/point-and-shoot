package dev.pointandshoot

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Sprint 13.18 — CameraX OEM ISP extension probe.
 *
 * Queries [ExtensionsManager] at app start to discover which OEM-ISP modes
 * (Night, Bokeh, HDR, Face Retouch, Auto) are available per camera.
 *
 * On LineageOS and AOSP builds the result is typically empty — all modes
 * return [ExtensionsManager.isExtensionAvailable] = false. The probe
 * completing successfully is what matters; the UI hides Night/Bokeh dial
 * segments automatically when the mode is unavailable for the selected camera.
 *
 * Thread safety: [cached] is written once from a background thread and read
 * from any thread thereafter. All public read methods are safe to call before
 * [probe] completes — they return empty/false defensively.
 */
object CameraXExtensionProbe {

    private const val TAG = "PNS.CamXExtProbe"

    /**
     * Maps a human-readable name to an [ExtensionMode] constant for logging.
     */
    private val ALL_MODES = listOf(
        "NIGHT" to ExtensionMode.NIGHT,
        "BOKEH" to ExtensionMode.BOKEH,
        "HDR" to ExtensionMode.HDR,
        "FACE_RETOUCH" to ExtensionMode.FACE_RETOUCH,
        "AUTO" to ExtensionMode.AUTO,
    )

    /** Immutable result written by [probe]; null until the probe completes. */
    @Volatile
    var cached: ExtensionMatrix? = null
        private set

    /**
     * Per-camera map of available [ExtensionMode] values.
     *
     * @param availableByCamera camera ID → list of available mode constants
     */
    data class ExtensionMatrix(
        val availableByCamera: Map<String, List<Int>>,
    ) {
        fun isAvailable(cameraId: String, mode: Int): Boolean =
            availableByCamera[cameraId]?.contains(mode) == true

        fun availableModesFor(cameraId: String): List<Int> =
            availableByCamera[cameraId] ?: emptyList()

        fun hasAny(): Boolean = availableByCamera.values.any { it.isNotEmpty() }

        fun summary(): String = buildString {
            if (availableByCamera.isEmpty()) {
                append("noExtensions=true")
                return@buildString
            }
            availableByCamera.entries.forEachIndexed { i, (id, modes) ->
                if (i > 0) append(" ")
                append("cam$id=[")
                append(modes.joinToString(",") { m ->
                    ALL_MODES.firstOrNull { it.second == m }?.first ?: m.toString()
                })
                append("]")
            }
        }
    }

    /** True if [mode] is available for [cameraId]; safe to call before probe completes (returns false). */
    fun isAvailable(cameraId: String, mode: Int): Boolean =
        cached?.isAvailable(cameraId, mode) == true

    /** All available modes for [cameraId]; empty if probe not yet complete or no extensions on device. */
    fun availableModesFor(cameraId: String): List<Int> =
        cached?.availableModesFor(cameraId) ?: emptyList()

    /** True if any extension is available on any camera. False until probe completes. */
    fun hasAny(): Boolean = cached?.hasAny() == true

    /**
     * Run the extension probe synchronously on the calling (background) thread.
     *
     * Uses [ProcessCameraProvider.getInstance] + [ExtensionsManager.getInstanceAsync],
     * both resolved synchronously via [ListenableFuture.get] with a 10 s timeout.
     *
     * Safe to call multiple times — returns early if [cached] is already set.
     */
    @Suppress("TooGenericExceptionCaught")
    fun probe(context: Context) {
        if (cached != null) return
        val appContext = context.applicationContext
        try {
            val providerFuture = ProcessCameraProvider.getInstance(appContext)
            val provider: ProcessCameraProvider = providerFuture.get(10L, TimeUnit.SECONDS)

            val extManagerFuture = ExtensionsManager.getInstanceAsync(
                appContext,
                provider,
            )
            val extManager = extManagerFuture.get(10L, TimeUnit.SECONDS)

            val cameraIds = runCatching {
                val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cm.cameraIdList.toList()
            }.getOrDefault(listOf("0", "1"))

            val availableByCamera = mutableMapOf<String, List<Int>>()

            for (cameraId in cameraIds) {
                val facing = runCatching {
                    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val chars = cm.getCameraCharacteristics(cameraId)
                    when (chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)) {
                        android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT ->
                            CameraSelector.LENS_FACING_FRONT
                        else -> CameraSelector.LENS_FACING_BACK
                    }
                }.getOrDefault(CameraSelector.LENS_FACING_BACK)

                val selector = CameraSelector.Builder()
                    .requireLensFacing(facing)
                    .build()

                val available = ALL_MODES.mapNotNull { (_, mode) ->
                    runCatching {
                        if (extManager.isExtensionAvailable(selector, mode)) mode else null
                    }.getOrNull()
                }
                if (available.isNotEmpty()) {
                    availableByCamera[cameraId] = available
                }
            }

            val matrix = ExtensionMatrix(availableByCamera.toMap())
            cached = matrix

            val summary = matrix.summary()
            Log.i(TAG, "extensionProbeComplete ${summary.ifEmpty { "noExtensions=true" }}")
            if (matrix.hasAny()) {
                Log.i(TAG, "extensionAvail=${summary}")
            } else {
                Log.i(TAG, "extensionAvail=none (OEM extensions not available on this device/ROM)")
            }

            runCatching { ContextCompat.getMainExecutor(appContext).execute { provider.unbindAll() } }
        } catch (e: Exception) {
            Log.w(TAG, "extension probe failed: ${e::class.java.simpleName}: ${e.message}")
            cached = ExtensionMatrix(emptyMap())
        }
    }
}
