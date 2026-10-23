package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTrayModeStoreTest {
    @Test
    fun resolveStoredDial_keepsPhotoH() {
        assertEquals(
            CommandDialMode.H,
            PreviewTrayModeStore.resolveStoredDial("H", photo = true),
        )
    }

    @Test
    fun resolveStoredDial_videoRejectsHFallsBackToAuto() {
        assertEquals(
            CommandDialMode.Auto,
            PreviewTrayModeStore.resolveStoredDial("H", photo = false),
        )
    }

    @Test
    fun resolveStoredDial_videoKeepsDual() {
        assertEquals(
            CommandDialMode.Dual,
            PreviewTrayModeStore.resolveStoredDial("Dual", photo = false),
        )
    }

    @Test
    fun resolveStoredDial_legacyGlobalMigratesWhenAllowed() {
        assertEquals(
            CommandDialMode.S,
            PreviewTrayModeStore.resolveStoredDial(
                storedName = null,
                photo = true,
                legacyGlobal = CommandDialMode.S,
            ),
        )
    }

    @Test
    fun resolveStoredDial_legacyHOnVideoBecomesAuto() {
        assertEquals(
            CommandDialMode.Auto,
            PreviewTrayModeStore.resolveStoredDial(
                storedName = null,
                photo = false,
                legacyGlobal = CommandDialMode.H,
            ),
        )
    }

    @Test
    fun resolveStoredDial_unknownNameFallsBackToAuto() {
        assertEquals(
            CommandDialMode.Auto,
            PreviewTrayModeStore.resolveStoredDial("NotAMode", photo = true),
        )
    }
}
