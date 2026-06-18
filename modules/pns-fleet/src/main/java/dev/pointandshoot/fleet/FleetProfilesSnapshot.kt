package dev.pointandshoot.fleet

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full fleet profile export for one device model (Milestone **13.2**).
 */
data class FleetProfilesSnapshot(
    val deviceModel: String,
    val manufacturer: String,
    val logicalCameraId: String?,
    val roleByCameraId: Map<String, FleetCameraRole>,
    val profiles: List<FleetCameraProfile>,
    val policyId: String?,
    val leafRawFormatOrder: List<Int>,
) {
    fun profile(cameraId: String): FleetCameraProfile? = profiles.firstOrNull { it.cameraId == cameraId }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("schemaVersion", FleetCameraProfile.SCHEMA_VERSION)
            put("deviceModel", deviceModel)
            put("manufacturer", manufacturer)
            put("logicalCameraId", logicalCameraId ?: JSONObject.NULL)
            put("policyId", policyId ?: JSONObject.NULL)
            put("leafRawFormatOrder", JSONArray().apply { leafRawFormatOrder.forEach { put(it) } })
            put(
                "roleByCameraId",
                JSONObject().apply {
                    roleByCameraId.forEach { (id, role) -> put(id, role.name) }
                },
            )
            put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
        }

    companion object {
        fun fromJson(root: JSONObject): FleetProfilesSnapshot? {
            if (root.optInt("schemaVersion", -1) != FleetCameraProfile.SCHEMA_VERSION) return null
            val rolesObj = root.optJSONObject("roleByCameraId") ?: return null
            val roleByCameraId = linkedMapOf<String, FleetCameraRole>()
            rolesObj.keys().forEach { key ->
                val roleName = rolesObj.optString(key, "")
                roleByCameraId[key] =
                    runCatching { FleetCameraRole.valueOf(roleName) }
                        .getOrDefault(FleetCameraRole.UNKNOWN)
            }
            val profilesArr = root.optJSONArray("profiles") ?: return null
            val profiles =
                (0 until profilesArr.length()).mapNotNull { i ->
                    profilesArr.optJSONObject(i)?.let { FleetCameraProfile.fromJson(it) }
                }
            val leafOrder =
                root.optJSONArray("leafRawFormatOrder")?.let { arr ->
                    (0 until arr.length()).map { arr.getInt(it) }
                }.orEmpty()
            return FleetProfilesSnapshot(
                deviceModel = root.optString("deviceModel", Build.MODEL),
                manufacturer = root.optString("manufacturer", Build.MANUFACTURER),
                logicalCameraId =
                    if (root.isNull("logicalCameraId")) {
                        null
                    } else {
                        root.getString("logicalCameraId").takeIf { !it.isNullOrEmpty() }
                    },
                roleByCameraId = roleByCameraId,
                profiles = profiles,
                policyId =
                    if (root.isNull("policyId")) {
                        null
                    } else {
                        root.getString("policyId").takeIf { !it.isNullOrEmpty() }
                    },
                leafRawFormatOrder = leafOrder,
            )
        }
    }
}
