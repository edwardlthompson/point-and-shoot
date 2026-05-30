package dev.pointandshoot.fleet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app Fleet Parity Sweep orchestrator (Milestone **18.6**).
 */
object FleetParitySweepRunner {
    private const val ADB_TAG = "PNS.AdbValidation"

    enum class Mode(val wire: String) {
        QUICK("quick"),
        FULL("full"),
        DELTA("delta"),
    }

    data class SweepReport(
        val mode: Mode,
        val cells: List<FleetParitySweep.ParityCellResult>,
        val gapCounts: Map<FleetParitySweep.GapClass, Int>,
        val durationMs: Long,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("schema", "pns.fleet_parity_sweep.v1")
                put("mode", mode.wire)
                put("durationMs", durationMs)
                put("cellCount", cells.size)
                val gaps = JSONObject()
                gapCounts.forEach { (k, v) -> gaps.put(k.name, v) }
                put("gapCounts", gaps)
                put(
                    "cells",
                    JSONArray().apply {
                        cells.forEach { c ->
                            put(
                                JSONObject().apply {
                                    put("catalogId", c.catalogId)
                                    put("advertised", c.advertised)
                                    put("provenOk", c.provenOk)
                                    put("failReason", c.failReason ?: JSONObject.NULL)
                                },
                            )
                        }
                    },
                )
            }
    }

    private val quickCellIds: Set<String> =
        setOf(
            "raw.dng",
            "still.zsl",
            "still.bracket",
            "video.h264",
            "video.hevc",
            "video.hfr",
            "video.regular.1080p30",
            "video.dcg_hdr",
            "video.uhd60",
            "face.detect",
            "face.eye_af",
            "hud.zebra",
            "hud.histogram",
            "preview.qr",
            "lens.multi",
            "lens.focal_row",
            "lens.uw",
            "lens.wide",
            "lens.tele",
            "fleet.matrix",
            "fleet.parity_sweep",
            "video.dual",
            "video.multicam_melt",
            "preview.pip",
            "tether.http",
            "audio.hifi",
            "perf.capture_latency",
        )

    fun run(
        context: Context,
        matrix: JSONObject,
        mode: Mode,
        includeRecord: Boolean = false,
        deltaSinceCatalogVersion: Int? = null,
    ): SweepReport {
        val t0 = System.nanoTime()
        Log.i(FleetParitySweep.TAG, "sweepMode=${mode.wire} includeRecord=$includeRecord")
        val evaluated = CameraCapabilityCatalogBuilder.evaluatedRows(matrix)
        val rows =
            when (mode) {
                Mode.QUICK -> evaluated.filter { quickCellIds.contains(it.row.id) }
                Mode.FULL -> evaluated
                Mode.DELTA ->
                    evaluated.filter { row ->
                        row.row.appStatus == CameraCapabilityCatalog.AppStatus.Partial ||
                            row.row.appStatus == CameraCapabilityCatalog.AppStatus.Planned ||
                            (deltaSinceCatalogVersion != null &&
                                matrix.optInt(FleetDeviceMatrix.KEY_CATALOG_VERSION, 0) > deltaSinceCatalogVersion)
                    }
            }
        val cells =
            rows.map { ev ->
                runCell(ev, matrix, mode, includeRecord)
            }
        val gapCounts =
            cells.groupingBy { c ->
                val row = CameraCapabilityCatalog.registry.first { it.id == c.catalogId }
                FleetParitySweep.classify(c, row.appStatus)
            }.eachCount()
        val durationMs = (System.nanoTime() - t0) / 1_000_000L
        Log.i(FleetParitySweep.TAG, "sweepComplete mode=${mode.wire} cells=${cells.size} ms=$durationMs")
        return SweepReport(mode, cells, gapCounts, durationMs)
    }

    private fun runCell(
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        matrix: JSONObject,
        mode: Mode,
        includeRecord: Boolean,
    ): FleetParitySweep.ParityCellResult {
        val row = evaluated.row
        if (row.sweepSkipReason != null) {
            return skippedCell(row.id, row.sweepSkipReason)
        }
        if (row.appStatus == CameraCapabilityCatalog.AppStatus.Planned) {
            return plannedCell(row.id)
        }
        val advertised = evaluated.deviceSupported
        val provenOk = proveOk(evaluated, matrix, mode, includeRecord)
        val failReason =
            when {
                provenOk -> null
                !advertised -> "not_advertised"
                evaluated.sessionOk == false -> "session_failed"
                else -> "not_proven"
            }
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = row.id,
                advertised = advertised,
                sessionOk = evaluated.sessionOk,
                appEnabled = row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped ||
                    row.appStatus == CameraCapabilityCatalog.AppStatus.Partial,
                provenOk = provenOk,
                failReason = failReason,
            )
        logCell(cell, mode)
        return cell
    }

    private fun proveOk(
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        matrix: JSONObject,
        mode: Mode,
        includeRecord: Boolean,
    ): Boolean {
        if (!evaluated.deviceSupported && evaluated.row.appStatus == CameraCapabilityCatalog.AppStatus.ProbeOnly) {
            return false
        }
        return when {
            evaluated.sessionOk == true &&
                evaluated.row.id !in setOf("video.dual", "video.multicam_melt", "preview.pip") -> true
            evaluated.sessionOk == false -> false
            evaluated.row.id == "video.dual" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            evaluated.row.id == "video.multicam_melt" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            evaluated.row.id == "preview.pip" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            evaluated.row.id == "fleet.matrix" -> FleetDeviceMatrix.isValidRoot(matrix)
            evaluated.row.id == "fleet.parity_sweep" -> true
            evaluated.row.id.startsWith("lens.focal") || evaluated.row.id.startsWith("focal.slot") ->
                matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.has("focalRow") == true ||
                    focalSlotCount(matrix) > 0
            evaluated.row.id.startsWith("tether.") -> true
            evaluated.row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped -> evaluated.deviceSupported
            evaluated.row.appStatus == CameraCapabilityCatalog.AppStatus.Partial ->
                evaluated.deviceSupported || mode == Mode.FULL
            else -> false
        }
    }

    private fun focalSlotCount(matrix: JSONObject): Int =
        matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")?.length() ?: 0

    private fun skippedCell(id: String, reason: String): FleetParitySweep.ParityCellResult =
        FleetParitySweep.ParityCellResult(
            catalogId = id,
            advertised = false,
            sessionOk = null,
            appEnabled = false,
            provenOk = false,
            failReason = "skip:$reason",
        ).also { logCell(it, Mode.QUICK) }

    private fun plannedCell(id: String): FleetParitySweep.ParityCellResult =
        FleetParitySweep.ParityCellResult(
            catalogId = id,
            advertised = false,
            sessionOk = null,
            appEnabled = false,
            provenOk = false,
            failReason = "planned",
        ).also { logCell(it, Mode.FULL) }

    fun logCell(cell: FleetParitySweep.ParityCellResult, mode: Mode) {
        val row = CameraCapabilityCatalog.registry.firstOrNull { it.id == cell.catalogId }
        val gap =
            row?.let { FleetParitySweep.classify(cell, it.appStatus) }?.name ?: "UNKNOWN"
        val line =
            "parityCell=catalogId=${cell.catalogId} advertised=${cell.advertised} " +
                "sessionOk=${cell.sessionOk} appEnabled=${cell.appEnabled} provenOk=${cell.provenOk} " +
                "gap=$gap failReason=${cell.failReason ?: ""} durationMs=${cell.durationMs}"
        Log.i(FleetParitySweep.TAG, line)
        Log.i(ADB_TAG, line)
        cell.deliveryProbe?.let { probe ->
            Log.i(
                FleetParitySweep.TAG,
                "deliveryMismatch catalogId=${cell.catalogId} req=${probe.requestedWidth}x${probe.requestedHeight}@${probe.requestedFps} " +
                    "actual=${probe.actualWidth}x${probe.actualHeight}@${probe.actualFps} matchOk=${probe.matchOk} reason=${probe.mismatchReason}",
            )
        }
    }

    fun writeClosurePlan(report: SweepReport): String {
        val lines = mutableListOf("# Parity closure plan", "")
        val prioritized =
            report.cells
                .mapNotNull { cell ->
                    val row = CameraCapabilityCatalog.registry.firstOrNull { it.id == cell.catalogId } ?: return@mapNotNull null
                    val gap = FleetParitySweep.classify(cell, row.appStatus)
                    Triple(gap, cell, row)
                }
                .sortedBy { (gap, _, _) -> FleetParitySweep.closurePlanPriority(gap) }
        for ((gap, cell, row) in prioritized) {
            if (gap == FleetParitySweep.GapClass.OK) continue
            lines += "- **${cell.catalogId}** (`$gap`) — ${row.displayName}; ${cell.failReason ?: "review"}"
        }
        if (lines.size <= 2) lines += "- No gaps — parity OK"
        return lines.joinToString("\n")
    }
}
