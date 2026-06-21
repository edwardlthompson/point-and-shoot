package dev.pointandshoot

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsExportBundleTest {

    @Test
    fun source_containsSafExportImportAndSkipKeys() {
        val source = settingsExportBundleSource()
        assertTrue(source.contains(SettingsExportBundle.SCHEMA))
        assertTrue(source.contains("intervalometer_running"))
        assertTrue(source.contains("application/json"))
        assertTrue(source.contains("writeToUri"))
        assertTrue(source.contains("importFromUri"))
        assertTrue(source.contains("last_rear_camera_id"))
        assertTrue(source.contains("HudSettings.PREFS_NAME"))
        assertTrue(source.contains("PreviewChromePreferences.PREFS_NAME"))
    }

    private fun settingsExportBundleSource(): String {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate =
                File(
                    dir,
                    "app/src/main/java/dev/pointandshoot/SettingsExportBundle.kt",
                )
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: error("SettingsExportBundle.kt not found")
            dir = parent
        }
    }
}
