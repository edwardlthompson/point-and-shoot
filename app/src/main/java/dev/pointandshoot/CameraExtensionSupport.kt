package dev.pointandshoot

import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

private const val TAG = "PNS.CameraExt"

/**
 * Camera2 **extensions** inventory (`CameraExtensionCharacteristics`) for Milestone 4 / probe export.
 * Session creation is exercised separately by [CameraExtensionSessionSmokeRunner].
 */
object CameraExtensionSupport {

    fun supportedExtensionInts(cm: CameraManager, cameraId: String): IntArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return intArrayOf()
        val raw =
            runCatching {
                cm.getCameraExtensionCharacteristics(cameraId).supportedExtensions
            }.getOrElse { e ->
                Log.w(TAG, "getCameraExtensionCharacteristics failed: ${e.message}")
                return intArrayOf()
            }
        @Suppress("UNCHECKED_CAST")
        return raw as? IntArray ?: intArrayOf()
    }

    /** Human-readable labels for probe markdown (comma-separated). */
    fun formatExtensionLabels(ids: IntArray): String {
        if (ids.isEmpty()) return "(none)"
        return ids.joinToString { extensionLabel(it) }
    }

    fun markdownLinesForCamera(cm: CameraManager, cameraId: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "- cameraExtensions: (requires API 31+)"
        }
        val ids = supportedExtensionInts(cm, cameraId)
        val labels = formatExtensionLabels(ids)
        return buildString {
            append("- cameraExtensionIds: ")
            appendLine(if (ids.isEmpty()) "(none)" else ids.joinToString(prefix = "[", postfix = "]"))
            append("- cameraExtensionLabels: ")
            appendLine(labels)
        }
    }

    internal fun extensionLabel(id: Int): String =
        when (id) {
            CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> "AUTOMATIC"
            CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH -> "FACE_RETOUCH"
            CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
            CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
            CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
            else -> "EXT_$id"
        }
}
