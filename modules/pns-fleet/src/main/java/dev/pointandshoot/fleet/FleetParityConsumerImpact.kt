package dev.pointandshoot.fleet

import org.json.JSONObject

/**
 * Default consumer-impact tier for catalog rows (Milestone **21.13g**).
 */
object FleetParityConsumerImpact {
    fun resolve(
        row: CameraCapabilityCatalog.CatalogRow,
        matrix: JSONObject? = null,
    ): FleetParitySweep.ConsumerImpact {
        row.consumerImpact?.let { return it }
        if (row.humanOnly) return FleetParitySweep.ConsumerImpact.INFORMATIONAL
        if (row.id.startsWith("video.av1") && matrixAv1SessionOkFalse(matrix)) {
            return FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY
        }
        return when {
            row.id.startsWith("perf.") -> FleetParitySweep.ConsumerImpact.INFORMATIONAL
            row.appStatus == CameraCapabilityCatalog.AppStatus.ProbeOnly -> FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY
            row.appStatus == CameraCapabilityCatalog.AppStatus.Planned -> FleetParitySweep.ConsumerImpact.INFORMATIONAL
            row.id.startsWith("root.") || row.id.startsWith("camerax.") -> FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY
            row.id.startsWith("fleet.parity") || row.id.startsWith("encoder.") -> FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY
            row.appStatus == CameraCapabilityCatalog.AppStatus.Partial &&
                row.surfacing.any { it in consumerSurfacing } ->
                FleetParitySweep.ConsumerImpact.SHIP_BLOCKER
            row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped &&
                row.surfacing.any { it in consumerSurfacing } ->
                FleetParitySweep.ConsumerImpact.SHIP_BLOCKER
            row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped -> FleetParitySweep.ConsumerImpact.SHIP_BLOCKER
            row.appStatus == CameraCapabilityCatalog.AppStatus.Partial -> FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY
            else -> FleetParitySweep.ConsumerImpact.INFORMATIONAL
        }
    }

    private val consumerSurfacing =
        setOf(
            "format_picker",
            "focal_row",
            "mode_dial",
            "qs_grid",
            "settings",
            "dial_H",
        )

    private fun matrixAv1SessionOkFalse(matrix: JSONObject?): Boolean {
        if (matrix == null) return false
        val cams = matrix.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return false
        for (i in 0 until cams.length()) {
            val gate = cams.optJSONObject(i)?.optJSONObject("featureGates")?.optJSONObject("av1")
            if (gate != null) {
                return gate.optBoolean("sessionOk", true) == false
            }
        }
        return false
    }
}
