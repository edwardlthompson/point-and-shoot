package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-[cameraId] capability snapshot for fleet RAW / still policy (Milestone **13.2**).
 */
data class FleetCameraProfile(
    val cameraId: String,
    val role: FleetCameraRole,
    val physicalCameraIds: List<String>,
    val focalLengthsMm: List<Float>,
    val rawFormatsAdvertised: List<Int>,
    val prefersRawSensor: Boolean,
    val lensShadingMapOnStill: Boolean,
    val shadingModes: Set<Int>,
    val supportsDcgSession: Boolean,
    /** Milestone **13.6** — in-app RAW video lane (legacy leaf first). */
    val supportsRawVideo: Boolean = false,
    val hfrMaxFps: Int?,
    val activeArrayWidth: Int,
    val activeArrayHeight: Int,
    val largestRawSensorWxH: String?,
    val largestRaw12WxH: String?,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("cameraId", cameraId)
            put("role", role.name)
            put("physicalCameraIds", JSONArray(physicalCameraIds))
            put("focalLengthsMm", JSONArray().apply { focalLengthsMm.forEach { put(it.toDouble()) } })
            put("rawFormatsAdvertised", JSONArray().apply { rawFormatsAdvertised.forEach { put(it) } })
            put("prefersRawSensor", prefersRawSensor)
            put("lensShadingMapOnStill", lensShadingMapOnStill)
            put("shadingModes", JSONArray().apply { shadingModes.sorted().forEach { put(it) } })
            put("supportsDcgSession", supportsDcgSession)
            put("supportsRawVideo", supportsRawVideo)
            put("hfrMaxFps", hfrMaxFps ?: JSONObject.NULL)
            put("activeArrayWidth", activeArrayWidth)
            put("activeArrayHeight", activeArrayHeight)
            put("largestRawSensorWxH", largestRawSensorWxH ?: JSONObject.NULL)
            put("largestRaw12WxH", largestRaw12WxH ?: JSONObject.NULL)
        }

    companion object {
        const val SCHEMA_VERSION: Int = 1

        fun fromJson(o: JSONObject): FleetCameraProfile {
            val phys = o.optJSONArray("physicalCameraIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }.orEmpty()
            val focal = o.optJSONArray("focalLengthsMm")?.let { arr ->
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            }.orEmpty()
            val rawFmts = o.optJSONArray("rawFormatsAdvertised")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }
            }.orEmpty()
            val shading = o.optJSONArray("shadingModes")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            }.orEmpty()
            val role =
                runCatching { FleetCameraRole.valueOf(o.getString("role")) }
                    .getOrDefault(FleetCameraRole.UNKNOWN)
            return FleetCameraProfile(
                cameraId = o.getString("cameraId"),
                role = role,
                physicalCameraIds = phys,
                focalLengthsMm = focal,
                rawFormatsAdvertised = rawFmts,
                prefersRawSensor = o.optBoolean("prefersRawSensor", false),
                lensShadingMapOnStill = o.optBoolean("lensShadingMapOnStill", false),
                shadingModes = shading,
                supportsDcgSession = o.optBoolean("supportsDcgSession", false),
                supportsRawVideo = o.optBoolean("supportsRawVideo", false),
                hfrMaxFps = if (o.isNull("hfrMaxFps")) null else o.getInt("hfrMaxFps"),
                activeArrayWidth = o.optInt("activeArrayWidth", 0),
                activeArrayHeight = o.optInt("activeArrayHeight", 0),
                largestRawSensorWxH =
                    if (o.isNull("largestRawSensorWxH")) {
                        null
                    } else {
                        o.getString("largestRawSensorWxH").takeIf { !it.isNullOrEmpty() }
                    },
                largestRaw12WxH =
                    if (o.isNull("largestRaw12WxH")) {
                        null
                    } else {
                        o.getString("largestRaw12WxH").takeIf { !it.isNullOrEmpty() }
                    },
            )
        }
    }
}
