package dev.pointandshoot

import org.junit.Assert.assertFalse
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
        assertFalse("donate/update prefs must stay device-local", source.contains("pns_updates"))
        assertFalse(
            "donate/update prefs must not Android-backup",
            backupRulesSource().contains("pns_updates"),
        )
        assertFalse(
            "donate/update prefs must not device-transfer",
            dataExtractionRulesSource().contains("pns_updates"),
        )
    }

    private fun settingsExportBundleSource(): String =
        repoFile("app/src/main/java/dev/pointandshoot/SettingsExportBundle.kt")

    private fun backupRulesSource(): String =
        repoFile("app/src/main/res/xml/pns_backup_rules.xml")

    private fun dataExtractionRulesSource(): String =
        repoFile("app/src/main/res/xml/pns_data_extraction_rules.xml")

    private fun repoFile(relativePath: String): String {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: error("$relativePath not found")
            dir = parent
        }
    }
}
