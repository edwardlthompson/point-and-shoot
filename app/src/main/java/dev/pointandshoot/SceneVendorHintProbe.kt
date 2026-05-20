package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.util.Log

/**
 * Sprint **13V.17** — read-only scan for OEM scene / quality vendor keys on each camera.
 *
 * No capture behavior is driven from these hints yet; results feed HUD readout and
 * `PNS.SceneHint` logs for fleet triage (EVA / `media_quality` often absent on LineageOS).
 */
object SceneVendorHintProbe {

    private const val TAG = "PNS.SceneHint"

  private val KEY_SUBSTRINGS = listOf(
        "media_quality",
        "scene",
        "eva",
        "ais",
        "ai_scene",
        "smart",
    )

    data class CameraSceneHints(
        val cameraId: String,
        val requestKeys: List<String>,
        val resultKeys: List<String>,
    )

    data class SceneHintMatrix(
        val perCamera: List<CameraSceneHints>,
    ) {
        fun summaryForReadout(): String? {
            val hits = perCamera.flatMap { cam ->
                (cam.requestKeys + cam.resultKeys).distinct().take(3)
            }.distinct()
            if (hits.isEmpty()) return null
            return hits.joinToString(" · ") { it.substringAfterLast('.').take(24) }
        }
    }

    @Volatile
    var cached: SceneHintMatrix? = null
        private set

    fun probe(context: Context) {
        if (cached != null) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val perCamera = mutableListOf<CameraSceneHints>()
        for (cameraId in runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())) {
            val chars = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull() ?: continue
            val req = filterVendorishRequestKeys(chars.availableCaptureRequestKeys)
            val res = filterVendorishResultKeys(chars.availableCaptureResultKeys)
            if (req.isNotEmpty() || res.isNotEmpty()) {
                perCamera.add(CameraSceneHints(cameraId, req, res))
            }
        }
        cached = SceneHintMatrix(perCamera)
        val summary = cached?.summaryForReadout() ?: "none"
        Log.i(TAG, "sceneHintProbeComplete cameras=${perCamera.size} summary=$summary")
        perCamera.forEach { cam ->
            Log.i(
                TAG,
                "sceneHint cam=${cam.cameraId} req=${cam.requestKeys.size} res=${cam.resultKeys.size} " +
                    "keys=${(cam.requestKeys + cam.resultKeys).distinct().take(6).joinToString()}",
            )
        }
    }

    private fun filterVendorishRequestKeys(keys: List<CaptureRequest.Key<*>>?): List<String> {
        if (keys.isNullOrEmpty()) return emptyList()
        return keys.map { it.name }
            .filter { name ->
                KEY_SUBSTRINGS.any { sub -> name.contains(sub, ignoreCase = true) }
            }
            .distinct()
            .sorted()
    }

    private fun filterVendorishResultKeys(keys: List<CaptureResult.Key<*>>?): List<String> {
        if (keys.isNullOrEmpty()) return emptyList()
        return keys.map { it.name }
            .filter { name ->
                KEY_SUBSTRINGS.any { sub -> name.contains(sub, ignoreCase = true) }
            }
            .distinct()
            .sorted()
    }
}
