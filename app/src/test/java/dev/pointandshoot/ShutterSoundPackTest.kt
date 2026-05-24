package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterSoundPackTest {
    @Test
    fun fromStorageKey_resolvesKnownPacks() {
        assertEquals(ShutterSoundPack.ClassicMechanical, ShutterSoundPack.fromStorageKey("mechanical"))
        assertEquals(ShutterSoundPack.DigitalBeep, ShutterSoundPack.fromStorageKey("digital"))
        assertEquals(ShutterSoundPack.VintageClick, ShutterSoundPack.fromStorageKey("vintage"))
        assertEquals(ShutterSoundPack.Silent, ShutterSoundPack.fromStorageKey("silent"))
    }

    @Test
    fun fromStorageKey_unknownFallsBackToMechanical() {
        assertEquals(ShutterSoundPack.ClassicMechanical, ShutterSoundPack.fromStorageKey("unknown"))
        assertEquals(ShutterSoundPack.ClassicMechanical, ShutterSoundPack.fromStorageKey(null))
    }

    @Test
    fun audiblePacksHaveBundledSamples() {
        assertTrue(ShutterSoundPack.ClassicMechanical.hasSample)
        assertTrue(ShutterSoundPack.DigitalBeep.hasSample)
        assertTrue(ShutterSoundPack.VintageClick.hasSample)
        assertFalse(ShutterSoundPack.Silent.hasSample)
    }
}
