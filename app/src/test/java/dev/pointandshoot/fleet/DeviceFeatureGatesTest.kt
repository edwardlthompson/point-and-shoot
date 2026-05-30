package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceFeatureGatesTest {
    @Test
    fun findRearRearConcurrentPair_requiresTwoBacksInSameSet() {
        val backIds = listOf("2", "3", "4")
        val sets = setOf(setOf("2", "3"), setOf("4", "1"))
        val pair = DeviceFeatureGates.findRearRearConcurrentPair(backIds, sets)
        assertEquals("2" to "3", pair)
    }

    @Test
    fun findRearRearConcurrentPair_nullWhenNoConcurrentSets() {
        assertNull(
            DeviceFeatureGates.findRearRearConcurrentPair(
                backIds = listOf("2", "3"),
                concurrentSets = emptySet(),
            ),
        )
    }
}
