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
    fun sanitizeReleaseNotes_stripsTagsAndCaps() {
        val raw = "<b>Hello</b> &amp; world\n\n\nmore"
        assertEquals("Hello & world\n\nmore", PnsProductUpdate.sanitizeReleaseNotes(raw))
        assertEquals("", PnsProductUpdate.sanitizeReleaseNotes("   "))
        assertEquals(2_000, PnsProductUpdate.sanitizeReleaseNotes("x".repeat(3_000)).length)
    }

    @Test
    fun formatSha256Short_takesTwelveHex() {
        val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertEquals("0123456789ab", PnsProductUpdate.formatSha256Short(hex))
        assertEquals(null, PnsProductUpdate.formatSha256Short("not-hex"))
    }

    @Test
    fun pendingVersionAlreadyInstalled_requiresExactName() {
        assertTrue(
            PnsProductUpdate.pendingVersionAlreadyInstalled("0.14.0-beta.21", "0.14.0-beta.21"),
        )
        assertFalse(
            PnsProductUpdate.pendingVersionAlreadyInstalled("0.14.0-beta.21", "0.14.0-beta.22"),
        )
        assertFalse(PnsProductUpdate.pendingVersionAlreadyInstalled(null, "0.14.0-beta.21"))
        assertFalse(PnsProductUpdate.pendingVersionAlreadyInstalled("", "0.14.0-beta.21"))
    }

    @Test
    fun formatMegabytes_roundsKnownSizes() {
        assertEquals(null, PnsProductUpdate.formatMegabytes(0L))
        assertEquals("1 MB", PnsProductUpdate.formatMegabytes(1L))
        assertEquals("50 MB", PnsProductUpdate.formatMegabytes(50L * 1024L * 1024L))
    }

    @Test
    fun formatLastChecked_usesRelativeBuckets() {
        val now = 10_000_000L
        assertEquals(null, PnsProductUpdate.formatLastChecked(null, now))
        assertEquals("Last checked just now", PnsProductUpdate.formatLastChecked(now - 1_000L, now))
        assertEquals(
            "Last checked 3m ago",
            PnsProductUpdate.formatLastChecked(now - (3L * PnsProductUpdate.MS_MINUTE), now),
        )
        assertEquals(
            "Last checked 2h ago",
            PnsProductUpdate.formatLastChecked(now - (2L * PnsProductUpdate.MS_HOUR), now),
        )
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
                        sizeBytes = 52_428_800L,
                    ),
                ),
            )
        assertEquals("0.14.0-beta.21", picked?.version)
        assertEquals("https://example.com/app.apk", picked?.url)
        assertEquals(null, picked?.sha256Url)
        assertEquals(52_428_800L, picked?.sizeBytes)
    }

    @Test
    fun selectApkAsset_pairsSha256Sidecar() {
        val picked =
            PnsProductUpdate.selectApkAsset(
                listOf(
                    PnsProductUpdate.NamedAsset(
                        "Point-and-Shoot-0.14.0-beta.21.apk",
                        "https://example.com/app.apk",
                    ),
                    PnsProductUpdate.NamedAsset(
                        "Point-and-Shoot-0.14.0-beta.21.apk.sha256",
                        "https://example.com/app.apk.sha256",
                    ),
                ),
            )
        assertEquals("https://example.com/app.apk.sha256", picked?.sha256Url)
    }

    @Test
    fun parseSha256Sidecar_readsHashFilenameLine() {
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            PnsProductUpdate.parseSha256Sidecar(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  Point-and-Shoot-0.14.0-beta.21.apk\n",
            ),
        )
        assertEquals(null, PnsProductUpdate.parseSha256Sidecar("not-a-hash"))
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
        assertEquals(null, result.markCheckedAt)
        assertFalse(result.fetchSucceeded)
    }

    @Test
    fun evaluateLaunch_skipNetworkDoesNotFetchOrMarkChecked() {
        var fetched = false
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = null,
                dismissedVersion = null,
                now = 10L,
                fetchLatest = {
                    fetched = true
                    null
                },
                skipNetwork = true,
            )
        assertEquals(PnsProductUpdate.LaunchPrompt.None, result.prompt)
        assertEquals(null, result.markCheckedAt)
        assertFalse(fetched)
    }

    @Test
    fun evaluateLaunch_forceCheckIgnoresDailyIntervalAndDismissed() {
        val result =
            PnsProductUpdate.evaluateLaunch(
                currentVersion = "0.14.0-beta.20",
                lastSeenVersion = "0.14.0-beta.20",
                lastCheckAt = 0L,
                dismissedVersion = "0.14.0-beta.21",
                now = 1L,
                fetchLatest = {
                    PnsProductUpdate.GithubRelease(
                        PnsProductUpdate.RELEASES_PAGE,
                        listOf(
                            PnsProductUpdate.NamedAsset(
                                "Point-and-Shoot-0.14.0-beta.21.apk",
                                "https://example.com/Point-and-Shoot-0.14.0-beta.21.apk",
                            ),
                        ),
                    )
                },
                forceCheck = true,
            )
        val update = result.prompt as PnsProductUpdate.LaunchPrompt.Update
        assertEquals("0.14.0-beta.21", update.version)
        assertTrue(result.fetchSucceeded)
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
        assertEquals(10L, result.markCheckedAt)
        assertTrue(result.fetchSucceeded)
        assertEquals(null, result.markKnownGithubVersion)
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
        assertEquals("0.14.0-beta.21", result.markKnownGithubVersion)
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

    @Test
    fun githubReleaseParseList_picksNewestPrereleaseFilename() {
        val parsed =
            PnsGithubRelease.parseList(
                """
                [
                  {
                    "html_url": "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.21",
                    "tag_name": "v0.22.1",
                    "prerelease": true,
                    "body": "Notes for beta.21",
                    "assets": [
                      {
                        "name": "Point-and-Shoot-0.14.0-beta.21.apk",
                        "browser_download_url": "https://example.com/21.apk"
                      }
                    ]
                  },
                  {
                    "html_url": "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.20",
                    "tag_name": "v9.9.9",
                    "prerelease": true,
                    "body": "Notes for beta.20",
                    "assets": [
                      {
                        "name": "Point-and-Shoot-0.14.0-beta.20.apk",
                        "browser_download_url": "https://example.com/20.apk"
                      }
                    ]
                  }
                ]
                """.trimIndent(),
            )
        val asset = PnsProductUpdate.selectApkAsset(parsed!!.assets)
        assertEquals("0.14.0-beta.21", asset?.version)
        assertEquals("https://example.com/21.apk", asset?.url)
        assertEquals("Notes for beta.21", parsed.notes)
    }
}
