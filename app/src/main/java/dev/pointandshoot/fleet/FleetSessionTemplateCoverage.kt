package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps catalog capabilities to session template support from matrix appendix (Milestone **21.9** / **21.13c**).
 */
object FleetSessionTemplateCoverage {
    data class TemplateRow(
        val catalogId: String,
        val preview: Boolean,
        val record: Boolean,
        val zsl: Boolean,
        val highSpeed: Boolean,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("catalogId", catalogId)
                put("preview", preview)
                put("record", record)
                put("zsl", zsl)
                put("highSpeed", highSpeed)
            }
    }

    fun build(matrix: JSONObject, catalogIds: Collection<String>): JSONArray {
        val sessionRoot = matrix.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("sessionMatrix")
        val hasSession = sessionRoot != null && sessionRoot.optJSONArray("cameras")?.length() ?: 0 > 0
        val hfrAny =
            (0 until (matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS)?.length() ?: 0)).any { i ->
                matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS)?.optJSONObject(i)?.optInt("hfrMaxFpsAt1080", 0) ?: 0 > 60
            }
        val arr = JSONArray()
        for (id in catalogIds) {
            val row =
                when {
                    id.startsWith("video.hfr") || id.contains(".120") || id.contains(".240") ->
                        TemplateRow(id, preview = hasSession, record = hasSession, zsl = false, highSpeed = hfrAny)
                    id.startsWith("still.zsl") ->
                        TemplateRow(id, preview = hasSession, record = hasSession, zsl = hasSession, highSpeed = false)
                    id.startsWith("video.") ->
                        TemplateRow(id, preview = hasSession, record = hasSession, zsl = false, highSpeed = false)
                    id.startsWith("raw.") || id.startsWith("still.") ->
                        TemplateRow(id, preview = hasSession, record = hasSession, zsl = hasSession, highSpeed = false)
                    else ->
                        TemplateRow(id, preview = hasSession, record = false, zsl = false, highSpeed = false)
                }
            arr.put(row.toJson())
        }
        return arr
    }
}
