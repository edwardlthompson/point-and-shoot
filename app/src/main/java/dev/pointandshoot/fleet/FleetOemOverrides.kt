package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject

/**
 * Milestone **13.5** — per-model camera id aliases / hide list (host JSON, not shipped in APK assets yet).
 */
data class FleetOemOverrides(
    val modelPattern: String,
    val hideCameraIds: Set<String> = emptySet(),
    val aliasCameraIds: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("modelPattern", modelPattern)
            put("hideCameraIds", JSONArray(hideCameraIds.toList()))
            put(
                "aliasCameraIds",
                JSONObject().apply {
                    aliasCameraIds.forEach { (k, v) -> put(k, v) }
                },
            )
        }

    companion object {
        fun fromJson(root: JSONObject): FleetOemOverrides? {
            val pattern = root.optString("modelPattern").takeIf { it.isNotBlank() } ?: return null
            val hide =
                buildSet {
                    val arr = root.optJSONArray("hideCameraIds") ?: return@buildSet
                    for (i in 0 until arr.length()) {
                        arr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            val alias = mutableMapOf<String, String>()
            root.optJSONObject("aliasCameraIds")?.let { o ->
                o.keys().forEach { key ->
                    val v = o.optString(key)
                    if (key.isNotBlank() && v.isNotBlank()) alias[key] = v
                }
            }
            return FleetOemOverrides(pattern, hide, alias)
        }

        /** CPH2655 dodge table — hide front / unused logical children in fleet export when applied. */
        fun onePlus13Cph2655(): FleetOemOverrides =
            FleetOemOverrides(
                modelPattern = "CPH2655",
                hideCameraIds = emptySet(),
                aliasCameraIds =
                    mapOf(
                        "3" to "uw",
                        "2" to "wide",
                        "4" to "tele",
                        "0" to "logical",
                    ),
            )
    }
}
