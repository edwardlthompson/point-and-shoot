package dev.pointandshoot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Sprint **14.11** — locked donation link (BUILD_PLAN Milestone 14). */
const val PNS_VENMO_DONATION_URL: String =
    "https://venmo.com/code?user_id=1857304970395648420"

/** GitHub repo for releases / changelog (updated by `scripts/pns_github_release.ps1`). */
const val PNS_GITHUB_OWNER: String = "edwardlthompson"
const val PNS_GITHUB_REPO: String = "point-and-shoot"

/** Latest shipped semver (no leading `v`) — sync with `scripts/changelog_coverage.v1.json`. */
const val PNS_GITHUB_LATEST_RELEASE_TAG: String = "0.14.0-beta.10"

const val PNS_GITHUB_RELEASES_URL: String =
    "https://github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/releases"

/** Redirects to the newest GitHub release page (release notes body from CHANGELOG). */
const val PNS_GITHUB_RELEASES_LATEST_URL: String =
    "$PNS_GITHUB_RELEASES_URL/latest"

const val PNS_GITHUB_CHANGELOG_URL: String =
    "https://github.com/$PNS_GITHUB_OWNER/$PNS_GITHUB_REPO/blob/main/CHANGELOG.md"

/** Opens the tagged release page (notes + APK assets). */
fun githubReleaseUrlForTag(tag: String): String {
    val clean = tag.trim().removePrefix("v")
    return "$PNS_GITHUB_RELEASES_URL/tag/v$clean"
}

/**
 * Opens [url] in the user's default browser (external handler).
 *
 * @return true if an activity was started.
 */
fun openExternalUrl(context: Context, url: String): Boolean {
    val uri = Uri.parse(url.trim())
    val intent =
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return try {
        context.startActivity(intent)
        Log.i("PNS.ChromeUx", "externalUrl=opened host=${uri.host}")
        true
    } catch (_: ActivityNotFoundException) {
        Log.w("PNS.ChromeUx", "externalUrl=no_handler url=$url")
        false
    }
}
