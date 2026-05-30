package dev.pointandshoot.fleet

/**
 * Fleet Parity Sweep — gap classification and cell model (Milestone **18.6**).
 *
 * Host script `pns_fleet_parity_sweep.ps1` parses `PNS.FleetParity` / `PNS.AdbValidation parityCell=`.
 */
object FleetParitySweep {
    const val TAG = "PNS.FleetParity"

    enum class GapClass {
        OK,
        GAP_ADVERTISED_NOT_PROVEN,
        GAP_DELIVERY_MISMATCH,
        GAP_PROVEN_NOT_ADVERTISED,
        GAP_PLANNED,
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
    )

    fun classify(cell: ParityCellResult, appStatus: CameraCapabilityCatalog.AppStatus): GapClass =
        when {
            appStatus == CameraCapabilityCatalog.AppStatus.Planned ->
                GapClass.GAP_PLANNED
            cell.deliveryProbe != null && !cell.deliveryProbe.matchOk ->
                GapClass.GAP_DELIVERY_MISMATCH
            cell.advertised && !cell.provenOk ->
                GapClass.GAP_ADVERTISED_NOT_PROVEN
            !cell.advertised && cell.provenOk ->
                GapClass.GAP_PROVEN_NOT_ADVERTISED
            cell.provenOk ->
                GapClass.OK
            else ->
                GapClass.GAP_ADVERTISED_NOT_PROVEN
        }

    /** Sort closure-plan rows: delivery mismatch and advertised-not-proven first. */
    fun closurePlanPriority(gap: GapClass): Int =
        when (gap) {
            GapClass.GAP_DELIVERY_MISMATCH -> 0
            GapClass.GAP_ADVERTISED_NOT_PROVEN -> 1
            GapClass.GAP_PROVEN_NOT_ADVERTISED -> 2
            GapClass.GAP_PLANNED -> 3
            GapClass.OK -> 4
        }
}
