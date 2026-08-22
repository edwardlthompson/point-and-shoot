package dev.pointandshoot

/**
 * Stable accessibility strings for About / heritage external actions.
 * Host JVM tests pin these so [AboutScreen] link chips keep non-empty content descriptions.
 */
object AboutScreenA11y {
    const val BACK = "Navigate back"
    const val CHECK_UPDATES = "Check GitHub for a newer Point and Shoot APK"
    const val RELEASE_NOTES = "Open tagged GitHub release notes"
    const val CHANGELOG = "Open CHANGELOG.md on GitHub"
    const val PRIVACY = "Open privacy policy on GitHub"
    const val VENMO = "Open Venmo donation page"
    const val OBTAINIUM = "Add Point and Shoot in Obtainium"
    const val NOTICE = "Open NOTICE and third-party licenses on GitHub"
    const val LICENSE = "Open Apache 2.0 LICENSE on GitHub"
    const val WIFI_ONLY_UPDATES = "Automatic GitHub checks only on Wi-Fi"

    val EXTERNAL_LINK_DESCRIPTIONS: List<String> =
        listOf(RELEASE_NOTES, CHANGELOG, PRIVACY, VENMO, OBTAINIUM, NOTICE, LICENSE)
}
