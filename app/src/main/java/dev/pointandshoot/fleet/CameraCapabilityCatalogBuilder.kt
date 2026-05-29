package dev.pointandshoot.fleet

import android.hardware.camera2.CameraMetadata
import dev.pointandshoot.Feature
import org.json.JSONArray
import org.json.JSONObject

/**
 * Evaluates [CameraCapabilityCatalog.registry] against a fleet matrix root (Milestone **17.1**).
 */
object CameraCapabilityCatalogBuilder {

    fun buildFromMatrix(root: JSONObject): JSONArray {
        val arr = JSONArray()
        for (row in CameraCapabilityCatalog.registry) {
            arr.put(evaluateRow(row, root).toJson())
        }
        return arr
    }

    fun attachTo(root: JSONObject): JSONObject {
        val catalog = buildFromMatrix(root)
        root.put(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG, catalog)
        root.put(FleetDeviceMatrix.KEY_CATALOG_VERSION, CameraCapabilityCatalog.CATALOG_VERSION)
        return root
    }

    fun evaluatedRows(root: JSONObject): List<CameraCapabilityCatalog.EvaluatedRow> =
        CameraCapabilityCatalog.registry.map { evaluateRow(it, root) }

    private fun evaluateRow(row: CameraCapabilityCatalog.CatalogRow, root: JSONObject): CameraCapabilityCatalog.EvaluatedRow {
        val (supported, sessionOk, detail) = when (row.id) {
            "raw.dng" -> gateTriple(root, "raw")
            "video.hfr" -> gateTriple(root, "hfr")
            "face.detect", "face.eye_af", "face.priority_ae" -> gateTriple(root, "face")
            "video.dcg_hdr" -> gateTriple(root, "dcgZsl")
            "video.regular.1080p30" -> regular1080p30(root)
            "camerax.night" -> cameraXMode(root, "NIGHT")
            "camerax.bokeh" -> cameraXMode(root, "BOKEH")
            "camerax.hdr" -> cameraXMode(root, "HDR")
            "lens.multi" -> Triple(focalSlotCount(root) > 0, null, "focalSlots=${focalSlotCount(root)}")
            "lens.ois" -> lensHasOis(root)
            "fleet.matrix" -> Triple(true, null, "matrix present")
            "root.hfr_unlock" -> Triple(anyHfrAbove60(root), null, "matrix HFR ceiling")
            else -> Triple(defaultProductSupported(row), null, "")
        }
        return CameraCapabilityCatalog.EvaluatedRow(
            row = row,
            deviceSupported = supported,
            sessionOk = sessionOk,
            detail = detail,
        )
    }

    private fun gateTriple(root: JSONObject, key: String): Triple<Boolean, Boolean?, String> {
        val gates = firstCameraGate(root, key) ?: return Triple(false, false, "no gate")
        val adv = gates.optBoolean("advertised", false)
        val sess = gates.optBoolean("sessionOk", false)
        val app = gates.optBoolean("appEnabled", false)
        return Triple(adv, sess, "advertised=$adv sessionOk=$sess appEnabled=$app")
    }

    private fun regular1080p30(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val enc = root.optJSONObject(FleetDeviceMatrix.KEY_ENCODER)
        val hasMr = anyStreamSize(root, 1920, 1080) || anyStreamSize(root, 1080, 1920)
        val encRow = encoderHasFps(enc, 30)
        return Triple(hasMr || encRow, null, "halMr=$hasMr enc30=$encRow")
    }

    private fun cameraXMode(root: JSONObject, label: String): Triple<Boolean, Boolean?, String> {
        val cx = root.optJSONObject(FleetDeviceMatrix.KEY_CAMERA_X) ?: return Triple(false, null, "no cameraX slice")
        val byCam = cx.optJSONObject("availableByCamera") ?: return Triple(false, null, "empty")
        var found = false
        val keys = byCam.keys()
        while (keys.hasNext()) {
            val modes = byCam.optJSONArray(keys.next()) ?: continue
            for (i in 0 until modes.length()) {
                if (modes.optJSONObject(i)?.optString("label") == label) {
                    found = true
                    break
                }
            }
        }
        return Triple(found, null, if (found) "mode=$label" else "absent")
    }

    private fun focalSlotCount(root: JSONObject): Int =
        root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")?.length() ?: 0

    private fun anyHfrAbove60(root: JSONObject): Boolean {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return false
        for (i in 0 until cams.length()) {
            if (cams.optJSONObject(i)?.optInt("hfrMaxFpsAt1080", 0) ?: 0 > 60) return true
        }
        return false
    }

    private fun anyStreamSize(root: JSONObject, w: Int, h: Int): Boolean {
        val deep = root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("deepCaps") ?: return false
        val cams = deep.optJSONArray("cameras") ?: return false
        for (i in 0 until cams.length()) {
            val map = cams.optJSONObject(i)?.optJSONObject("streamConfigurationMap") ?: continue
            if (sizesContain(map, w, h)) return true
        }
        return false
    }

    private fun sizesContain(map: JSONObject, w: Int, h: Int): Boolean {
        fun checkArr(key: String): Boolean {
            val arr = map.optJSONObject("outputSizesByFormat")?.optJSONArray(key)
                ?: map.optJSONObject("outputSizes")?.optJSONArray(key)
                ?: return false
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ww = o.optInt("w")
                val hh = o.optInt("h")
                if ((ww == w && hh == h) || (ww == h && hh == w)) return true
            }
            return false
        }
        return checkArr("mediaRecorder") || checkArr("surfaceTexture")
    }

    private fun encoderHasFps(enc: JSONObject?, fps: Int): Boolean {
        if (enc == null) return false
        val best = enc.optJSONArray("bestByCameraFps") ?: return false
        for (i in 0 until best.length()) {
            val o = best.optJSONObject(i) ?: continue
            if (o.optInt("fps", -1) == fps) return true
        }
        return false
    }

    private fun lensHasOis(root: JSONObject): Triple<Boolean, Boolean?, String> {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return Triple(false, null, "no cameras")
        for (i in 0 until cams.length()) {
            val lens = cams.optJSONObject(i)?.optJSONObject("lensInfo") ?: continue
            val modes = lens.optJSONArray("opticalStabilizationModes") ?: continue
            for (j in 0 until modes.length()) {
                val m = modes.optString(j)
                if (m.isNotBlank() && !m.equals("OFF", ignoreCase = true)) {
                    return Triple(true, null, "ois=$m")
                }
            }
        }
        return Triple(false, null, "ois=off")
    }

    private fun firstCameraGate(root: JSONObject, key: String): JSONObject? {
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return null
        for (i in 0 until cams.length()) {
            val g = cams.optJSONObject(i)?.optJSONObject("featureGates")?.optJSONObject(key)
            if (g != null) return g
        }
        return null
    }

    private fun defaultProductSupported(row: CameraCapabilityCatalog.CatalogRow): Boolean =
        when (row.appStatus) {
            CameraCapabilityCatalog.AppStatus.Shipped,
            CameraCapabilityCatalog.AppStatus.Partial,
            -> row.id.startsWith("still.") || row.id.startsWith("video.") || row.id.startsWith("hud.") || row.id.startsWith("af.") || row.id.startsWith("preview.")
            CameraCapabilityCatalog.AppStatus.ProbeOnly -> false
            CameraCapabilityCatalog.AppStatus.Planned -> false
            CameraCapabilityCatalog.AppStatus.NotApplicable -> false
        }

    /** Maps live [Feature] names to catalog ids for tests. */
    fun catalogIdForGateFeature(feature: Feature): String? =
        when (feature) {
            Feature.RawDng -> "raw.dng"
            Feature.UltraMaxProfile -> "raw.ultra_max"
            Feature.HfrPreview120 -> "video.hfr"
            Feature.EyeAfOverlay -> "face.eye_af"
            Feature.HighlightWeightedMetering -> "hud.highlight_meter"
            Feature.BracketBurst -> "still.bracket"
            Feature.SuperMacroLock -> "af.macro"
            Feature.TenBitHdrAvif -> "still.avif"
            Feature.OpticalStabilization -> "lens.ois"
            Feature.CameraExtensions -> "camerax.hdr"
            Feature.ReprocessSession -> "video.dcg_hdr"
        }
}
