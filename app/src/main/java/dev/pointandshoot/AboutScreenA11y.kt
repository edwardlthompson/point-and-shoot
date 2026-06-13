package dev.pointandshoot

/**
 * Stable accessibility strings for About / heritage external actions.
 * Host JVM tests pin these so [AboutScreen] link chips keep non-empty content descriptions.
 */
object AboutScreenA11y {
    const val BACK = "Navigate back"
    const val RELEASE_NOTES = "Open latest GitHub release notes"
    const val CHANGELOG = "Open CHANGELOG.md on GitHub"
    const val PRIVACY = "Open privacy policy on GitHub"
    const val VENMO = "Open Venmo donation page"

    val EXTERNAL_LINK_DESCRIPTIONS: List<String> =
        listOf(RELEASE_NOTES, CHANGELOG, PRIVACY, VENMO)
}
