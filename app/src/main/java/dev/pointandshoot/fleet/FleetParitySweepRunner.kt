package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import dev.pointandshoot.HardwareCapsSnapshot
import dev.pointandshoot.RootCapabilityStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app Fleet Parity Sweep orchestrator (Milestone **18.6** + **21**).
 */
object FleetParitySweepRunner {
    private const val ADB_TAG = "PNS.AdbValidation"
    const val PARITY_REPORT_FILE_PREFIX = "parity_report_"

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
        val matrix: JSONObject,
        val encoderCrossCheck: JSONObject? = null,
        val sessionTemplates: JSONArray? = null,
        val conflictPairs: JSONArray? = null,
    ) {
        fun toJson(): JSONObject {
            val durations = cells.map { it.durationMs }.sorted()
            val p95Idx = ((durations.size - 1) * 0.95).toInt().coerceAtLeast(0)
            val timingSummary =
                JSONObject().apply {
                    put("p95CellMs", if (durations.isEmpty()) 0 else durations[p95Idx])
                    put("maxCellMs", durations.maxOrNull() ?: 0)
                    put(
                        "slowestCells",
                        JSONArray().apply {
                            cells.sortedByDescending { it.durationMs }.take(10).forEach { c ->
                                put(
                                    JSONObject().apply {
                                        put("catalogId", c.catalogId)
                                        put("durationMs", c.durationMs)
                                    },
                                )
                            }
                        },
                    )
                }
            val formatPickerCells =
                cells.filter { cell ->
                    CameraCapabilityCatalog.registry.firstOrNull { it.id == cell.catalogId }?.surfacing?.contains("format_picker") == true
                }
            val formatScore =
                if (formatPickerCells.isEmpty()) {
                    100
                } else {
                    val ok = formatPickerCells.count { it.provenOk && it.advertised }
                    (ok * 100 / formatPickerCells.size)
                }
            val scanMeta = matrix.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
            return JSONObject().apply {
                put("schema", "pns.fleet_parity_sweep.v2")
                put("mode", mode.wire)
                put("durationMs", durationMs)
                put("cellCount", cells.size)
                put("catalogVersion", matrix.optInt(FleetDeviceMatrix.KEY_CATALOG_VERSION, CameraCapabilityCatalog.CATALOG_VERSION))
                put("matrixSchemaVersion", matrix.optInt(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION))
                put("scanTier", scanMeta?.optString("scanTier") ?: JSONObject.NULL)
                put("appVersionCode", scanMeta?.optInt("appVersionCode") ?: JSONObject.NULL)
                put("fingerprintSha256Prefix", scanMeta?.optString("fingerprintSha256Prefix") ?: JSONObject.NULL)
                put("formatPickerHonestyScore", formatScore)
                put("stillResolutionAdvertised", matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("stillResolutionAdvertised") ?: JSONArray())
                put(
                    "experimentalUnlockState",
                    matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("experimentalUnlockState")
                        ?: JSONObject.NULL,
                )
                put("timingSummary", timingSummary)
                val gaps = JSONObject()
                gapCounts.forEach { (k, v) -> gaps.put(k.name, v) }
                put("gapCounts", gaps)
                put("gapBreakdown", gaps)
                val shipBlockers =
                    cells.count { c ->
                        val row = CameraCapabilityCatalog.registry.firstOrNull { it.id == c.catalogId }
                        val gap = row?.let { FleetParitySweep.classify(c, it.appStatus, it) } ?: FleetParitySweep.GapClass.OK
                        FleetParitySweep.blocksFullPass(gap, c.consumerImpact)
                    }
                put("shipBlockerGapCount", shipBlockers)
                put(
                    "cells",
                    JSONArray().apply {
                        cells.forEach { c -> put(cellToJson(c)) }
                    },
                )
                encoderCrossCheck?.let { put("encoderCrossCheck", it) }
                sessionTemplates?.let { put("sessionTemplates", it) }
                conflictPairs?.let { put("conflictPairs", it) }
            }
        }
    }

    private fun cellToJson(c: FleetParitySweep.ParityCellResult): JSONObject =
        JSONObject().apply {
            put("catalogId", c.catalogId)
            put("advertised", c.advertised)
            put("provenOk", c.provenOk)
            put("sessionOk", c.sessionOk ?: JSONObject.NULL)
            put("consumerImpact", c.consumerImpact.name)
            put("failReason", c.failReason ?: JSONObject.NULL)
            put("durationMs", c.durationMs)
            c.conflictId?.let { put("conflictId", it) }
            c.gapClass?.let { put("gap", it.name) }
        }

    private val quickCellIds: Set<String> =
        setOf(
            "raw.dng",
            "still.zsl",
            "still.bracket",
            "still.referenceapp_leaf",
            "still.nightscape",
            "still.avif",
            "video.h264",
            "video.hevc",
            "video.hfr",
            "video.regular.1080p30",
            "video.dcg_hdr",
            "video.uhd60",
            "video.vp9",
            "video.av1",
            "video.raw",
            "video.raw_picker",
            "video.dual_iso",
            "video.dual",
            "video.multicam_melt",
            "video.timelapse",
            "face.detect",
            "face.eye_af",
            "face.priority_ae",
            "af.manual",
            "af.rack",
            "hud.zebra",
            "hud.histogram",
            "hud.highlight_meter",
            "hud.focus_peaking",
            "preview.qr",
            "preview.ae_lock",
            "preview.pip",
            "lens.multi",
            "lens.focal_row",
            "lens.uw",
            "lens.wide",
            "lens.tele",
            "lens.ois",
            "lens.eis",
            "focal.slot.14",
            "focal.slot.23",
            "focal.slot.73",
            "fleet.matrix",
            "fleet.parity_sweep",
            "root.max_res_unlock_cph2583",
            "product.format_picker",
            "tether.http",
            "tether.wifi_direct",
            "audio.hifi",
            "audio.wind_filter",
            "audio.spatial",
            "perf.capture_latency",
            "perf.battery_adaptive_fps",
            "workflow.preset.street",
            "workflow.preset.portrait",
            "workflow.preset.video_log",
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
                runCell(context, ev, matrix, mode, includeRecord)
            }
        val conflictJson = emitConflictRows(cells)
        val gapCounts =
            cells.groupingBy { c ->
                val row = CameraCapabilityCatalog.registry.first { it.id == c.catalogId }
                FleetParitySweep.classify(c, row.appStatus, row)
            }.eachCount()
        val durationMs = (System.nanoTime() - t0) / 1_000_000L
        val gapsByClass = gapCounts.entries.joinToString(",") { "${it.key.name}=${it.value}" }
        val stillResRows =
            matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONArray("stillResolutionAdvertised")
                ?.length() ?: 0
        Log.i(FleetParitySweep.TAG, "sweepComplete mode=${mode.wire} cells=${cells.size} ms=$durationMs gapsByClass=$gapsByClass")
        Log.i(FleetParitySweep.TAG, "sweepStillResolutionAdvertised rows=$stillResRows")
        val encoderCross = if (mode == Mode.FULL) FleetParityEncoderCrossCheck.build(matrix) else null
        val sessionTemplates =
            if (mode == Mode.FULL) {
                FleetSessionTemplateCoverage.build(matrix, cells.map { it.catalogId })
            } else {
                null
            }
        return SweepReport(
            mode = mode,
            cells = cells,
            gapCounts = gapCounts,
            durationMs = durationMs,
            matrix = matrix,
            encoderCrossCheck = encoderCross,
            sessionTemplates = sessionTemplates,
            conflictPairs = conflictJson,
        )
    }

    private fun emitConflictRows(cells: List<FleetParitySweep.ParityCellResult>): JSONArray {
        val advertised = cells.filter { it.advertised }.map { it.catalogId }.toSet()
        val arr = JSONArray()
        for (pair in FleetParityConflictMatrix.activeConflicts(advertised)) {
            Log.i(FleetParitySweep.TAG, "conflictId=${pair.id} left=${pair.leftId} right=${pair.rightId}")
            arr.put(
                JSONObject().apply {
                    put("conflictId", pair.id)
                    put("leftId", pair.leftId)
                    put("rightId", pair.rightId)
                    put("note", pair.note)
                },
            )
        }
        return arr
    }

    private fun runCell(
        context: Context,
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        matrix: JSONObject,
        mode: Mode,
        includeRecord: Boolean,
    ): FleetParitySweep.ParityCellResult {
        val row = evaluated.row
        val t0 = System.nanoTime()
        val impact = FleetParityConsumerImpact.resolve(row)
        if (row.sweepSkipReason != null) {
            return skippedCell(row, row.sweepSkipReason, System.nanoTime() - t0, impact)
        }
        if (row.appStatus == CameraCapabilityCatalog.AppStatus.Planned) {
            return plannedCell(row.id, System.nanoTime() - t0, impact)
        }
        val advertised = evaluated.deviceSupported
        val provenOk = proveOk(evaluated, matrix, mode, includeRecord)
        val surfacingFail = surfacingGap(context, evaluated, provenOk, matrix)
        val failReason =
            when {
                surfacingFail != null -> surfacingFail
                provenOk -> null
                !advertised -> "not_advertised"
                evaluated.sessionOk == false -> "session_failed"
                row.parityProofScript == null &&
                    (row.appStatus == CameraCapabilityCatalog.AppStatus.Partial ||
                        row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped) &&
                    mode == Mode.FULL ->
                    "unautomated"
                else -> "not_proven"
            }
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = row.id,
                advertised = advertised,
                sessionOk = evaluated.sessionOk,
                appEnabled = row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped ||
                    row.appStatus == CameraCapabilityCatalog.AppStatus.Partial,
                provenOk = provenOk && surfacingFail == null,
                failReason = failReason,
                durationMs = (System.nanoTime() - t0) / 1_000_000L,
                consumerImpact = impact,
                deliveryProbe = deliveryProbeForRow(row, includeRecord, mode),
            )
        logCell(cell, mode, row)
        return cell
    }

    private fun surfacingGap(
        context: Context,
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        provenOk: Boolean,
        matrix: JSONObject,
    ): String? {
        if (!evaluated.deviceSupported || !provenOk) return null
        if (evaluated.row.surfacing.isEmpty()) return null
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList.toList()
        val visCtx =
            FleetUiVisibilityGate.VisibilityContext(
                matrix = matrix,
                caps = HardwareCapsSnapshot.build(cm, ids.firstOrNull(), ids),
                rootGranted = RootCapabilityStore.loadOrUnknown(context).grantsPrivileged,
                activeCameraId = ids.firstOrNull(),
            )
        val visible = FleetUiVisibilityGate.visible(evaluated.row.id, visCtx)
        return if (!visible && evaluated.row.visibilityPolicy == CameraCapabilityCatalog.VisibilityPolicy.HideWhenUnavailable) {
            "advertised_not_surfaced"
        } else {
            null
        }
    }

    private fun proveOk(
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        matrix: JSONObject,
        mode: Mode,
        includeRecord: Boolean,
    ): Boolean {
        val row = evaluated.row
        if (row.appStatus == CameraCapabilityCatalog.AppStatus.ProbeOnly) {
            return evaluated.deviceSupported
        }
        if (row.id.startsWith("workflow.preset.")) {
            return when (mode) {
                Mode.FULL -> evaluated.deviceSupported && evaluated.sessionOk == true
                else -> evaluated.deviceSupported && row.parityProofScript != null
            }
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
                when {
                    mode == Mode.FULL && row.parityProofScript != null && evaluated.sessionOk != true ->
                        false
                    else ->
                        evaluated.deviceSupported &&
                            (row.parityProofScript != null || evaluated.sessionOk == true)
                }
            else -> false
        }
    }

    private fun deliveryProbeForRow(
        row: CameraCapabilityCatalog.CatalogRow,
        includeRecord: Boolean,
        mode: Mode,
    ): FleetParitySweep.DeliveryProbe? {
        if (!includeRecord || mode != Mode.FULL || !row.id.startsWith("video.")) return null
        Log.i(
            FleetParitySweep.TAG,
            "deliveryProbeDeferred catalogId=${row.id} (host: pns_in_app_video_verify.ps1 or Full -IncludeRecord)",
        )
        return null
    }

    private fun focalSlotCount(matrix: JSONObject): Int =
        matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots")?.length() ?: 0

    private fun skippedCell(
        row: CameraCapabilityCatalog.CatalogRow,
        reason: String,
        elapsedNs: Long,
        impact: FleetParitySweep.ConsumerImpact,
    ): FleetParitySweep.ParityCellResult {
        val provenOk = reason == "probe_only_inventory" && row.appStatus == CameraCapabilityCatalog.AppStatus.ProbeOnly
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = row.id,
                advertised = false,
                sessionOk = null,
                appEnabled = false,
                provenOk = provenOk,
                failReason = "skip:$reason",
                durationMs = elapsedNs / 1_000_000L,
                consumerImpact = impact,
            )
        logCell(cell, Mode.QUICK, row)
        return cell
    }

    private fun plannedCell(
        id: String,
        elapsedNs: Long,
        impact: FleetParitySweep.ConsumerImpact,
    ): FleetParitySweep.ParityCellResult {
        val row = CameraCapabilityCatalog.registry.first { it.id == id }
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = id,
                advertised = false,
                sessionOk = null,
                appEnabled = false,
                provenOk = false,
                failReason = "planned",
                durationMs = elapsedNs / 1_000_000L,
                consumerImpact = impact,
            )
        logCell(cell, Mode.FULL, row)
        return cell
    }

    fun logCell(
        cell: FleetParitySweep.ParityCellResult,
        mode: Mode,
        row: CameraCapabilityCatalog.CatalogRow? = CameraCapabilityCatalog.registry.firstOrNull { it.id == cell.catalogId },
    ) {
        val gap =
            row?.let { FleetParitySweep.classify(cell, it.appStatus, it) }?.name ?: "UNKNOWN"
        val line =
            "parityCell=catalogId=${cell.catalogId} advertised=${cell.advertised} " +
                "sessionOk=${cell.sessionOk} appEnabled=${cell.appEnabled} provenOk=${cell.provenOk} " +
                "gap=$gap impact=${cell.consumerImpact.name} failReason=${cell.failReason ?: ""} durationMs=${cell.durationMs}"
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
                    val gap = FleetParitySweep.classify(cell, row.appStatus, row)
                    Triple(gap, cell, row)
                }
                .sortedWith(
                    compareBy<Triple<FleetParitySweep.GapClass, FleetParitySweep.ParityCellResult, CameraCapabilityCatalog.CatalogRow>> {
                        FleetParitySweep.closurePlanPriority(it.first)
                    }.thenBy {
                        FleetParitySweep.consumerImpactPriority(it.second.consumerImpact)
                    },
                )
        fun section(title: String, filter: (FleetParitySweep.ConsumerImpact) -> Boolean) {
            val sectionRows =
                prioritized.filter { (gap, cell, _) ->
                    filter(cell.consumerImpact) && gap != FleetParitySweep.GapClass.OK
                }
            if (sectionRows.isEmpty()) return
            lines += "## $title"
            lines += ""
            for ((gap, cell, row) in sectionRows) {
                val effort = row.closureEffort ?: row.parityProofScript ?: "review"
                val sprint = row.buildPlanSprint?.let { " sprint=$it" } ?: ""
                lines += "- **${cell.catalogId}** (`$gap`, ${cell.consumerImpact.name}) — ${row.displayName}; $effort$sprint; ${cell.failReason ?: "review"}"
            }
            lines += ""
        }
        section("Ship blockers", { it == FleetParitySweep.ConsumerImpact.SHIP_BLOCKER })
        section("Engineering", { it == FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY })
        section("Informational", { it == FleetParitySweep.ConsumerImpact.INFORMATIONAL })
        if (lines.size <= 2) lines += "- No gaps — parity OK"
        val text = lines.joinToString("\n")
        FleetParityChromeLint.assertClosurePlanSafe(text)
        return text
    }

    fun reportFileName(mode: Mode): String = "${PARITY_REPORT_FILE_PREFIX}${mode.wire}.json"
}
