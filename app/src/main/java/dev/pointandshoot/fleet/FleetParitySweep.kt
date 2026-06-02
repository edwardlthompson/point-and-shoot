package dev.pointandshoot.fleet

/**
 * Fleet Parity Sweep — gap classification and cell model (Milestone **18.6** + **21.8**).
 */
object FleetParitySweep {
    const val TAG = "PNS.FleetParity"

    enum class GapClass {
        OK,
        GAP_ADVERTISED_NOT_PROVEN,
        GAP_DELIVERY_MISMATCH,
        GAP_PROVEN_NOT_ADVERTISED,
        GAP_PLANNED,
        GAP_PROBE_INVENTORY,
        GAP_ADVERTISED_NOT_SURFACED,
        GAP_SURFACED_NOT_ADVERTISED,
        GAP_REGRESSION_SINCE_BASELINE,
        GAP_CONFLICT_RISK,
        GAP_UNAUTOMATED,
        GAP_FLAKE_SUSPECT,
        GAP_HUMAN_ONLY,
        GAP_FLEET_PLUGIN_CANDIDATE,
    }

    enum class ConsumerImpact {
        SHIP_BLOCKER,
        ENGINEERING_ONLY,
        INFORMATIONAL,
    }

    data class DeliveryProbe(
        val requestedWidth: Int,
        val requestedHeight: Int,
        val requestedFps: Int,
        val actualWidth: Int,
        val actualHeight: Int,
        val actualFps: Double,
        val matchOk: Boolean,
        val mismatchReason: String? = null,
    )

    data class ParityCellResult(
        val catalogId: String,
        val advertised: Boolean,
        val sessionOk: Boolean?,
        val appEnabled: Boolean,
        val provenOk: Boolean,
        val failReason: String? = null,
        val artifactPath: String? = null,
        val durationMs: Long = 0L,
        val deliveryProbe: DeliveryProbe? = null,
        val consumerImpact: ConsumerImpact = ConsumerImpact.SHIP_BLOCKER,
        val gapClass: GapClass? = null,
        val conflictId: String? = null,
    )

    fun classify(
        cell: ParityCellResult,
        appStatus: CameraCapabilityCatalog.AppStatus,
        row: CameraCapabilityCatalog.CatalogRow? = null,
    ): GapClass {
        cell.gapClass?.let { return it }
        if (row?.humanOnly == true) return GapClass.GAP_HUMAN_ONLY
        if (cell.failReason?.startsWith("skip:probe_only_inventory") == true) {
            return GapClass.GAP_PROBE_INVENTORY
        }
        if (cell.failReason == "unautomated") return GapClass.GAP_UNAUTOMATED
        if (cell.failReason == "flake_suspect") return GapClass.GAP_FLAKE_SUSPECT
        if (cell.failReason == "regression_since_baseline") return GapClass.GAP_REGRESSION_SINCE_BASELINE
        if (cell.failReason == "fleet_plugin_candidate") return GapClass.GAP_FLEET_PLUGIN_CANDIDATE
        if (cell.failReason == "advertised_not_surfaced") return GapClass.GAP_ADVERTISED_NOT_SURFACED
        if (cell.failReason == "surfaced_not_advertised") return GapClass.GAP_SURFACED_NOT_ADVERTISED
        if (cell.conflictId != null) return GapClass.GAP_CONFLICT_RISK
        return when {
            appStatus == CameraCapabilityCatalog.AppStatus.Planned ->
                GapClass.GAP_PLANNED
            appStatus == CameraCapabilityCatalog.AppStatus.ProbeOnly &&
                cell.provenOk ->
                GapClass.GAP_PROBE_INVENTORY
            cell.deliveryProbe != null && !cell.deliveryProbe.matchOk ->
                GapClass.GAP_DELIVERY_MISMATCH
            cell.advertised && !cell.provenOk ->
                GapClass.GAP_ADVERTISED_NOT_PROVEN
            !cell.advertised && cell.provenOk ->
                GapClass.GAP_PROVEN_NOT_ADVERTISED
            cell.provenOk ->
                GapClass.OK
            !cell.advertised ->
                GapClass.OK
            else ->
                GapClass.GAP_ADVERTISED_NOT_PROVEN
        }
    }

    fun closurePlanPriority(gap: GapClass): Int =
        when (gap) {
            GapClass.GAP_REGRESSION_SINCE_BASELINE -> 0
            GapClass.GAP_DELIVERY_MISMATCH -> 1
            GapClass.GAP_ADVERTISED_NOT_PROVEN -> 2
            GapClass.GAP_CONFLICT_RISK -> 3
            GapClass.GAP_ADVERTISED_NOT_SURFACED -> 4
            GapClass.GAP_UNAUTOMATED -> 5
            GapClass.GAP_PROVEN_NOT_ADVERTISED -> 6
            GapClass.GAP_SURFACED_NOT_ADVERTISED -> 7
            GapClass.GAP_FLEET_PLUGIN_CANDIDATE -> 8
            GapClass.GAP_FLAKE_SUSPECT -> 9
            GapClass.GAP_HUMAN_ONLY -> 10
            GapClass.GAP_PROBE_INVENTORY -> 11
            GapClass.GAP_PLANNED -> 12
            GapClass.OK -> 13
        }

    fun consumerImpactPriority(tier: ConsumerImpact): Int =
        when (tier) {
            ConsumerImpact.SHIP_BLOCKER -> 0
            ConsumerImpact.ENGINEERING_ONLY -> 1
            ConsumerImpact.INFORMATIONAL -> 2
        }

    /** Full pass fails when a ship-blocker hits a blocking gap class. */
    fun blocksFullPass(gap: GapClass, impact: ConsumerImpact): Boolean {
        if (impact != ConsumerImpact.SHIP_BLOCKER) return false
        return gap in
            setOf(
                GapClass.GAP_ADVERTISED_NOT_PROVEN,
                GapClass.GAP_DELIVERY_MISMATCH,
                GapClass.GAP_REGRESSION_SINCE_BASELINE,
            )
    }

    fun isExcludedFromGapCount(gap: GapClass): Boolean =
        gap in
            setOf(
                GapClass.OK,
                GapClass.GAP_PROBE_INVENTORY,
                GapClass.GAP_PLANNED,
                GapClass.GAP_HUMAN_ONLY,
                GapClass.GAP_FLAKE_SUSPECT,
                GapClass.GAP_FLEET_PLUGIN_CANDIDATE,
                GapClass.GAP_CONFLICT_RISK,
                GapClass.GAP_UNAUTOMATED,
                GapClass.GAP_ADVERTISED_NOT_SURFACED,
                GapClass.GAP_SURFACED_NOT_ADVERTISED,
                GapClass.GAP_PROVEN_NOT_ADVERTISED,
            )
}
