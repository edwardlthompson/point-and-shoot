package dev.pointandshoot.fleet

/**
 * Default consumer-impact tier for catalog rows (Milestone **21.13g**).
 */
object FleetParityConsumerImpact {
    fun resolve(row: CameraCapabilityCatalog.CatalogRow): FleetParitySweep.ConsumerImpact {
        row.consumerImpact?.let { return it }
        if (row.humanOnly) return FleetParitySweep.ConsumerImpact.INFORMATIONAL
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
}
