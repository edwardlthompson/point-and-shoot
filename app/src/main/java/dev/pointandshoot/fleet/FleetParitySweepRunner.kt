package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import dev.pointandshoot.HardwareCapsSnapshot
import dev.pointandshoot.InAppVideoFormatSelection
import dev.pointandshoot.RootCapabilityStore
import dev.pointandshoot.VideoDeliveryHonesty
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app Fleet Parity Sweep orchestrator (Milestone **18.6** + **21**).
 */
object FleetParitySweepRunner {
    private const val ADB_TAG = "PNS.AdbValidation"
    const val PARITY_REPORT_FILE_PREFIX = "parity_report_"

    /** Catalog rows backed by matrix featureGates.sessionOk — never proven on quick-tier matrix alone. */
    internal val SESSION_GATED_CATALOG_IDS: Set<String> =
        setOf(
            "raw.dng",
            "video.hfr",
            "video.uhd60",
            "video.4k_regular",
            "video.dcg_hdr",
            "video.av1",
            "video.raw",
            "video.raw_picker",
            "face.detect",
            "face.eye_af",
            "face.priority_ae",
        )

    enum class Mode(val wire: String) {
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
            val resolutionBetrayalIndex = ResolutionBetrayal.computeFromMatrix(matrix)
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
                put("resolutionBetrayalIndex", resolutionBetrayalIndex)
                put("stillResolutionAdvertised", matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("stillResolutionAdvertised") ?: JSONArray())
                put(
                    "measurementContext",
                    JSONObject().apply {
                        put("api", "camera2")
                        put("cameraXProbed", matrix.optJSONObject(FleetDeviceMatrix.KEY_CAMERA_X) != null)
                        put("oemCameraAppTested", false)
                    },
                )
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
        val impact = FleetParityConsumerImpact.resolve(row, matrix)
        val skipReason = row.sweepSkipReason
        if (skipReason != null) {
            return skippedCell(row, skipReason, System.nanoTime() - t0, impact)
        }
        if (row.appStatus == CameraCapabilityCatalog.AppStatus.Planned) {
            return plannedCell(row.id, System.nanoTime() - t0, impact)
        }
        val advertised = evaluated.deviceSupported
        val provenOk = proveOk(context, evaluated, matrix, mode, includeRecord)
        val surfacingFail = surfacingGap(context, evaluated, provenOk, matrix)
        val failReason =
            when {
                surfacingFail != null -> surfacingFail
                provenOk -> null
                !advertised -> "not_advertised"
                evaluated.sessionOk == null &&
                    row.id in SESSION_GATED_CATALOG_IDS &&
                    FleetDeviceMatrix.parseScanTier(matrix) != FleetDeviceMatrix.ScanTier.FULL ->
                    "matrix_tier_quick"
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
        if (isFamilySurfacingRow(evaluated.row)) return null
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList.toList()
        val visCtx =
            FleetUiVisibilityGate.VisibilityContext(
                matrix = matrix,
                caps = HardwareCapsSnapshot.build(cm, ids.firstOrNull(), ids),
                rootGranted = RootCapabilityStore.loadOrUnknown(context).grantsPrivileged,
                activeCameraId = ids.firstOrNull(),
            )
        val visible =
            FleetUiVisibilityGate.visibleWithAdvertisedFallback(
                featureId = evaluated.row.id,
                ctx = visCtx,
                advertisedFallback = evaluated.deviceSupported,
            )
        return if (!visible && evaluated.row.visibilityPolicy == CameraCapabilityCatalog.VisibilityPolicy.HideWhenUnavailable) {
            "advertised_not_surfaced"
        } else {
            null
        }
    }

    /**
     * Expanded catalog families share a single consumer control (for example one format picker chip)
     * and are not independently auditable as surfaced/hidden per catalog row id.
     */
    private fun isFamilySurfacingRow(row: CameraCapabilityCatalog.CatalogRow): Boolean {
        val familySurfacing = row.surfacing.any { it == "format_picker" || it == "qs_grid" || it == "settings" }
        if (!familySurfacing) return false
        return row.id.startsWith("video.") || row.id.startsWith("audio.") || row.id.startsWith("hud.")
    }

    private fun proveDeliveryHonesty(context: Context): Boolean {
        val hsMap = VideoDeliveryHonesty.wideHighSpeedMap(context)
        val catalog =
            InAppVideoFormatSelection.loadCatalog(
                supportsDcg = false,
                highSpeedMap = hsMap,
            )
        return VideoDeliveryHonesty.isCatalogHonest(catalog, hsMap)
    }

    private fun proveStillHeic(evaluated: CameraCapabilityCatalog.EvaluatedRow): Boolean =
        evaluated.deviceSupported &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            evaluated.sessionOk != false

    private fun proveOk(
        context: Context,
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
        if (row.id == "video.delivery_honesty") return proveDeliveryHonesty(context)
        if (row.id == "still.heic") return proveStillHeic(evaluated)
        return proveOkFromEvaluated(evaluated, matrix, mode)
    }

    private fun proveOkFromEvaluated(
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        matrix: JSONObject,
        mode: Mode,
    ): Boolean {
        val row = evaluated.row
        return when {
            evaluated.sessionOk == true &&
                row.id !in setOf("video.dual", "video.multicam_melt", "preview.pip") -> true
            evaluated.sessionOk == false -> false
            row.id == "video.dual" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            row.id == "video.multicam_melt" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            row.id == "preview.pip" ->
                evaluated.deviceSupported && evaluated.sessionOk != false
            row.id == "fleet.matrix" -> FleetDeviceMatrix.isValidRoot(matrix)
            row.id == "fleet.parity_sweep" -> true
            row.id.startsWith("lens.focal") || row.id.startsWith("focal.slot") ->
                matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.has("focalRow") == true ||
                    focalSlotCount(matrix) > 0
            row.id.startsWith("tether.") -> true
            row.id in SESSION_GATED_CATALOG_IDS &&
                evaluated.sessionOk == null &&
                FleetDeviceMatrix.parseScanTier(matrix) != FleetDeviceMatrix.ScanTier.FULL -> false
            row.appStatus == CameraCapabilityCatalog.AppStatus.Shipped -> evaluated.deviceSupported
            row.appStatus == CameraCapabilityCatalog.AppStatus.Partial ->
                provePartialRow(evaluated, mode)
            else -> false
        }
    }

    private fun provePartialRow(
        evaluated: CameraCapabilityCatalog.EvaluatedRow,
        mode: Mode,
    ): Boolean {
        val row = evaluated.row
        return when {
            mode == Mode.FULL &&
                row.parityProofScript != null &&
                isSessionGatedCatalogId(row.id) &&
                evaluated.sessionOk != true ->
                false
            else ->
                evaluated.deviceSupported &&
                    (row.parityProofScript != null || evaluated.sessionOk == true)
        }
    }

    private fun isSessionGatedCatalogId(catalogId: String): Boolean =
        catalogId in SESSION_GATED_CATALOG_IDS || catalogId.startsWith("video.av1.")

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
        logCell(cell, Mode.DELTA, row)
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
