package dev.pointandshoot

/**
 * Pure-data helper that produces the "Color & LUT" section of the
 * [DiagnosticsMode] dump per BUILD_PLAN §7 ("Diagnostics dump: include the
 * active calibration profile + LUT name + SHA256 in `diagnostics_<utc>.txt`
 * so on-device support tickets are reproducible").
 *
 * Runtime state (active LUT, active calibration profile) is supplied via
 * [ActiveColorState] so the engine can snapshot whatever it has at dump time
 * without coupling the diagnostics writer to the future Preferences-backed
 * setting. The catalog itself is enumerated from [LutCatalog] so the section
 * can never drift from what is actually bundled.
 *
 * No Android imports; safe for unit testing on the JVM.
 */
object LutDiagnosticsBuilder {

    /**
     * Build the "Color & LUT" section as a Markdown string suitable for
     * appending to the broader diagnostics dump. The string ends with a
     * trailing newline so the caller can concatenate without manual spacing.
     */
    fun buildSection(state: ActiveColorState): String {
        val sb = StringBuilder()
        sb.appendLine("## Color & LUT")

        // Active state.
        sb.appendLine("### Active state")
        sb.appendLine("- active LUT: ${state.activeLutName} (spdx=${state.activeLutSpdx ?: "n/a"})")
        sb.appendLine("- LUT sha256: ${state.activeLutSha256 ?: "n/a (code-generated or built-in)"}")
        sb.appendLine("- active calibration: ${state.calibrationProfileId ?: "(none - using factory color path)"}")
        if (state.calibrationCapturedAtUtc != null) {
            sb.appendLine("- calibration captured (UTC): ${state.calibrationCapturedAtUtc}")
        }
        sb.appendLine()

        // Bundled catalog (auto-derived).
        sb.appendLine("### Bundled LUT catalog (auto-derived from LutCatalog.kt)")
        for (entry in LutCatalog.entries) {
            sb.appendLine(
                "- ${entry.displayName}: spdx=${entry.spdx}, scope=${entry.scope.name}, source=${entry.source}",
            )
        }
        sb.appendLine()

        // Allowed-SPDX whitelist (for support tickets that need to know why a
        // user-imported LUT was rejected).
        sb.appendLine("### Allowed SPDX whitelist (LutCatalog.ALLOWED_SPDX)")
        sb.appendLine("- ${LutCatalog.ALLOWED_SPDX.sorted().joinToString(", ")}")
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Snapshot of the runtime color-pipeline state at the moment of a
     * diagnostics dump.
     *
     *   * [activeLutName] / [activeLutSpdx] / [activeLutSha256] describe the
     *     LUT currently applied to the preview / encode lanes. For the
     *     code-generated bundled LUTs, [activeLutSha256] is `null` (the SHA
     *     would be a hash of the runtime float buffer, not a checked-in file).
     *   * [calibrationProfileId] is the on-disk filename of the active
     *     [CalibrationProfile] (e.g. `wb-D65-2026-05-08T1830Z.json`) or
     *     `null` when no calibration has been applied.
     *   * [calibrationCapturedAtUtc] is an ISO-8601 timestamp string, or null.
     */
    data class ActiveColorState(
        val activeLutName: String,
        val activeLutSpdx: String?,
        val activeLutSha256: String?,
        val calibrationProfileId: String?,
        val calibrationCapturedAtUtc: String?,
    ) {
        companion object {
            /** Sentinel snapshot for "no calibration, identity LUT". */
            val Default: ActiveColorState = ActiveColorState(
                activeLutName = LutCatalog.None.displayName,
                activeLutSpdx = LutCatalog.None.spdx,
                activeLutSha256 = null,
                calibrationProfileId = null,
                calibrationCapturedAtUtc = null,
            )
        }
    }
}
