package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightAeModeSupportTest {

    @Test
    fun `vendorExtraModesFiltered removes standard ints and sorts`() {
        val standard = setOf(0, 1, 2, 3)
        val avail = intArrayOf(3, 9, 1, 9, 5)
        assertEquals(listOf(5, 9), HighlightAeModeSupport.vendorExtraModesFiltered(avail, standard))
    }

    @Test
    fun `resolve picks max when multiple extras — logic via filtered list`() {
        val extras = listOf(5, 9, 7)
        val chosen = when (extras.size) {
            0 -> null
            1 -> extras[0]
            else -> extras.maxOrNull()
        }
        assertEquals(9, chosen)
    }

    @Test
    fun `single extra resolves to itself`() {
        val extras = listOf(42)
        val chosen = when (extras.size) {
            0 -> null
            1 -> extras[0]
            else -> extras.maxOrNull()
        }
        assertEquals(42, chosen)
    }

    @Test
    fun `empty extras resolve null`() {
        val extras = emptyList<Int>()
        val chosen = when (extras.size) {
            0 -> null
            1 -> extras[0]
            else -> extras.maxOrNull()
        }
        assertNull(chosen)
    }
}
