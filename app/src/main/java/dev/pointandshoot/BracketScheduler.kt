package dev.pointandshoot

import android.util.Range
import android.util.Rational
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bridges [BracketPlan]'s engine-agnostic EV offsets to the integer values
 * that Camera2 expects on `CONTROL_AE_EXPOSURE_COMPENSATION`.
 *
 * Camera2 reports two characteristics that this helper consumes:
 *   * `CONTROL_AE_COMPENSATION_RANGE` -> `Range<Int>` (e.g., `[-12, 12]`)
 *   * `CONTROL_AE_COMPENSATION_STEP`  -> `Rational`  (e.g., `1/3` EV per unit)
 *
 * `EV per step` = `step.numerator / step.denominator`. Multiplying the
 * desired EV offset by `1 / EV-per-step` (i.e., dividing by the step) yields
 * the integer unit count to set on `CaptureRequest.Builder.set(...)`.
 *
 * The scheduler is **pure data**: trivially unit-testable with no Camera2
 * runtime needed (`Range` and `Rational` are pure JVM `android.util` classes
 * but available in the unit-test classpath via Android stub).
 */
object BracketScheduler {

    /**
     * Map a [BracketPlan] to the integer AE compensation values the engine
     * should set on each `CaptureRequest`. Values are clamped to [range]; if
     * the requested EV exceeds the range, it is silently clamped (the engine
     * may also surface a HUD warning).
     *
     * @param plan  the user-requested bracket (3/5/7).
     * @param step  camera's `CONTROL_AE_COMPENSATION_STEP` (e.g., 1/3 EV).
     * @param range camera's `CONTROL_AE_COMPENSATION_RANGE`.
     * @return integer AE compensation per shot in plan order.
     */
    fun aeStepsFor(
        plan: BracketPlan,
        step: Rational,
        range: Range<Int>,
    ): List<Int> = aeStepsFor(
        plan = plan,
        cameraStepNumerator = step.numerator,
        cameraStepDenominator = step.denominator,
        rangeLow = range.lower,
        rangeHigh = range.upper,
    )

    /**
     * Pure-primitive overload of [aeStepsFor] - same arithmetic without any
     * `android.util.Range` / `android.util.Rational` dependency, so it is
     * trivially unit-testable on the JVM.
     */
    fun aeStepsFor(
        plan: BracketPlan,
        cameraStepNumerator: Int,
        cameraStepDenominator: Int,
        rangeLow: Int,
        rangeHigh: Int,
    ): List<Int> {
        require(cameraStepNumerator != 0 && cameraStepDenominator != 0) {
            "step rational must be non-zero (was $cameraStepNumerator/$cameraStepDenominator)"
        }
        val evPerStep = cameraStepNumerator.toDouble() / cameraStepDenominator.toDouble()
        require(evPerStep > 0.0) {
            "step rational must be positive (was $cameraStepNumerator/$cameraStepDenominator)"
        }
        require(rangeLow <= rangeHigh) {
            "rangeLow ($rangeLow) must be <= rangeHigh ($rangeHigh)"
        }

        return plan.stops.map { stop ->
            val raw = stop.evOffset / evPerStep
            val rounded = raw.roundToInt()
            clamp(rounded, rangeLow, rangeHigh)
        }
    }

    /**
     * Convenience that builds the plan and immediately schedules it.
     * Useful for tests and for one-shot UI invocations.
     */
    fun schedule(
        pattern: BracketPattern,
        evStep: Double,
        cameraStep: Rational,
        range: Range<Int>,
    ): List<Int> = aeStepsFor(BracketPlan.build(pattern, evStep), cameraStep, range)

    private fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))
}
