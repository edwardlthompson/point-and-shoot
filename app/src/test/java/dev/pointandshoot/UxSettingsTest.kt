package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class UxSettingsTest {

    @Test
    fun themeMode_fromStorage_parsesKnownValues() {
        assertEquals(PnsThemeMode.Dark, PnsThemeMode.fromStorage("dark"))
        assertEquals(PnsThemeMode.Light, PnsThemeMode.fromStorage("LIGHT"))
        assertEquals(PnsThemeMode.System, PnsThemeMode.fromStorage("System"))
    }

    @Test
    fun themeMode_fromStorage_unknownFallsBackToSystem() {
        assertEquals(PnsThemeMode.System, PnsThemeMode.fromStorage(null))
        assertEquals(PnsThemeMode.System, PnsThemeMode.fromStorage("sepia"))
    }

}
