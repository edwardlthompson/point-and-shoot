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
        val (supported, sessionOk, detail) = CameraCapabilityCatalogEvaluators.evaluate(row, root)
        return CameraCapabilityCatalog.EvaluatedRow(
            row = row,
            deviceSupported = supported,
            sessionOk = sessionOk,
            detail = detail,
        )
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
