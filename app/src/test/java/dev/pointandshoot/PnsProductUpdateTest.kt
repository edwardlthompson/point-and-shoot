package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsProductUpdateTest {

    @Test
    fun dailyCheckWaitsAFullDay() {
        assertTrue(PnsProductUpdate.shouldCheckDaily(null, 0L))
        assertFalse(PnsProductUpdate.shouldCheckDaily(0L, PnsProductUpdate.MS_DAY - 1))
        assertTrue(PnsProductUpdate.shouldCheckDaily(0L, PnsProductUpdate.MS_DAY))
    }

    @Test
    fun apkVersionIgnoresTemplateTags() {
        assertEquals(
            "0.14.0-beta.20",
            PnsProductUpdate.parseApkVersion("Point-and-Shoot-0.14.0-beta.20.apk"),
        )
        assertEquals(
            "1.0.0",
            PnsProductUpdate.parseApkVersion("point-and-shoot-1.0.0-foss.apk"),
        )
        assertEquals(null, PnsProductUpdate.parseApkVersion("v0.15.0"))
        assertEquals(null, PnsProductUpdate.parseApkVersion("CHANGELOG.md"))
    }

    @Test
    fun newerThanCurrent_usesFilenameSemverNotGitTag() {
        assertTrue(PnsProductUpdate.isNewerVersion("0.14.0-beta.19", "0.14.0-beta.20"))
        assertFalse(PnsProductUpdate.isNewerVersion("0.14.0-beta.20", "0.14.0-beta.20"))
        assertFalse(PnsProductUpdate.isNewerVersion("0.14.0-beta.20", "0.14.0-beta.19"))
        assertTrue(PnsProductUpdate.isNewerVersion("0.14.0-beta.20", "0.14.0"))
        assertTrue(PnsProductUpdate.isNewerVersion("0.14.0", "0.15.0"))
    }

    @Test
    fun donateNudgeOnlyAfterVersionChange() {
        assertFalse(PnsProductUpdate.shouldNudgeDonate(null, "0.14.0-beta.20"))
        assertFalse(PnsProductUpdate.shouldNudgeDonate("0.14.0-beta.20", "0.14.0-beta.20"))
        assertTrue(PnsProductUpdate.shouldNudgeDonate("0.14.0-beta.19", "0.14.0-beta.20"))
    }

    @Test
    fun updatePromptSkipsDismissedVersion() {
        assertTrue(PnsProductUpdate.shouldPromptUpdate("0.14.0-beta.19", "0.14.0-beta.20", null))
        assertFalse(PnsProductUpdate.shouldPromptUpdate("0.14.0-beta.19", "0.14.0-beta.20", "0.14.0-beta.20"))
        assertFalse(PnsProductUpdate.shouldPromptUpdate("0.14.0-beta.20", "0.14.0-beta.20", null))
    }

    @Test
    fun selectApkAssetReadsProductFilename() {
        val picked =
            PnsProductUpdate.selectApkAsset(
                listOf(
                    PnsProductUpdate.NamedAsset("CHANGELOG.md", "https://example.com/changelog"),
                    PnsProductUpdate.NamedAsset(
                        "Point-and-Shoot-0.14.0-beta.21.apk",
                        "https://example.com/app.apk",
                    ),
                ),
            )
        assertEquals("0.14.0-beta.21", picked?.version)
        assertEquals("https://example.com/app.apk", picked?.url)
    }

    @Test
    fun evaluateLaunch_firstRunRecordsVersionWithoutDonate() {
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = null,
                lastCheckAt = 0L,
                dismissedVersion = null,
                now = 1L,
                fetchLatest = { error("fetch should wait for daily interval") },
            )
        assertEquals(PnsProductUpdate.LaunchPrompt.None, result.prompt)
        assertEquals("0.14.0-beta.20", result.markSeenVersion)
        assertEquals(null, result.markCheckedAt)
    }

    @Test
    fun evaluateLaunch_donateNudgeTakesPriorityOverUpdateCheck() {
        var fetched = false
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.21",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = null,
                dismissedVersion = null,
                now = 0L,
                fetchLatest = {
                    fetched = true
                    null
                },
            )
        assertTrue(result.prompt is PnsProductUpdate.LaunchPrompt.Donate)
        assertEquals(null, result.markSeenVersion)
        assertFalse(fetched)
    }

    @Test
    fun evaluateLaunch_failedFetchStaysSilent() {
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = null,
                dismissedVersion = null,
                now = 10L,
                fetchLatest = { null },
            )
        assertEquals(PnsProductUpdate.LaunchPrompt.None, result.prompt)
        assertEquals(10L, result.markCheckedAt)
    }

    @Test
    fun evaluateLaunch_emptyAssetsStaySilent() {
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = null,
                dismissedVersion = null,
                now = 10L,
                fetchLatest = {
                    PnsProductUpdate.GithubRelease(PnsProductUpdate.RELEASES_PAGE, emptyList())
                },
            )
        assertEquals(PnsProductUpdate.LaunchPrompt.None, result.prompt)
    }

    @Test
    fun evaluateLaunch_promptsInstallForNewerFilenameVersion() {
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = null,
                dismissedVersion = null,
                now = 10L,
                fetchLatest = {
                    PnsProductUpdate.GithubRelease(
                        "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.21",
                        listOf(
                            PnsProductUpdate.NamedAsset(
                                "Point-and-Shoot-0.14.0-beta.21.apk",
                                "https://example.com/Point-and-Shoot-0.14.0-beta.21.apk",
                            ),
                        ),
                    )
                },
            )
        val update = result.prompt as PnsProductUpdate.LaunchPrompt.Update
        assertEquals("0.14.0-beta.21", update.version)
        assertEquals("https://example.com/Point-and-Shoot-0.14.0-beta.21.apk", update.url)
    }

    @Test
    fun githubReleaseParse_readsAssetFilenames() {
        val parsed =
            PnsGithubRelease.parse(
                """
                {
                  "html_url": "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.21",
                  "tag_name": "v0.22.1",
                  "assets": [
                    {"name": "CHANGELOG.md", "browser_download_url": "https://example.com/cl"},
                    {
                      "name": "Point-and-Shoot-0.14.0-beta.21.apk",
                      "browser_download_url": "https://example.com/app.apk"
                    }
                  ]
                }
                """.trimIndent(),
            )
        val asset = PnsProductUpdate.selectApkAsset(parsed!!.assets)
        assertEquals("0.14.0-beta.21", asset?.version)
        assertEquals("https://example.com/app.apk", asset?.url)
    }
}
