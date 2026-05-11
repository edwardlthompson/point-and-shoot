package dev.pointandshoot

/**
 * Pure-data performance-budget evaluator. Encodes the budgets defined in
 * [PERFORMANCE_BUDGETS.md](../../../../../../PERFORMANCE_BUDGETS.md) so the
 * engine can self-grade once telemetry exists.
 *
 * The thresholds here are the **defaults** - tests typically supply explicit
 * numbers to keep the contract pinned in case the markdown drifts. The split
 * between the two is intentional: the markdown is the human-facing source of
 * truth; this file is the machine-checkable mirror.
 *
 * Result classes are an enum + a small data class so callers can render them
 * as colored chips ("OK" / "WARN" / "FAIL") in the HUD or as a row in
 * `pns_hfr_autorun.ps1 --PerfReport` output. No Android imports.
 */
object PerfBudget {

    /** Defaults sourced from `PERFORMANCE_BUDGETS.md` (Standard Pro). */
    object Defaults {
        const val COLD_START_MS: Long = 800L
        const val DNG_SAVE_STANDARD_MS: Long = 250L
        const val DNG_SAVE_ULTRAMAX_MS: Long = 600L
        const val BRACKET7_TOTAL_MS: Long = 4_000L
        const val POST_READOUT_TICK_TARGET_MS: Long = 30L
        const val POST_READOUT_TICK_TOLERANCE_MS: Long = 5L

        /**
         * Max wall time to wait for the still encode executor (`PNS.Reader` / `ioExecutor`)
         * to finish in-flight work before starting a sequential RAW bracket. Mirrors
         * `CAPTURE_ARCHITECTURE.md` and `PreviewEngineScreen` BKT preflight; on timeout the
         * engine logs **`encode_lane_busy`** and surfaces **"Engine busy - retry"**.
         */
        const val ENCODE_LANE_DRAIN_WAIT_MS: Long = 200L

        /**
         * `ImageReader.newInstance` **maxImages** for still / metering readers (`PNS.Reader` lane).
         * See **`CAPTURE_ARCHITECTURE.md`** — bounded queue; supersede / HAL lag logs **`drop oldest`**.
         */
        const val STILL_IMAGE_READER_MAX_IMAGES: Int = 4

        /**
         * GLES preview / video LUT shader cost per 1080p frame. Pinned at 2 ms
         * so a 33^3 LUT applied via `sampler3D` at 60 fps consumes < 12 % of
         * the per-frame budget. BUILD_PLAN §7 V&V gate: "enabling/disabling a
         * 33^3 LUT does not drop preview FPS by more than 5 %".
         */
        const val LUT_SHADER_PER_FRAME_1080P_MS: Long = 2L

        /**
         * CPU LUT-apply pass for a single 12 MP still (4000 x 3000 pixels).
         * Pinned at 80 ms so the still-encode lane (DNG save 250 ms +
         * tone curve + LUT) stays comfortably inside the BUILD_PLAN
         * "tap-to-write < 1 s" budget. Per-pixel cost target: ~ 6.7 ns.
         */
        const val LUT_CPU_STILL_12MP_MS: Long = 80L
    }

    /**
     * Generic single-threshold evaluator. Returns:
     *   * OK   if `measured <= budget`
     *   * WARN if `budget < measured <= budget * warnFactor`
     *   * FAIL if `measured > budget * warnFactor`
     *
     * @param warnFactor 1.0..n - how far over budget is tolerated as a WARN.
     *                   Defaults to 1.25 (25% slack).
     */
    fun check(
        label: String,
        measured: Long,
        budget: Long,
        warnFactor: Double = 1.25,
    ): BudgetCheck {
        require(budget > 0) { "budget must be > 0 (was $budget)" }
        require(warnFactor >= 1.0) { "warnFactor must be >= 1.0 (was $warnFactor)" }
        val warnCutoff = (budget * warnFactor).toLong()
        val severity = when {
            measured <= budget -> BudgetSeverity.Ok
            measured <= warnCutoff -> BudgetSeverity.Warn
            else -> BudgetSeverity.Fail
        }
        return BudgetCheck(
            label = label,
            measuredMs = measured,
            budgetMs = budget,
            severity = severity,
        )
    }

    /**
     * Tolerance-band evaluator for the post-readout haptic tick: the spec
     * requires `POST_READOUT_TICK_TARGET_MS \u00b1 POST_READOUT_TICK_TOLERANCE_MS`.
     */
    fun checkHapticTick(
        measuredMs: Long,
        targetMs: Long = Defaults.POST_READOUT_TICK_TARGET_MS,
        toleranceMs: Long = Defaults.POST_READOUT_TICK_TOLERANCE_MS,
    ): BudgetCheck {
        require(targetMs > 0) { "targetMs must be > 0 (was $targetMs)" }
        require(toleranceMs >= 0) { "toleranceMs must be >= 0 (was $toleranceMs)" }
        val low = targetMs - toleranceMs
        val high = targetMs + toleranceMs
        val severity = when {
            measuredMs in low..high -> BudgetSeverity.Ok
            // Within twice the tolerance band - WARN.
            measuredMs in (targetMs - 2 * toleranceMs)..(targetMs + 2 * toleranceMs) -> BudgetSeverity.Warn
            else -> BudgetSeverity.Fail
        }
        return BudgetCheck(
            label = "post-readout haptic tick",
            measuredMs = measuredMs,
            budgetMs = targetMs,
            severity = severity,
        )
    }
}

enum class BudgetSeverity { Ok, Warn, Fail }

data class BudgetCheck(
    val label: String,
    val measuredMs: Long,
    val budgetMs: Long,
    val severity: BudgetSeverity,
)
