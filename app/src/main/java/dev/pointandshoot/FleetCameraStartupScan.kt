package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Size
import dev.pointandshoot.fleet.Camera2FullMpBreakthrough
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Sprint **15.13** — first-launch focal map from [CameraCharacteristics] (35 mm equiv + MP gate).
 *
 * **Fleet SoT (16.11):** focal slots for agents/UI policy live in `files/fleet_device_matrix.json`
 * under `product.focalSlots`. This file is a **compat mirror** for first-launch UI; do not treat
 * `fleet_focal_map.json` as the canonical fleet artifact.
 */
object FleetCameraStartupScan {
    const val TAG = "PNS.FleetScan"
    private const val PREFS = "pns_fleet_scan"
    private const val KEY_DONE = "scan_done_v1"
    private const val MP_OVERRIDE_FILE = "fleet_focal_mp_override.json"

    data class SlotEntry(
        val cameraId: String,
        val focalMm35: Int,
        val megapixels: Double,
        val grayscaled: Boolean,
    )

    internal data class MegapixelProbe(
        val pixelArrayMp: Double,
        val activeArrayMp: Double,
        val defaultMapMp: Double,
        val maxPixelArrayMp: Double,
        val maxActiveArrayMp: Double,
        val maxResMapMp: Double,
    ) {
        val chosenMp: Double
            get() = maxOf(pixelArrayMp, activeArrayMp, defaultMapMp, maxPixelArrayMp, maxActiveArrayMp, maxResMapMp)
    }

    data class MpOverrideEntry(
        val cameraId: String,
        val megapixels: Double,
        val source: String?,
    )

    data class StillResolutionAdvertisedEntry(
        val cameraId: String,
        val defaultJpeg: Size?,
        val highResJpeg: Size?,
        val maxResMapJpeg: Size?,
        val multiResJpeg: Size?,
        val defaultRawSensor: Size?,
        val highResRawSensor: Size?,
        val maxResMapRawSensor: Size?,
        val hasLargerThanDefault: Boolean,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("cameraId", cameraId)
                put("defaultJpeg", sizeJson(defaultJpeg))
                put("highResJpeg", sizeJson(highResJpeg))
                put("maxResMapJpeg", sizeJson(maxResMapJpeg))
                put("multiResJpeg", sizeJson(multiResJpeg))
                put("defaultRawSensor", sizeJson(defaultRawSensor))
                put("highResRawSensor", sizeJson(highResRawSensor))
                put("maxResMapRawSensor", sizeJson(maxResMapRawSensor))
                put("hasLargerThanDefault", hasLargerThanDefault)
            }
    }

    fun scanFile(context: Context): File =
        File(context.filesDir, "fleet_focal_map.json")

    fun mpOverrideFile(context: Context): File =
        File(context.filesDir, MP_OVERRIDE_FILE)

    fun loadMpOverrides(context: Context): Map<String, MpOverrideEntry> {
        val file = mpOverrideFile(context)
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray("overrides") ?: JSONArray()
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("cameraId", "")
                    val mp = obj.optDouble("megapixels", Double.NaN)
                    if (id.isBlank() || !mp.isFinite() || mp <= 0.0) continue
                    put(
                        id,
                        MpOverrideEntry(
                            cameraId = id,
                            megapixels = mp,
                            source = obj.optString("source").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

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
        val overrides = loadMpOverrides(context)
        val out = mutableListOf<SlotEntry>()
        for (id in cm.cameraIdList) {
            val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
            if (!isFocalRoutingCandidate(chars)) continue
            val focal = focalLength35mm(chars) ?: continue
            val mpProbe = sensorMegapixelProbe(chars)
            val overrideMp = overrides[id]?.megapixels ?: 0.0
            val mp = maxOf(mpProbe.chosenMp, overrideMp)
            val gray = mp < 12.0
            out.add(SlotEntry(id, focal, mp, gray))
            Log.i(
                TAG,
                "fleetScan cameraId=$id focal35=$focal mp=$mp overrideMp=${"%.3f".format(overrideMp)} " +
                    "mpProbe=px:${"%.3f".format(mpProbe.pixelArrayMp)}," +
                    "act:${"%.3f".format(mpProbe.activeArrayMp)}," +
                    "map:${"%.3f".format(mpProbe.defaultMapMp)}," +
                    "pxMax:${"%.3f".format(mpProbe.maxPixelArrayMp)}," +
                    "actMax:${"%.3f".format(mpProbe.maxActiveArrayMp)}," +
                    "mapMax:${"%.3f".format(mpProbe.maxResMapMp)}",
            )
        }
        return out.sortedBy { it.focalMm35 }
    }

    private fun isFocalRoutingCandidate(chars: CameraCharacteristics): Boolean {
        if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
            return false
        }
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return false
        if (!caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
            return false
        }
        return sensorMegapixels(chars) >= 2.0
    }

    fun scanStillResolutionAdvertised(context: Context): List<StillResolutionAdvertisedEntry> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return scanStillResolutionAdvertised(cm)
    }

    fun scanExperimentalUnlockState(context: Context): JSONObject {
        val hud = HudSettings.load(context.applicationContext)
        return JSONObject().apply {
            put("masterEnabled", hud.enableExperimentalAppBreakingFeatures)
            put("maxResolutionUnlockEnabled", hud.enableExperimentalMaxResolutionUnlock)
            put("vendorSessionKeysEnabled", hud.enableExperimentalVendorSessionKeys)
            put("safeModeActive", ExperimentalSafeModeStore.isSafeModeActive(context.applicationContext))
            put("rootGranted", RootCapabilityStore.loadOrUnknown(context.applicationContext).grantsPrivileged)
            put("unlockLane", ExperimentalMaxResolutionUnlock.snapshotForMatrix(context.applicationContext))
        }
    }

    /** Per rear camera: HAL max-resolution map probe (no live capture session). */
    fun scanMaxResolutionStillProbe(cm: CameraManager): JSONArray {
        val entries = scanStillResolutionAdvertised(cm)
        return JSONArray().apply {
            entries.forEach { entry ->
                val facing =
                    runCatching {
                        cm.getCameraCharacteristics(entry.cameraId).get(CameraCharacteristics.LENS_FACING)
                    }.getOrNull()
                if (facing != CameraCharacteristics.LENS_FACING_BACK) return@forEach
                val defaultMp =
                    (area(entry.defaultJpeg).coerceAtLeast(area(entry.defaultRawSensor)) / 1_000_000.0)
                val maxResMp =
                    listOf(
                        area(entry.maxResMapJpeg) / 1_000_000.0,
                        area(entry.maxResMapRawSensor) / 1_000_000.0,
                    ).maxOrNull() ?: 0.0
                val suggestedUnlock =
                    when {
                        defaultMp >= Camera2FullMpBreakthrough.MP_THRESHOLD -> "default_full"
                        entry.hasLargerThanDefault && maxResMp >= Camera2FullMpBreakthrough.MP_THRESHOLD ->
                            "photo_resolution_mode_max"
                        else -> "none"
                    }
                put(
                    JSONObject().apply {
                        put("cameraId", entry.cameraId)
                        put("defaultMp", (defaultMp * 10.0).roundToInt() / 10.0)
                        put("maxResMapMp", (maxResMp * 10.0).roundToInt() / 10.0)
                        put("hasLargerThanDefault", entry.hasLargerThanDefault)
                        put("suggestedUnlock", suggestedUnlock)
                    },
                )
            }
        }
    }

    fun scanStillResolutionAdvertised(cm: CameraManager): List<StillResolutionAdvertisedEntry> {
        return cm.cameraIdList.sorted().mapNotNull { id ->
            val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
            val defaultMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val maxResMap =
                keyByName<StreamConfigurationMap>(chars, "android.scaler.streamConfigurationMapMaximumResolution")
                    ?.let { chars.get(it) }
            val defaultJpeg = largestSize(defaultMap, ImageFormat.JPEG, includeHighRes = false)
            val highResJpeg = largestSize(defaultMap, ImageFormat.JPEG, includeHighRes = true, onlyHighRes = true)
            val maxResMapJpeg = largestSize(maxResMap, ImageFormat.JPEG, includeHighRes = true)
            val multiResJpeg = largestMultiResolutionJpeg(chars)
            val defaultRawSensor = largestSize(defaultMap, ImageFormat.RAW_SENSOR, includeHighRes = false)
            val highResRawSensor = largestSize(defaultMap, ImageFormat.RAW_SENSOR, includeHighRes = true, onlyHighRes = true)
            val maxResMapRawSensor = largestSize(maxResMap, ImageFormat.RAW_SENSOR, includeHighRes = true)
            val defaultArea = area(defaultJpeg)
            val hasLargerThanDefault =
                listOf(highResJpeg, maxResMapJpeg, multiResJpeg).any { area(it) > defaultArea } ||
                    listOf(highResRawSensor, maxResMapRawSensor).any { area(it) > area(defaultRawSensor) }
            StillResolutionAdvertisedEntry(
                cameraId = id,
                defaultJpeg = defaultJpeg,
                highResJpeg = highResJpeg,
                maxResMapJpeg = maxResMapJpeg,
                multiResJpeg = multiResJpeg,
                defaultRawSensor = defaultRawSensor,
                highResRawSensor = highResRawSensor,
                maxResMapRawSensor = maxResMapRawSensor,
                hasLargerThanDefault = hasLargerThanDefault,
            )
        }
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> keyByName(chars: CameraCharacteristics, name: String): CameraCharacteristics.Key<T>? =
        chars.keys.firstOrNull { it.name == name } as? CameraCharacteristics.Key<T>

    private fun maxMpFromSizes(sizes: Array<Size>?): Double =
        sizes
            ?.maxOfOrNull { (it.width.toLong() * it.height.toLong()) / 1_000_000.0 }
            ?: 0.0

    private fun maxMpFromMap(map: StreamConfigurationMap?): Double {
        if (map == null) return 0.0
        return maxOf(
            maxMpFromSizes(runCatching { map.getOutputSizes(ImageFormat.JPEG) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getOutputSizes(ImageFormat.YUV_420_888) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getHighResolutionOutputSizes(ImageFormat.JPEG) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getHighResolutionOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getHighResolutionOutputSizes(ImageFormat.RAW10) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getHighResolutionOutputSizes(ImageFormat.RAW12) }.getOrNull()),
            maxMpFromSizes(runCatching { map.getHighResolutionOutputSizes(ImageFormat.YUV_420_888) }.getOrNull()),
        )
    }

    private fun largestSize(
        map: StreamConfigurationMap?,
        format: Int,
        includeHighRes: Boolean,
        onlyHighRes: Boolean = false,
    ): Size? {
        if (map == null) return null
        val regular =
            if (!onlyHighRes) {
                runCatching { map.getOutputSizes(format)?.toList() }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        val high =
            if (includeHighRes) {
                runCatching { map.getHighResolutionOutputSizes(format)?.toList() }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        return (regular + high)
            .distinctBy { it.width to it.height }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    private fun largestMultiResolutionJpeg(chars: CameraCharacteristics): Size? {
        val key = keyByName<Any>(chars, "android.scaler.multiResolutionStreamConfigurationMap") ?: return null
        val map = runCatching { chars.get(key) }.getOrNull() ?: return null
        val infos =
            runCatching {
                val m = map.javaClass.getMethod("getOutputInfo", Int::class.javaPrimitiveType)
                m.invoke(map, ImageFormat.JPEG) as? Collection<*>
            }.getOrNull() ?: return null
        var best: Size? = null
        for (info in infos.filterNotNull()) {
            val w = runCatching { info.javaClass.getMethod("getWidth").invoke(info) as Int }.getOrNull() ?: continue
            val h = runCatching { info.javaClass.getMethod("getHeight").invoke(info) as Int }.getOrNull() ?: continue
            val candidate = Size(w, h)
            if (best == null || area(candidate) > area(best)) {
                best = candidate
            }
        }
        return best
    }

    private fun area(size: Size?): Long =
        if (size == null) 0L else size.width.toLong() * size.height.toLong()

    private fun sizeJson(size: Size?): Any =
        if (size == null) {
            JSONObject.NULL
        } else {
            JSONObject().apply {
                put("width", size.width)
                put("height", size.height)
                put("mp", (size.width.toLong() * size.height.toLong()) / 1_000_000.0)
            }
        }

    internal fun sensorMegapixels(chars: CameraCharacteristics): Double {
        return sensorMegapixelProbe(chars).chosenMp
    }

    internal fun sensorMegapixels(
        context: Context,
        cameraId: String,
        chars: CameraCharacteristics,
    ): Double {
        val probe = sensorMegapixelProbe(chars).chosenMp
        val overrideMp = loadMpOverrides(context)[cameraId]?.megapixels ?: 0.0
        return maxOf(probe, overrideMp)
    }

    internal fun sensorMegapixelProbe(chars: CameraCharacteristics): MegapixelProbe {
        val pixelArrayMp =
            chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                ?.let { (it.width.toLong() * it.height.toLong()) / 1_000_000.0 }
                ?: 0.0
        val activeArrayMp =
            chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?.let { (it.width().toLong() * it.height().toLong()) / 1_000_000.0 }
                ?: 0.0
        val defaultMapMp = maxMpFromMap(chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))

        // API 31+ keys on ultra-high-res sensors. Resolve by key name so older frameworks remain safe.
        val maxPixelArrayMp =
            keyByName<android.util.Size>(chars, "android.sensor.info.pixelArraySizeMaximumResolution")
                ?.let { chars.get(it) }
                ?.let { (it.width.toLong() * it.height.toLong()) / 1_000_000.0 }
                ?: 0.0
        val maxActiveArrayMp =
            keyByName<android.graphics.Rect>(chars, "android.sensor.info.activeArraySizeMaximumResolution")
                ?.let { chars.get(it) }
                ?.let { (it.width().toLong() * it.height().toLong()) / 1_000_000.0 }
                ?: 0.0
        val maxResMapMp =
            keyByName<StreamConfigurationMap>(chars, "android.scaler.streamConfigurationMapMaximumResolution")
                ?.let { chars.get(it) }
                .let { maxMpFromMap(it) }

        return MegapixelProbe(
            pixelArrayMp = pixelArrayMp,
            activeArrayMp = activeArrayMp,
            defaultMapMp = defaultMapMp,
            maxPixelArrayMp = maxPixelArrayMp,
            maxActiveArrayMp = maxActiveArrayMp,
            maxResMapMp = maxResMapMp,
        )
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
