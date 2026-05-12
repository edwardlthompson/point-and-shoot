package dev.pointandshoot

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sprint **10.1** — shallow, session-free camera capability snapshot for fleet / probe export.
 * Markdown embed lives in [buildProbeReport]; persistence is a later **[MIXED]** row.
 */
object DeviceCameraCapabilityCache {
    const val SCHEMA_VERSION: Int = 1

    private const val FHD_LANDSCAPE_W = 1920
    private const val FHD_LANDSCAPE_H = 1080
    private const val HD_LANDSCAPE_W = 1280
    private const val HD_LANDSCAPE_H = 720
    private const val FINGERPRINT_PREFIX_DEFAULT_LEN = 16
    private const val FINGERPRINT_PREFIX_MIN_LEN = 8
    private const val FINGERPRINT_PREFIX_MAX_LEN = 64

    fun fingerprintSha256Prefix(fingerprint: String, prefixLen: Int = FINGERPRINT_PREFIX_DEFAULT_LEN): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
            .take(prefixLen.coerceIn(FINGERPRINT_PREFIX_MIN_LEN, FINGERPRINT_PREFIX_MAX_LEN))
    }

    fun largestSizeWxH(map: StreamConfigurationMap?, format: Int): String? {
        if (map == null) return null
        val sizes = runCatching { map.getOutputSizes(format)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null
        val s = sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
        return "${s.width}x${s.height}"
    }

    /**
     * Max high-speed **upper** FPS: all advertised sizes, at **1080p** (1920×1080 or 1080×1920),
     * and at **720p** (1280×720 or 720×1280). Uses [StreamConfigurationMap.getHighSpeedVideoFpsRangesFor] only.
     */
    fun hfrMaxAtSizeClasses(map: StreamConfigurationMap?): Triple<Int?, Int?, Int?> {
        if (map == null) return Triple(null, null, null)
        val sizes = runCatching { map.highSpeedVideoSizes }.getOrNull() ?: return Triple(null, null, null)
        var maxAll = 0
        var max1080 = 0
        var max720 = 0
        fun is1080(s: Size): Boolean =
            (s.width == FHD_LANDSCAPE_W && s.height == FHD_LANDSCAPE_H) ||
                (s.width == FHD_LANDSCAPE_H && s.height == FHD_LANDSCAPE_W)
        fun is720(s: Size): Boolean =
            (s.width == HD_LANDSCAPE_W && s.height == HD_LANDSCAPE_H) ||
                (s.width == HD_LANDSCAPE_H && s.height == HD_LANDSCAPE_W)
        for (s in sizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
            for (r in ranges) {
                val u = r.upper
                if (u > maxAll) maxAll = u
                if (is1080(s) && u > max1080) max1080 = u
                if (is720(s) && u > max720) max720 = u
            }
        }
        if (maxAll == 0) return Triple(null, null, null)
        return Triple(
            maxAll,
            if (max1080 > 0) max1080 else null,
            if (max720 > 0) max720 else null,
        )
    }

    fun hfrRollupMarkdownLine(map: StreamConfigurationMap?): String {
        val (a, b, c) = hfrMaxAtSizeClasses(map)
        return "- hfrRollup maxFps=${a ?: "null"} maxFpsAt1080=${b ?: "null"} maxFpsAt720=${c ?: "null"}"
    }

    fun cameraJson(id: String, cc: CameraCharacteristics, map: StreamConfigurationMap?): JSONObject {
        val facing =
            when (cc.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
        val physical = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
        val focal = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focalArr =
            JSONArray().apply {
                focal?.forEach { put(it.toDouble()) }
            }
        val zoomMax = cc.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        val (hfrAll, hfr1080, hfr720) = hfrMaxAtSizeClasses(map)
        val rawPick = RawCaptureSupport.pickRawOutput(cc)
        return JSONObject().apply {
            put("cameraId", id)
            put("lensFacing", facing)
            put("physicalCameraIds", JSONArray(physical))
            if (focal == null || focal.isEmpty()) {
                put("focalLengthsMm", JSONObject.NULL)
            } else {
                put("focalLengthsMm", focalArr)
            }
            put("zoomDigitalMax", zoomMax?.toDouble() ?: JSONObject.NULL)
            put("largestJpeg", largestSizeWxH(map, ImageFormat.JPEG) ?: JSONObject.NULL)
            put("largestRaw12", largestSizeWxH(map, ImageFormat.RAW12) ?: JSONObject.NULL)
            put("largestRaw10", largestSizeWxH(map, ImageFormat.RAW10) ?: JSONObject.NULL)
            put("largestRawSensor", largestSizeWxH(map, ImageFormat.RAW_SENSOR) ?: JSONObject.NULL)
            put("rawPickEffective", RawCaptureSupport.rawPickEffectiveLabel(rawPick?.first))
            put("rawPickSize", rawPick?.second?.let { "${it.width}x${it.height}" } ?: JSONObject.NULL)
            put("hfrMaxFps", hfrAll ?: JSONObject.NULL)
            put("hfrMaxFpsAt1080", hfr1080 ?: JSONObject.NULL)
            put("hfrMaxFpsAt720", hfr720 ?: JSONObject.NULL)
        }
    }

    fun buildRoot(context: Context, cameras: JSONArray, degraded: Boolean): JSONObject {
        val pInfo =
            runCatching {
                val pm: PackageManager = context.packageManager
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode?.toLong()
            }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("appVersionCode", versionCode ?: JSONObject.NULL)
            put("androidSdk", Build.VERSION.SDK_INT)
            put("fingerprintSha256Prefix", fingerprintSha256Prefix(Build.FINGERPRINT))
            put("degraded", degraded)
            put("cameras", cameras)
        }
    }

    fun appendMarkdownJsonBlock(sb: StringBuilder, root: JSONObject) {
        sb.appendLine("### Shallow fleet capability cache (Sprint 10.1 JSON)")
        sb.appendLine()
        sb.appendLine("```json")
        sb.appendLine(root.toString(2))
        sb.appendLine("```")
        sb.appendLine()
    }
}
