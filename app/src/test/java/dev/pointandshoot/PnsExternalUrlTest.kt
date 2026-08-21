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
        assertEquals(
            "https://api.github.com/repos/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases/latest",
            PNS_GITHUB_RELEASES_API_URL,
        )
        assertTrue(PNS_GITHUB_CHANGELOG_URL.contains("/blob/main/CHANGELOG.md"))
        assertTrue(PNS_GITHUB_PRIVACY_URL.contains("/blob/main/PRIVACY.md"))
        val expectedLatest =
            "https://github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases/tag/v${PNS_GITHUB_LATEST_RELEASE_TAG}"
        assertEquals(expectedLatest, githubReleaseUrlForTag(PNS_GITHUB_LATEST_RELEASE_TAG))
        assertEquals(expectedLatest, githubReleaseUrlForTag("v$PNS_GITHUB_LATEST_RELEASE_TAG"))
    }
}
