package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class BracketSchedulerTest {

    @Test
    fun `1 EV per step camera converts 3-shot bracket directly`() {
        val plan = BracketPlan.build(BracketPattern.Three, evStep = 1.0)
        val steps = BracketScheduler.aeStepsFor(
            plan = plan,
            cameraStepNumerator = 1,
            cameraStepDenominator = 1,
            rangeLow = -12,
            rangeHigh = 12,
        )
        assertEquals(listOf(-1, 0, 1), steps)
    }

    @Test
    fun `1-third EV per step camera quantizes a 1 EV bracket to 3 steps`() {
        val plan = BracketPlan.build(BracketPattern.Three, evStep = 1.0)
        val steps = BracketScheduler.aeStepsFor(
            plan = plan,
            cameraStepNumerator = 1,
            cameraStepDenominator = 3,
            rangeLow = -12,
            rangeHigh = 12,
        )
        assertEquals(listOf(-3, 0, 3), steps)
    }

    @Test
    fun `1-half EV per step camera quantizes a 0_5 EV bracket to 1 step`() {
        val plan = BracketPlan.build(BracketPattern.Five, evStep = 0.5)
        val steps = BracketScheduler.aeStepsFor(
            plan = plan,
            cameraStepNumerator = 1,
            cameraStepDenominator = 2,
            rangeLow = -12,
            rangeHigh = 12,
        )
        // EVs: -1.0, -0.5, 0, 0.5, 1.0 ; step is 0.5 EV/unit -> -2, -1, 0, 1, 2
        assertEquals(listOf(-2, -1, 0, 1, 2), steps)
    }

    @Test
    fun `requested EV out of range clamps to range bounds`() {
        // 7-shot at 2 EV step -> EVs: -6, -4, -2, 0, 2, 4, 6
        // With camera step 1/1 EV and range [-3, 3], extremes clamp.
        val plan = BracketPlan.build(BracketPattern.Seven, evStep = 2.0)
        val steps = BracketScheduler.aeStepsFor(
            plan = plan,
            cameraStepNumerator = 1,
            cameraStepDenominator = 1,
            rangeLow = -3,
            rangeHigh = 3,
        )
        assertEquals(listOf(-3, -3, -2, 0, 2, 3, 3), steps)
    }

    @Test
    fun `reference shot is always exactly zero in the schedule`() {
        val plan = BracketPlan.build(BracketPattern.Five, evStep = 0.7)
        val steps = BracketScheduler.aeStepsFor(
            plan = plan,
            cameraStepNumerator = 1,
            cameraStepDenominator = 3,
            rangeLow = -12,
            rangeHigh = 12,
        )
        val refIndex = plan.stops.indexOfFirst { it.isReference }
        assertEquals(0, steps[refIndex])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero numerator step is rejected`() {
        BracketScheduler.aeStepsFor(
            plan = BracketPlan.build(BracketPattern.Three),
            cameraStepNumerator = 0,
            cameraStepDenominator = 3,
            rangeLow = -12,
            rangeHigh = 12,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero denominator step is rejected`() {
        BracketScheduler.aeStepsFor(
            plan = BracketPlan.build(BracketPattern.Three),
            cameraStepNumerator = 1,
            cameraStepDenominator = 0,
            rangeLow = -12,
            rangeHigh = 12,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative step rational is rejected`() {
        BracketScheduler.aeStepsFor(
            plan = BracketPlan.build(BracketPattern.Three),
            cameraStepNumerator = -1,
            cameraStepDenominator = 3,
            rangeLow = -12,
            rangeHigh = 12,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inverted range is rejected`() {
        BracketScheduler.aeStepsFor(
            plan = BracketPlan.build(BracketPattern.Three),
            cameraStepNumerator = 1,
            cameraStepDenominator = 3,
            rangeLow = 5,
            rangeHigh = -5,
        )
    }
}
