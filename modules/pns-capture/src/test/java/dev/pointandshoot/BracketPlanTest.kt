package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketPlanTest {

    @Test
    fun `three-shot bracket is centered with EV stops -1, 0, +1`() {
        val plan = BracketPlan.build(BracketPattern.Three, evStep = 1.0)

        assertEquals(3, plan.stops.size)
        assertEquals(listOf(-1.0, 0.0, 1.0), plan.stops.map { it.evOffset })
        assertEquals(listOf(0, 1, 2), plan.stops.map { it.indexInBurst })

        val refIndex = plan.stops.indexOfFirst { it.isReference }
        assertEquals(1, refIndex)
        assertEquals(0.0, plan.stops[refIndex].evOffset, 0.0)
    }

    @Test
    fun `five-shot bracket is centered`() {
        val plan = BracketPlan.build(BracketPattern.Five, evStep = 0.5)

        assertEquals(5, plan.stops.size)
        assertEquals(listOf(-1.0, -0.5, 0.0, 0.5, 1.0), plan.stops.map { it.evOffset })
        assertTrue(plan.stops[2].isReference)
    }

    @Test
    fun `seven-shot bracket is centered with default 1 EV step`() {
        val plan = BracketPlan.build(BracketPattern.Seven)

        assertEquals(7, plan.stops.size)
        assertEquals(listOf(-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0), plan.stops.map { it.evOffset })
        assertTrue(plan.stops[3].isReference)
    }

    @Test
    fun `every shot in a plan shares the same grouping id`() {
        val plan = BracketPlan.build(BracketPattern.Five, evStep = 1.0)
        val ids = plan.stops.map { it.bracketGroupingId }.distinct()
        assertEquals(1, ids.size)
        assertEquals(plan.groupingId, ids.single())
    }

    @Test
    fun `independent plans get independent grouping ids by default`() {
        val a = BracketPlan.build(BracketPattern.Three)
        val b = BracketPlan.build(BracketPattern.Three)
        assertNotEquals(a.groupingId, b.groupingId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive EV step is rejected`() {
        BracketPlan.build(BracketPattern.Three, evStep = 0.0)
    }
}
