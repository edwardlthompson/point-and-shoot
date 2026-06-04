package dev.pointandshoot

import org.junit.Assert.assertEquals
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
        assertTrue(PNS_GITHUB_CHANGELOG_URL.contains("/blob/main/CHANGELOG.md"))
        assertEquals(
            "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.6",
            githubReleaseUrlForTag(PNS_GITHUB_LATEST_RELEASE_TAG),
        )
        assertEquals(
            "https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.6",
            githubReleaseUrlForTag("v0.14.0-beta.6"),
        )
    }
}
