package dev.pointandshoot.fleet

import android.content.Context
import org.json.JSONObject

/**
 * Embeds a short [FleetDeviceMatrix] summary in engineering probe markdown (Milestone **16.11**).
 *
 * Full structured JSON remains in `files/fleet_device_matrix.json`; `deep_caps_*` lives under matrix `appendix` on full tier.
 */
object FleetProbeMatrixMarkdown {
    fun appendSummary(sb: StringBuilder, context: Context) {
        val app = context.applicationContext
        val root =
            FleetDeviceMatrixStore.loadValid(app)
                ?: runCatching {
                    val f = FleetDeviceMatrixStore.matrixFile(app)
                    if (f.exists()) JSONObject(f.readText()) else null
                }.getOrNull()
        sb.appendLine()
        sb.appendLine("## Fleet device matrix (SoT)")
        if (root == null) {
            sb.appendLine()
            sb.appendLine("_No `fleet_device_matrix.json` — shallow probe writes quick tier; hub **Device capability matrix** runs full tier._")
            return
        }
        val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
        val device = root.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)
        sb.appendLine()
        sb.appendLine("| Field | Value |")
        sb.appendLine("|-------|-------|")
        sb.appendLine("| scanTier | ${meta?.optString("scanTier") ?: "?"} |")
        sb.appendLine("| cameras | ${FleetDeviceMatrix.cameraCount(root)} |")
        sb.appendLine("| model | ${device?.optString("model") ?: "?"} |")
        sb.appendLine("| scanDurationMs | ${meta?.optLong("scanDurationMs") ?: 0} |")
        sb.appendLine("| mediaPerformanceClass | ${meta?.opt("mediaPerformanceClass")} |")
        val policyId =
            root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("fleetProfiles")
                ?.optString("policyId")
                ?.takeIf { it.isNotEmpty() }
        sb.appendLine("| fleet policyId | ${policyId ?: "(generic)"} |")
        sb.appendLine()
        sb.appendLine("Per-camera HFR@1080 / RAW / gates:")
        sb.appendLine()
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return
        for (i in 0 until cams.length()) {
            val c = cams.getJSONObject(i)
            val id = c.optString("cameraId")
            val hfr = c.opt("hfrMaxFpsAt1080")
            val raw = c.optString("rawPickEffective", "—")
            val gates = c.optJSONObject("featureGates")
            val hfrGate = gates?.optJSONObject("hfr")?.let { g ->
                "adv=${g.optBoolean("advertised")} sess=${g.optBoolean("sessionOk")} app=${g.optBoolean("appEnabled")}"
            } ?: "—"
            sb.appendLine("- **$id**: HFR@1080=$hfr RAW=$raw gates(hfr)=$hfrGate")
        }
        sb.appendLine()
        sb.appendLine("_Host: `pns_fleet_matrix_scan.ps1` / `pns_fleet_matrix_diff.ps1`. Focal slots: matrix `product.focalSlots` (not standalone `fleet_focal_map.json` SoT)._")
    }
}
