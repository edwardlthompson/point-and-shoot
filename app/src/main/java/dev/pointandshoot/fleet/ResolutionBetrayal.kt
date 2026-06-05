package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import dev.pointandshoot.FleetCameraStartupScan
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Measures how often the HAL exposes higher-resolution stream maps than the default Camera2 path.
 * Higher [index] = more rear cameras with hidden / alternate-map high resolution (0–100).
 */
object ResolutionBetrayal {
    private const val MP_RATIO_THRESHOLD = 1.25

    fun computeIndex(
        entries: List<FleetCameraStartupScan.StillResolutionAdvertisedEntry>,
        cm: CameraManager,
    ): Int {
        val rear = entries.filter { isRearCamera(cm, it.cameraId) }
        if (rear.isEmpty()) return 0
        val betrayed = rear.count { isBetrayed(it) }
        return ((betrayed * 100.0) / rear.size).roundToInt().coerceIn(0, 100)
    }

    fun computeFromMatrix(matrix: JSONObject): Int {
        val product = matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT) ?: return 0
        val arr = product.optJSONArray("stillResolutionAdvertised") ?: return 0
        if (arr.length() == 0) return 0
        val specMpByCamera = focalMegapixelsByCameraId(product.optJSONArray("focalSlots"))
        var total = 0
        var betrayed = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            total++
            if (
                o.optBoolean("hasLargerThanDefault", false) ||
                    jsonBetrayedByRatio(o) ||
                    specBetrayed(o, specMpByCamera)
            ) {
                betrayed++
            }
        }
        if (total == 0) return 0
        return ((betrayed * 100.0) / total).roundToInt().coerceIn(0, 100)
    }

    fun toSummaryJson(
        entries: List<FleetCameraStartupScan.StillResolutionAdvertisedEntry>,
        cm: CameraManager,
        index: Int,
    ): JSONObject =
        JSONObject().apply {
            put("index", index)
            put("rearCameraCount", entries.count { isRearCamera(cm, it.cameraId) })
            put(
                "cameras",
                JSONArray().apply {
                    entries.filter { isRearCamera(cm, it.cameraId) }.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("cameraId", e.cameraId)
                                put("hasLargerThanDefault", e.hasLargerThanDefault)
                                put("defaultJpegMp", mp(e.defaultJpeg))
                                put("maxAdvertisedJpegMp", maxMp(e))
                                put("betrayed", isBetrayed(e))
                            },
                        )
                    }
                },
            )
        }

    private fun isRearCamera(cm: CameraManager, cameraId: String): Boolean {
        val facing =
            runCatching {
                cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull()
        return facing == CameraCharacteristics.LENS_FACING_BACK
    }

    private fun isBetrayed(entry: FleetCameraStartupScan.StillResolutionAdvertisedEntry): Boolean =
        entry.hasLargerThanDefault || ratioBetrayed(entry)

    private fun ratioBetrayed(entry: FleetCameraStartupScan.StillResolutionAdvertisedEntry): Boolean {
        val default = mp(entry.defaultJpeg).coerceAtLeast(mp(entry.defaultRawSensor))
        val max = maxMp(entry)
        if (default <= 0.0 || max <= 0.0) return false
        return max / default >= MP_RATIO_THRESHOLD
    }

    private fun jsonBetrayedByRatio(o: JSONObject): Boolean {
        val default = defaultMpFromJson(o)
        val max =
            listOf(
                o.optDouble("maxAdvertisedJpegMp", 0.0),
                o.optDouble("highResJpegMp", 0.0),
                o.optDouble("maxResMapJpegMp", 0.0),
                mpFromSizeJson(o.optJSONObject("highResJpeg")),
                mpFromSizeJson(o.optJSONObject("maxResMapJpeg")),
                mpFromSizeJson(o.optJSONObject("multiResJpeg")),
                mpFromSizeJson(o.optJSONObject("highResRawSensor")),
                mpFromSizeJson(o.optJSONObject("maxResMapRawSensor")),
            ).maxOrNull() ?: 0.0
        if (default <= 0.0 || max <= 0.0) return false
        return max / default >= MP_RATIO_THRESHOLD
    }

    private fun specBetrayed(o: JSONObject, specMpByCamera: Map<String, Double>): Boolean {
        val spec = specMpByCamera[o.optString("cameraId")] ?: return false
        val default = defaultMpFromJson(o)
        if (default <= 0.0 || spec <= 0.0) return false
        return spec / default >= MP_RATIO_THRESHOLD
    }

    private fun focalMegapixelsByCameraId(focalSlots: JSONArray?): Map<String, Double> {
        if (focalSlots == null || focalSlots.length() == 0) return emptyMap()
        return buildMap {
            for (i in 0 until focalSlots.length()) {
                val slot = focalSlots.optJSONObject(i) ?: continue
                val id = slot.optString("cameraId", "")
                val mp = slot.optDouble("megapixels", Double.NaN)
                if (id.isNotBlank() && mp.isFinite() && mp > 0.0) {
                    put(id, mp)
                }
            }
        }
    }

    private fun defaultMpFromJson(o: JSONObject): Double =
        listOf(
            o.optDouble("defaultJpegMp", 0.0),
            mpFromSizeJson(o.optJSONObject("defaultJpeg")),
            mpFromSizeJson(o.optJSONObject("defaultRawSensor")),
        ).maxOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun mpFromSizeJson(size: JSONObject?): Double {
        if (size == null) return 0.0
        val mp = size.optDouble("mp", Double.NaN)
        if (mp.isFinite() && mp > 0.0) return mp
        val w = size.optInt("width", 0)
        val h = size.optInt("height", 0)
        if (w <= 0 || h <= 0) return 0.0
        return (w.toLong() * h.toLong()) / 1_000_000.0
    }

    private fun maxMp(entry: FleetCameraStartupScan.StillResolutionAdvertisedEntry): Double =
        listOf(
            mp(entry.highResJpeg),
            mp(entry.maxResMapJpeg),
            mp(entry.multiResJpeg),
            mp(entry.highResRawSensor),
            mp(entry.maxResMapRawSensor),
        ).maxOrNull() ?: 0.0

    private fun mp(size: Size?): Double {
        if (size == null || size.width <= 0 || size.height <= 0) return 0.0
        return (size.width.toLong() * size.height.toLong()) / 1_000_000.0
    }
}
