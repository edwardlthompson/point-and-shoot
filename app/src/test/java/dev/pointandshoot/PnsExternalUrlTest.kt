package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsExternalUrlTest {
    @Test
    fun venmoDonationUrl_usesHttpsAndLockedUserId() {
        assertTrue(PNS_VENMO_DONATION_URL.startsWith("https://"))
        assertTrue(PNS_VENMO_DONATION_URL.contains("venmo.com"))
        assertEquals(
            "https://venmo.com/code?user_id=1857304970395648420",
            PNS_VENMO_DONATION_URL,
        )
    }

    @Test
    fun githubReleaseUrls_useLockedRepoAndLatestTag() {
        assertTrue(PNS_GITHUB_RELEASES_URL.startsWith("https://github.com/"))
        assertTrue(PNS_GITHUB_RELEASES_LATEST_URL.endsWith("/releases/latest"))
        assertEquals(
            "https://api.github.com/repos/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases?per_page=15",
            PNS_GITHUB_RELEASES_API_URL,
        )
        assertTrue(PNS_GITHUB_CHANGELOG_URL.contains("/blob/main/CHANGELOG.md"))
        assertTrue(PNS_GITHUB_PRIVACY_URL.contains("/blob/main/PRIVACY.md"))
        val expectedLatest =
            "https://github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases/tag/v${PNS_GITHUB_LATEST_RELEASE_TAG}"
        assertEquals(expectedLatest, githubReleaseUrlForTag(PNS_GITHUB_LATEST_RELEASE_TAG))
        assertEquals(expectedLatest, githubReleaseUrlForTag("v$PNS_GITHUB_LATEST_RELEASE_TAG"))
        assertEquals(expectedLatest, githubReleaseNotesUrl(null))
        assertEquals(
            "https://github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases/tag/v0.14.0-beta.20",
            githubReleaseNotesUrl("0.14.0-beta.20"),
        )
        assertFalse(githubReleaseNotesUrl("0.14.0-beta.20").endsWith("/releases/latest"))
    }

    @Test
    fun licenseUrl_pointsAtRepoLicense() {
        assertTrue(PNS_GITHUB_LICENSE_URL.endsWith("/blob/main/LICENSE"))
    }

    @Test
    fun noticeUrl_pointsAtRepoNotice() {
        assertTrue(PNS_GITHUB_NOTICE_URL.endsWith("/blob/main/NOTICE"))
    }

    @Test
    fun obtainiumAddUrl_targetsThisRepo() {
        assertEquals(
            "obtainium://add/github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO",
            PNS_OBTAINIUM_ADD_URL,
        )
    }
}
