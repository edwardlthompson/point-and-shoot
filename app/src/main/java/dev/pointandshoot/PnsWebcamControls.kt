@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.util.Log

/**
 * UVC-style webcam controls over HTTP (Zoom/Teams only see these if the OEM
 * UVC gadget is active). P&S applies the same names to Camera2 while MJPEG is live.
 */
object PnsWebcamControls {
    const val TAG: String = "PNS.WebcamCtl"

    @Volatile
    var cameraId: String = ""

    @Volatile
    var camerasCsv: String = ""

    @Volatile
    var facing: String = ""

    @Volatile
    var afMode: String = "continuous"

    @Volatile
    var faceCount: Int = 0

    @Volatile
    var tracking: Boolean = false

    @Volatile
    var depthAvailable: Boolean = false

    @Volatile
    var evBias: Int = 0

    @Volatile
    var zoom: Float = 1f

    @Volatile
    var onSwitchCamera: ((String) -> Unit)? = null

    @Volatile
    var onAfMode: ((String) -> Unit)? = null

    @Volatile
    var onEv: ((Int) -> Unit)? = null

    @Volatile
    var onZoom: ((Float) -> Unit)? = null

    fun applyQuery(query: Map<String, String>): String {
        var applied = 0
        query["camera"]?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
            onSwitchCamera?.invoke(id)
            cameraId = id
            applied++
        }
        query["focus_auto"]?.let { raw ->
            val mode = if (raw == "0" || raw.equals("off", true)) "manual" else "continuous"
            afMode = mode
            onAfMode?.invoke(mode)
            applied++
        }
        query["ae_ev"]?.toIntOrNull()?.let { ev ->
            evBias = ev.coerceIn(-12, 12)
            onEv?.invoke(evBias)
            applied++
        }
        query["zoom"]?.toFloatOrNull()?.let { z ->
            zoom = z.coerceIn(1f, 10f)
            onZoom?.invoke(zoom)
            applied++
        }
        runCatching { Log.i(TAG, "apply n=$applied q=$query") }
        return statusJson()
    }

    fun statusJson(): String {
        val jpeg = PnsExternalOutput.latestJpeg?.size ?: 0
        return """{"ok":true,"camera":"$cameraId","cameras":"$camerasCsv","facing":"$facing",""" +
            """"af":"$afMode","faces":$faceCount,"tracking":$tracking,"depth":$depthAvailable,""" +
            """"ev":$evBias,"zoom":$zoom,"copy":"${PnsExternalOutput.lastCopyWidth}x${PnsExternalOutput.lastCopyHeight}",""" +
            """"copyMs":${PnsExternalOutput.lastCopyMs},"fps":${PnsExternalOutput.achievedFps},""" +
            """"jpegBytes":$jpeg,"encode":"${PnsWebcamEncoder.tier?.name ?: "off"}",""" +
            """"encodeWxH":"${PnsWebcamEncoder.tier?.width ?: 0}x${PnsWebcamEncoder.tier?.height ?: 0}",""" +
            """"encodeFps":${PnsWebcamEncoder.achievedFps},"encodeTargetFps":${PnsWebcamEncoder.tier?.fps ?: 0},""" +
            """"h264":"/h264","uvcOem":${PnsUsbWebcam.link.uvc},"usb":"${PnsUsbWebcam.statusLine()}",""" +
            """"windowsDevice":"${PnsUsbWebcam.WINDOWS_DEVICE_NAME}","windowsDriver":"${PnsUsbWebcam.WINDOWS_INBOX_DRIVER}"}"""
    }
}
