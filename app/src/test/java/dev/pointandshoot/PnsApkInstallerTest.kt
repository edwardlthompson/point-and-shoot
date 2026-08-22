package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsApkInstallerTest {
    @Test
    fun httpsDownloadUrl_rejectsHttpAndBlank() {
        assertTrue(
            PnsApkInstaller.isHttpsDownloadUrl(
                "https://github.com/edwardlthompson/point-and-shoot/releases/download/v1/app.apk",
            ),
        )
        assertFalse(PnsApkInstaller.isHttpsDownloadUrl("http://example.com/app.apk"))
        assertFalse(PnsApkInstaller.isHttpsDownloadUrl(""))
        assertFalse(PnsApkInstaller.isHttpsDownloadUrl("ftp://example.com/app.apk"))
    }

    @Test
    fun expectedPackageMatches_requiresExactName() {
        assertTrue(PnsApkInstaller.expectedPackageMatches("dev.pointandshoot", "dev.pointandshoot"))
        assertFalse(PnsApkInstaller.expectedPackageMatches("com.evil", "dev.pointandshoot"))
        assertFalse(PnsApkInstaller.expectedPackageMatches(null, "dev.pointandshoot"))
        assertFalse(PnsApkInstaller.expectedPackageMatches("", "dev.pointandshoot"))
    }

    @Test
    fun expectedVersionMatches_requiresPromptedVersionWhenKnown() {
        assertTrue(PnsApkInstaller.expectedVersionMatches("0.14.0-beta.22", "0.14.0-beta.22"))
        assertTrue(PnsApkInstaller.expectedVersionMatches("other", null))
        assertFalse(PnsApkInstaller.expectedVersionMatches("0.14.0-beta.21", "0.14.0-beta.22"))
        assertFalse(PnsApkInstaller.expectedVersionMatches(null, "0.14.0-beta.22"))
    }

    @Test
    fun downloadedBytesMatchLength_rejectsShortBody() {
        assertTrue(PnsApkInstaller.downloadedBytesMatchLength(10L, 0L))
        assertTrue(PnsApkInstaller.downloadedBytesMatchLength(10L, 10L))
        assertFalse(PnsApkInstaller.downloadedBytesMatchLength(9L, 10L))
    }

    @Test
    fun downloadedBytesMatchDeclared_usesGithubSizeWhenKnown() {
        assertTrue(PnsApkInstaller.downloadedBytesMatchDeclared(10L, 0L, 0L))
        assertTrue(PnsApkInstaller.downloadedBytesMatchDeclared(10L, 10L, 10L))
        assertFalse(PnsApkInstaller.downloadedBytesMatchDeclared(10L, 0L, 11L))
        assertFalse(PnsApkInstaller.downloadedBytesMatchDeclared(9L, 10L, 10L))
    }

    @Test
    fun versionCodeIsNewer_requiresGreaterCode() {
        assertTrue(PnsApkInstaller.versionCodeIsNewer(22016L, 22015L))
        assertFalse(PnsApkInstaller.versionCodeIsNewer(22015L, 22015L))
        assertFalse(PnsApkInstaller.versionCodeIsNewer(0L, 22015L))
    }

    @Test
    fun allowedApkDownloadHost_githubAndUsercontentOnly() {
        assertTrue(PnsApkInstaller.isAllowedApkDownloadHost("github.com"))
        assertTrue(PnsApkInstaller.isAllowedApkDownloadHost("objects.githubusercontent.com"))
        assertTrue(PnsApkInstaller.isAllowedApkDownloadHost("release-assets.githubusercontent.com"))
        assertFalse(PnsApkInstaller.isAllowedApkDownloadHost("evil.example"))
        assertFalse(PnsApkInstaller.isAllowedApkDownloadHost("github.com.evil.example"))
        assertFalse(PnsApkInstaller.isAllowedApkDownloadHost(null))
    }

    @Test
    fun declaredSizesAgree_whenBothKnown() {
        assertTrue(PnsApkInstaller.declaredSizesAgree(0L, 10L))
        assertTrue(PnsApkInstaller.declaredSizesAgree(10L, 0L))
        assertTrue(PnsApkInstaller.declaredSizesAgree(10L, 10L))
        assertFalse(PnsApkInstaller.declaredSizesAgree(9L, 10L))
    }

    @Test
    fun resolveRedirectUrl_joinsRelativeLocation() {
        assertEquals(
            "https://objects.githubusercontent.com/apk",
            PnsApkInstaller.resolveRedirectUrl(
                "https://github.com/edwardlthompson/point-and-shoot/releases/download/v1/app.apk",
                "https://objects.githubusercontent.com/apk",
            ),
        )
        assertEquals(
            "https://github.com/foo/bar",
            PnsApkInstaller.resolveRedirectUrl("https://github.com/foo/old", "/foo/bar"),
        )
        assertEquals(null, PnsApkInstaller.resolveRedirectUrl("https://github.com/foo", null))
    }

    @Test
    fun hasRoomForApk_failsClosedWhenBytesKnownAndLow() {
        assertTrue(PnsApkInstaller.hasRoomForApk(null, 10L))
        val need = PnsApkInstaller.requiredDownloadBytes(10L)
        assertTrue(PnsApkInstaller.hasRoomForApk(need, 10L))
        assertFalse(PnsApkInstaller.hasRoomForApk(need - 1L, 10L))
        assertTrue(PnsApkInstaller.hasRoomForApk(PnsApkInstaller.requiredDownloadBytes(0L), 0L))
    }

    @Test
    fun sha256Matches_requiresExactHex() {
        val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertTrue(PnsApkInstaller.sha256Matches(hex.uppercase(), hex))
        assertFalse(PnsApkInstaller.sha256Matches(hex, hex.replace('0', '1')))
        assertFalse(PnsApkInstaller.sha256Matches(hex, null))
        assertFalse(PnsApkInstaller.sha256Matches(null, hex))
    }

    @Test
    fun cachedApkMatchingSha_returnsHashedFile() {
        val dir = kotlin.io.path.createTempDirectory("pns-apk-cache").toFile()
        try {
            val file = java.io.File(dir, "update.apk")
            file.writeText("hello-apk")
            val sha = PnsApkInstaller.sha256Hex(file)
            assertEquals(file, PnsApkInstaller.cachedApkMatchingSha(dir, sha))
            assertEquals(null, PnsApkInstaller.cachedApkMatchingSha(dir, "ab"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun progressPercent_usesContentLength() {
        assertEquals(0, PnsApkInstaller.progressPercent(0L, 100L))
        assertEquals(50, PnsApkInstaller.progressPercent(50L, 100L))
        assertEquals(100, PnsApkInstaller.progressPercent(100L, 100L))
        assertEquals(0, PnsApkInstaller.progressPercent(10L, 0L))
    }

    @Test
    fun safeApkFileName_pinsBasenameAndSuffix() {
        assertEquals("update.apk", PnsApkInstaller.safeApkFileName(null))
        assertEquals("update.apk", PnsApkInstaller.safeApkFileName("https://evil/../x"))
        assertEquals("update.apk", PnsApkInstaller.safeApkFileName("payload.exe"))
        assertEquals("Point-and-Shoot-0.14.0.apk", PnsApkInstaller.safeApkFileName(
            "https://github.com/x/Point-and-Shoot-0.14.0.apk?foo=1",
        ))
    }

    @Test
    fun destInCacheDir_rejectsEscape() {
        val dir = kotlin.io.path.createTempDirectory("pns-apk-dest").toFile()
        try {
            val ok = PnsApkInstaller.destInCacheDir(dir, "update.apk")
            assertTrue(ok != null && ok.parentFile.canonicalPath == dir.canonicalPath)
            assertEquals(null, PnsApkInstaller.destInCacheDir(dir, "..${java.io.File.separator}escape.apk"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun declaredSizeWithinCap_rejectsOver150Mb() {
        assertTrue(PnsApkInstaller.declaredSizeWithinCap(0L, 0L))
        assertTrue(PnsApkInstaller.declaredSizeWithinCap(PnsApkInstaller.MAX_APK_BYTES, 0L))
        assertFalse(PnsApkInstaller.declaredSizeWithinCap(PnsApkInstaller.MAX_APK_BYTES + 1L, 0L))
        assertFalse(PnsApkInstaller.declaredSizeWithinCap(0L, PnsApkInstaller.MAX_APK_BYTES + 1L))
    }

    @Test
    fun formatProgress_includesMegabytesWhenKnown() {
        val line =
            PnsApkInstaller.formatProgress(PnsApkInstaller.Progress(50, 10L * 1024L * 1024L, 20L * 1024L * 1024L))
        assertTrue(line.contains("50%"))
        assertTrue(line.contains("10 MB"))
        assertTrue(line.contains("20 MB"))
    }
}
