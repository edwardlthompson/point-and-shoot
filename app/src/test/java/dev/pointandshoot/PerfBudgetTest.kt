package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PerfBudgetTest {

    @Test
    fun `under or at budget is OK`() {
        val r = PerfBudget.check("dng-save", measured = 200, budget = 250)
        assertEquals(BudgetSeverity.Ok, r.severity)
        assertEquals(200L, r.measuredMs)
        assertEquals(250L, r.budgetMs)

        val atLimit = PerfBudget.check("dng-save", measured = 250, budget = 250)
        assertEquals(BudgetSeverity.Ok, atLimit.severity)
    }

    @Test
    fun `slightly over budget within warnFactor is WARN`() {
        // budget = 250, warnFactor 1.25 -> warnCutoff = 312
        val r = PerfBudget.check("dng-save", measured = 300, budget = 250)
        assertEquals(BudgetSeverity.Warn, r.severity)

        val atWarnCutoff = PerfBudget.check("dng-save", measured = 312, budget = 250)
        assertEquals(BudgetSeverity.Warn, atWarnCutoff.severity)
    }

    @Test
    fun `well over warnFactor is FAIL`() {
        val r = PerfBudget.check("dng-save", measured = 1_000, budget = 250)
        assertEquals(BudgetSeverity.Fail, r.severity)
    }

    @Test
    fun `custom warnFactor widens the WARN band`() {
        // budget = 100, warnFactor = 2.0 -> warnCutoff = 200.
        val warn = PerfBudget.check("custom", measured = 199, budget = 100, warnFactor = 2.0)
        assertEquals(BudgetSeverity.Warn, warn.severity)
        val fail = PerfBudget.check("custom", measured = 201, budget = 100, warnFactor = 2.0)
        assertEquals(BudgetSeverity.Fail, fail.severity)
    }

    @Test
    fun `non-positive budget or sub-1 warnFactor are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PerfBudget.check("x", measured = 10, budget = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerfBudget.check("x", measured = 10, budget = 100, warnFactor = 0.5)
        }
    }

    @Test
    fun `haptic tick within tolerance is OK`() {
        val r = PerfBudget.checkHapticTick(measuredMs = 32) // target 30 +/- 5
        assertEquals(BudgetSeverity.Ok, r.severity)
    }

    @Test
    fun `haptic tick just outside tolerance is WARN`() {
        val r = PerfBudget.checkHapticTick(measuredMs = 38) // outside +/-5, inside +/-10
        assertEquals(BudgetSeverity.Warn, r.severity)
    }

    @Test
    fun `haptic tick way outside tolerance is FAIL`() {
        val r = PerfBudget.checkHapticTick(measuredMs = 200)
        assertEquals(BudgetSeverity.Fail, r.severity)
    }

    @Test
    fun `defaults match documented PERFORMANCE_BUDGETS`() {
        // Pin the most safety-critical values so accidental code-side drift gets caught.
        assertEquals(800L, PerfBudget.Defaults.COLD_START_MS)
        assertEquals(250L, PerfBudget.Defaults.DNG_SAVE_STANDARD_MS)
        assertEquals(600L, PerfBudget.Defaults.DNG_SAVE_ULTRAMAX_MS)
        assertEquals(4_000L, PerfBudget.Defaults.BRACKET7_TOTAL_MS)
        assertEquals(30L, PerfBudget.Defaults.POST_READOUT_TICK_TARGET_MS)
        assertEquals(5L, PerfBudget.Defaults.POST_READOUT_TICK_TOLERANCE_MS)
        assertEquals(200L, PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS)
        assertEquals(8, PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES)
    }

    @Test
    fun `LUT defaults match documented PERFORMANCE_BUDGETS Phase 4`() {
        // Pin Phase 4 LUT-stage budgets so accidental code-side drift gets caught.
        assertEquals(2L, PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS)
        assertEquals(80L, PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS)
    }

    @Test
    fun `LUT shader budget grades 60 fps preview correctly`() {
        // 1.8 ms per frame: under the 2 ms budget -> OK.
        val ok = PerfBudget.check("lut-shader-1080p", measured = 1L, budget = PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS)
        assertEquals(BudgetSeverity.Ok, ok.severity)
        // 2.5 ms: warnFactor 1.25 -> warnCutoff = 2 ms (truncated to 2L by Long math).
        // Use explicit warnFactor for the WARN test so the cutoff is 3 ms.
        val warn = PerfBudget.check("lut-shader-1080p", measured = 3L,
            budget = PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS, warnFactor = 1.5)
        assertEquals(BudgetSeverity.Warn, warn.severity)
        // 5 ms: 250 % over budget -> FAIL.
        val fail = PerfBudget.check("lut-shader-1080p", measured = 5L, budget = PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS)
        assertEquals(BudgetSeverity.Fail, fail.severity)
    }

    @Test
    fun `LUT CPU still budget grades 12 MP capture correctly`() {
        val ok = PerfBudget.check("lut-cpu-12mp", measured = 60L, budget = PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS)
        assertEquals(BudgetSeverity.Ok, ok.severity)
        val atLimit = PerfBudget.check("lut-cpu-12mp", measured = 80L, budget = PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS)
        assertEquals(BudgetSeverity.Ok, atLimit.severity)
        val warn = PerfBudget.check("lut-cpu-12mp", measured = 90L, budget = PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS)
        assertEquals(BudgetSeverity.Warn, warn.severity)
        val fail = PerfBudget.check("lut-cpu-12mp", measured = 200L, budget = PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS)
        assertEquals(BudgetSeverity.Fail, fail.severity)
    }
}
