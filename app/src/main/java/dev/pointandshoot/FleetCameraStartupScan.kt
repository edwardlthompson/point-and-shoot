package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Sprint **15.13** — first-launch focal map from [CameraCharacteristics] (35 mm equiv + MP gate).
 */
object FleetCameraStartupScan {
    const val TAG = "PNS.FleetScan"
    private const val PREFS = "pns_fleet_scan"
    private const val KEY_DONE = "scan_done_v1"

    data class SlotEntry(
        val cameraId: String,
        val focalMm35: Int,
        val megapixels: Double,
        val grayscaled: Boolean,
    )

    fun scanFile(context: Context): File =
        File(context.filesDir, "fleet_focal_map.json")

    fun isScanDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun runIfNeeded(context: Context): List<SlotEntry> {
        val file = scanFile(context)
        if (isScanDone(context) && file.exists()) {
            return loadFromFile(file)
        }
        val entries = scanNow(context)
        persist(context, file, entries)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).commit()
        PnsAdbLog.i(context, "fleetScan slots=${entries.size} file=${file.absolutePath}")
        Log.i(TAG, "fleet_focal_map written slots=${entries.size}")
        return entries
    }

    fun scanNow(context: Context): List<SlotEntry> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val out = mutableListOf<SlotEntry>()
        for (id in cm.cameraIdList) {
            val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val focal = focalLength35mm(chars) ?: continue
            val mp = sensorMegapixels(chars)
            val gray = mp < 12.0
            out.add(SlotEntry(id, focal, mp, gray))
        }
        return out.sortedBy { it.focalMm35 }
    }

    internal fun focalLength35mm(chars: CameraCharacteristics): Int? {
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        if (focalLengths.isEmpty() || sensorSize.width <= 0f) return null
        val fMm = focalLengths.maxOrNull() ?: return null
        val diagSensor = hypot(sensorSize.width.toDouble(), sensorSize.height.toDouble())
        val diag35 = 43.27
        return (fMm * (diag35 / diagSensor)).roundToInt().coerceIn(10, 300)
    }

    internal fun sensorMegapixels(chars: CameraCharacteristics): Double {
        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return 0.0
        return (size.width.toLong() * size.height.toLong()) / 1_000_000.0
    }

    private fun persist(context: Context, file: File, entries: List<SlotEntry>) {
        val root =
            JSONObject().apply {
                put("version", 1)
                put("device", android.os.Build.MODEL)
                val arr = JSONArray()
                entries.forEach { e ->
                    arr.put(
                        JSONObject().apply {
                            put("cameraId", e.cameraId)
                            put("focalMm35", e.focalMm35)
                            put("megapixels", e.megapixels)
                            put("grayscaled", e.grayscaled)
                        },
                    )
                }
                put("slots", arr)
            }
        file.writeText(root.toString(2))
    }

    fun loadFromFile(file: File): List<SlotEntry> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        val arr = root.optJSONArray("slots") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SlotEntry(
                        cameraId = o.getString("cameraId"),
                        focalMm35 = o.getInt("focalMm35"),
                        megapixels = o.getDouble("megapixels"),
                        grayscaled = o.getBoolean("grayscaled"),
                    ),
                )
            }
        }
    }
}
