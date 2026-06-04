package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device diff summary for consecutive [FleetDeviceMatrix] scans (Milestone **16.1**).
 */
object FleetDeviceMatrixDiff {

    data class DiffResult(
        val summaryLines: List<String>,
        val changedCameraIds: List<String>,
        val hasChanges: Boolean,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("hasChanges", hasChanges)
                put("changedCameraIds", JSONArray(changedCameraIds))
                put("summaryLines", JSONArray(summaryLines))
            }
    }

    fun diff(previous: JSONObject?, current: JSONObject): DiffResult {
        if (previous == null) {
            return DiffResult(
                summaryLines = listOf("No previous scan — baseline established."),
                changedCameraIds = emptyList(),
                hasChanges = false,
            )
        }
        val lines = mutableListOf<String>()
        val changedIds = linkedSetOf<String>()

        val prevTier = previous.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optString("scanTier")
        val curTier = current.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optString("scanTier")
        if (prevTier != curTier) {
            lines += "scanTier: $prevTier → $curTier"
        }

        val prevCams = previous.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: JSONArray()
        val curCams = current.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: JSONArray()
        if (prevCams.length() != curCams.length()) {
            lines += "cameraCount: ${prevCams.length()} → ${curCams.length()}"
        }

        val prevById = camerasById(prevCams)
        val curById = camerasById(curCams)
        for (id in (prevById.keys + curById.keys).sorted()) {
            val prev = prevById[id]
            val cur = curById[id]
            if (prev == null) {
                lines += "camera $id: added"
                changedIds += id
                continue
            }
            if (cur == null) {
                lines += "camera $id: removed"
                changedIds += id
                continue
            }
            val camLines = diffCamera(id, prev, cur)
            if (camLines.isNotEmpty()) {
                lines += camLines
                changedIds += id
            }
        }

        diffFocalSlots(previous, current)?.let { lines += it }
        diffHardwareLaunch(previous, current)?.let { lines += it }
        diffHardwareButtons(previous, current)?.let { lines += it }
        diffEncoder(previous, current)?.let { lines += it }

        return DiffResult(
            summaryLines = lines.ifEmpty { listOf("No material changes vs previous scan.") },
            changedCameraIds = changedIds.toList(),
            hasChanges = lines.isNotEmpty(),
        )
    }

    private fun camerasById(arr: JSONArray): Map<String, JSONObject> {
        val out = linkedMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out[o.optString("cameraId", "?")] = o
        }
        return out
    }

    private fun diffCamera(id: String, prev: JSONObject, cur: JSONObject): List<String> {
        val lines = mutableListOf<String>()
        diffScalar(prev, cur, "hfrMaxFpsAt1080")?.let { lines += "camera $id hfrMaxFpsAt1080: $it" }
        diffScalar(prev, cur, "rawPickEffective")?.let { lines += "camera $id rawPickEffective: $it" }
        diffFeatureGate(prev, cur, "raw")?.let { lines += "camera $id raw: $it" }
        diffFeatureGate(prev, cur, "hfr")?.let { lines += "camera $id hfr: $it" }
        diffFeatureGate(prev, cur, "face")?.let { lines += "camera $id face: $it" }
        diffFeatureGate(prev, cur, "dcgZsl")?.let { lines += "camera $id dcgZsl: $it" }
        val prevCaps = prev.optJSONArray("capabilitiesNormalized")?.toStringSet().orEmpty()
        val curCaps = cur.optJSONArray("capabilitiesNormalized")?.toStringSet().orEmpty()
        val added = curCaps - prevCaps
        val removed = prevCaps - curCaps
        if (added.isNotEmpty()) lines += "camera $id capabilities +${added.sorted()}"
        if (removed.isNotEmpty()) lines += "camera $id capabilities -${removed.sorted()}"
        return lines
    }

    private fun diffScalar(prev: JSONObject, cur: JSONObject, key: String): String? {
        val p = prev.opt(key)
        val c = cur.opt(key)
        if (p == c || (p == JSONObject.NULL && c == JSONObject.NULL)) return null
        return "$p → $c"
    }

    private fun diffFeatureGate(prev: JSONObject, cur: JSONObject, family: String): String? {
        val p = prev.optJSONObject("featureGates")?.optJSONObject(family)
        val c = cur.optJSONObject("featureGates")?.optJSONObject(family)
        if (p == null && c == null) return null
        val parts = mutableListOf<String>()
        for (col in listOf("advertised", "sessionOk", "appEnabled")) {
            val pv = p?.optBoolean(col)
            val cv = c?.optBoolean(col)
            if (pv != cv) parts += "$col $pv→$cv"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun diffEncoder(prev: JSONObject, cur: JSONObject): String? {
        val p = prev.optJSONObject(FleetDeviceMatrix.KEY_ENCODER)
        val c = cur.optJSONObject(FleetDeviceMatrix.KEY_ENCODER)
        if (p == null && c == null) return null
        if (p == null || c == null) return "encoder slice: ${if (p == null) "added" else "removed"}"
        val pSrc = p.optString("source")
        val cSrc = c.optString("source")
        if (pSrc != cSrc) return "encoder source: $pSrc → $cSrc"
        val pRows = p.optJSONArray("bestByCameraFps")?.length() ?: 0
        val cRows = c.optJSONArray("bestByCameraFps")?.length() ?: 0
        if (pRows != cRows) return "encoder bestByCameraFps rows: $pRows → $cRows"
        return null
    }

    private fun diffFocalSlots(prev: JSONObject, cur: JSONObject): String? {
        val pSlots = prev.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")
        val cSlots = cur.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")
        if (pSlots?.toString() == cSlots?.toString()) return null
        return "focalSlots changed (${pSlots?.length() ?: 0} → ${cSlots?.length() ?: 0} entries)"
    }

    private fun diffHardwareLaunch(prev: JSONObject, cur: JSONObject): String? {
        val pRole =
            prev.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareLaunch")
                ?.optJSONObject("stillImageCamera")
                ?.optString("defaultRoleHolder")
        val cRole =
            cur.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareLaunch")
                ?.optJSONObject("stillImageCamera")
                ?.optString("defaultRoleHolder")
        if (pRole == cRole) return null
        return "hardwareLaunch defaultRoleHolder: $pRole → $cRole"
    }

    private fun diffHardwareButtons(prev: JSONObject, cur: JSONObject): String? {
        val pCodes =
            prev.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareButtons")
                ?.optJSONObject("interactiveProbe")
                ?.optJSONArray("distinctKeyCodes")
                ?.toString()
        val cCodes =
            cur.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("hardwareButtons")
                ?.optJSONObject("interactiveProbe")
                ?.optJSONArray("distinctKeyCodes")
                ?.toString()
        if (pCodes == cCodes && pCodes != null) return null
        if (pCodes == null && cCodes == null) {
            val pLikely =
                prev.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                    ?.optJSONObject("hardwareButtons")
                    ?.optBoolean("dedicatedCameraKeyLikely")
            val cLikely =
                cur.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                    ?.optJSONObject("hardwareButtons")
                    ?.optBoolean("dedicatedCameraKeyLikely")
            if (pLikely == cLikely) return null
            return "hardwareButtons dedicatedCameraKeyLikely: $pLikely → $cLikely"
        }
        return "hardwareButtons distinctKeyCodes changed"
    }

    private fun JSONArray.toStringSet(): Set<String> {
        val out = linkedSetOf<String>()
        for (i in 0 until length()) {
            out += optString(i)
        }
        return out
    }
}
