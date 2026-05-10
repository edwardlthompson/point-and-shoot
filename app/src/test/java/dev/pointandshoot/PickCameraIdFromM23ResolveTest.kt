package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickCameraIdFromM23ResolveTest {
    @Test
    fun prefersWideWhenInList() {
        assertEquals("2", pickCameraIdFromM23Resolve("2" to null, listOf("0", "2", "1")))
    }

    @Test
    fun fallsBackWhenResolveNull() {
        assertEquals("0", pickCameraIdFromM23Resolve(null, listOf("0", "1")))
    }

    @Test
    fun fallsBackWhenWideNotInIds() {
        assertEquals("0", pickCameraIdFromM23Resolve("9" to null, listOf("0", "1")))
    }

    @Test
    fun emptyIds() {
        assertNull(pickCameraIdFromM23Resolve("2" to null, emptyList()))
    }
}
