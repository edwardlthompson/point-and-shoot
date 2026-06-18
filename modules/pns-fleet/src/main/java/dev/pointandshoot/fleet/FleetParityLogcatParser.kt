package dev.pointandshoot.fleet

/**
 * Parses `PNS.FleetParity` / `PNS.AdbValidation` parityCell log lines (Milestone **21.0**).
 */
object FleetParityLogcatParser {
    private val cellRegex =
        Regex(
            """parityCell=catalogId=(\S+)\s+advertised=(\w+)\s+sessionOk=(\S+)\s+appEnabled=(\w+)\s+provenOk=(\w+)(?:\s+gap=(\S+))?(?:\s+impact=(\S+))?(?:\s+failReason=(\S*))?(?:\s+durationMs=(\d+))?""",
        )

    data class ParsedCell(
        val catalogId: String,
        val advertised: Boolean,
        val sessionOk: Boolean?,
        val appEnabled: Boolean,
        val provenOk: Boolean,
        val gap: String?,
        val impact: String?,
        val failReason: String?,
        val durationMs: Long,
    )

    fun parseLine(line: String): ParsedCell? {
        val m = cellRegex.find(line) ?: return null
        val sessionRaw = m.groupValues[3]
        val sessionOk =
            when (sessionRaw.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        return ParsedCell(
            catalogId = m.groupValues[1],
            advertised = m.groupValues[2].equals("true", ignoreCase = true),
            sessionOk = sessionOk,
            appEnabled = m.groupValues[4].equals("true", ignoreCase = true),
            provenOk = m.groupValues[5].equals("true", ignoreCase = true),
            gap = m.groupValues.getOrNull(6)?.takeIf { it.isNotBlank() },
            impact = m.groupValues.getOrNull(7)?.takeIf { it.isNotBlank() },
            failReason = m.groupValues.getOrNull(8)?.takeIf { it.isNotBlank() },
            durationMs = m.groupValues.getOrNull(9)?.toLongOrNull() ?: 0L,
        )
    }

    fun parseLog(text: String): List<ParsedCell> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<ParsedCell>()
        for (line in text.lineSequence()) {
            val cell = parseLine(line) ?: continue
            // Deduplicate mirrored FleetParity/AdbValidation copies of the same line, but keep
            // distinct rows that share catalogId (for example per-slot lens rows).
            val dedupeKey =
                listOf(
                    cell.catalogId,
                    cell.advertised.toString(),
                    cell.sessionOk?.toString() ?: "null",
                    cell.appEnabled.toString(),
                    cell.provenOk.toString(),
                    cell.gap ?: "",
                    cell.impact ?: "",
                    cell.failReason ?: "",
                    cell.durationMs.toString(),
                ).joinToString("|")
            if (seen.add(dedupeKey)) {
                out += cell
            }
        }
        return out
    }

    fun gapBreakdownFromCells(cells: List<ParsedCell>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (cell in cells) {
            val row = CameraCapabilityCatalog.registry.firstOrNull { it.id == cell.catalogId }
            val parityCell =
                FleetParitySweep.ParityCellResult(
                    catalogId = cell.catalogId,
                    advertised = cell.advertised,
                    sessionOk = cell.sessionOk,
                    appEnabled = cell.appEnabled,
                    provenOk = cell.provenOk,
                    failReason = cell.failReason,
                    durationMs = cell.durationMs,
                )
            val gap =
                cell.gap?.let { runCatching { FleetParitySweep.GapClass.valueOf(it) }.getOrNull() }
                    ?: row?.let { FleetParitySweep.classify(parityCell, it.appStatus, it) }
                    ?: FleetParitySweep.GapClass.GAP_ADVERTISED_NOT_PROVEN
            if (!FleetParitySweep.isExcludedFromGapCount(gap)) {
                counts[gap.name] = (counts[gap.name] ?: 0) + 1
            }
        }
        return counts
    }
}
