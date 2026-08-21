package dev.pointandshoot

/**
 * Quiet donate + GitHub update rules (same method as Continuum Calendar).
 *
 * Product versions come from installer filenames, not git/template tags.
 */
object PnsProductUpdate {
    const val MS_DAY = 86_400_000L

    const val RELEASES_API: String = PNS_GITHUB_RELEASES_API_URL

    const val RELEASES_PAGE: String = PNS_GITHUB_RELEASES_LATEST_URL

    data class NamedAsset(val name: String, val url: String)

    data class ProductAsset(val version: String, val url: String)

    sealed class LaunchPrompt {
        data object None : LaunchPrompt()

        data class Donate(val currentVersion: String) : LaunchPrompt()

        data class Update(val version: String, val url: String) : LaunchPrompt()
    }

    data class LaunchEvaluation(
        val prompt: LaunchPrompt,
        val markSeenVersion: String? = null,
        val markCheckedAt: Long? = null,
    )

    fun shouldCheckDaily(lastCheckAt: Long?, now: Long): Boolean {
        if (lastCheckAt == null || lastCheckAt < 0L) return true
        return now - lastCheckAt >= MS_DAY
    }

    fun shouldNudgeDonate(lastSeenVersion: String?, currentVersion: String): Boolean {
        if (currentVersion.isBlank()) return false
        if (lastSeenVersion.isNullOrBlank()) return false
        return lastSeenVersion.trim() != currentVersion.trim()
    }

    fun shouldPromptUpdate(
        currentVersion: String,
        latestVersion: String?,
        dismissedVersion: String?,
    ): Boolean {
        if (latestVersion.isNullOrBlank()) return false
        if (!isNewerVersion(currentVersion, latestVersion)) return false
        if (dismissedVersion == latestVersion) return false
        return true
    }

    fun isNewerVersion(current: String, latest: String): Boolean =
        compareVersions(current, latest) < 0

    /**
     * Reads the product version from a Point & Shoot APK name.
     * Examples: `Point-and-Shoot-0.14.0-beta.20.apk`, `point-and-shoot-1.0.0-foss.apk`.
     * Git/template tags such as `v0.15.0` are ignored.
     */
    fun parseApkVersion(name: String): String? {
        val match =
            APK_NAME_REGEX.find(name.trim()) ?: return null
        var version = match.groupValues[1]
        if (version.endsWith(FOSS_SUFFIX, ignoreCase = true)) {
            version = version.dropLast(FOSS_SUFFIX.length)
        }
        if (!CORE_VERSION_REGEX.containsMatchIn(version)) return null
        return version
    }

    fun selectApkAsset(assets: List<NamedAsset>): ProductAsset? {
        val matches =
            assets.mapNotNull { asset ->
                val version = parseApkVersion(asset.name) ?: return@mapNotNull null
                if (asset.url.isBlank()) return@mapNotNull null
                ProductAsset(version, asset.url)
            }
        return matches.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    fun evaluateLaunch(
        currentVersion: String,
        lastSeenVersion: String?,
        lastCheckAt: Long?,
        dismissedVersion: String?,
        now: Long,
        fetchLatest: () -> GithubRelease?,
    ): LaunchEvaluation {
        if (shouldNudgeDonate(lastSeenVersion, currentVersion)) {
            return LaunchEvaluation(LaunchPrompt.Donate(currentVersion))
        }
        if (!shouldCheckDaily(lastCheckAt, now)) {
            return LaunchEvaluation(LaunchPrompt.None, markSeenVersion = currentVersion)
        }
        val release =
            try {
                fetchLatest()
            } catch (_: Exception) {
                null
            }
        val asset = release?.let { selectApkAsset(it.assets) }
        val latest = asset?.version
        if (!shouldPromptUpdate(currentVersion, latest, dismissedVersion) || latest == null) {
            return LaunchEvaluation(
                LaunchPrompt.None,
                markSeenVersion = currentVersion,
                markCheckedAt = now,
            )
        }
        val url =
            asset.url.ifBlank { null }
                ?: release.htmlUrl.takeIf { it.isNotBlank() }
                ?: RELEASES_PAGE
        return LaunchEvaluation(
            LaunchPrompt.Update(latest, url),
            markSeenVersion = currentVersion,
            markCheckedAt = now,
        )
    }

    data class GithubRelease(
        val htmlUrl: String,
        val assets: List<NamedAsset>,
    )

    internal fun compareVersions(left: String, right: String): Int {
        val a = parseSemver(left)
        val b = parseSemver(right)
        for (i in 0 until SEMVER_CORE_PARTS) {
            val diff = a.core[i] - b.core[i]
            if (diff != 0) return diff
        }
        if (a.pre == null && b.pre == null) return 0
        if (a.pre == null) return 1
        if (b.pre == null) return -1
        val n = maxOf(a.pre.size, b.pre.size)
        for (i in 0 until n) {
            val ia = a.pre.getOrNull(i)
            val ib = b.pre.getOrNull(i)
            if (ia == null) return -1
            if (ib == null) return 1
            val na = ia.toIntOrNull()
            val nb = ib.toIntOrNull()
            val cmp =
                when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1
                    nb != null -> 1
                    else -> ia.compareTo(ib)
                }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private data class ParsedSemver(val core: List<Int>, val pre: List<String>?)

    private fun parseSemver(raw: String): ParsedSemver {
        val trimmed = raw.trim().removePrefix("v")
        val noBuild = trimmed.substringBefore('+')
        val dash = noBuild.indexOf('-')
        val coreStr = if (dash >= 0) noBuild.substring(0, dash) else noBuild
        val preStr = if (dash >= 0) noBuild.substring(dash + 1) else null
        val parsedCore = coreStr.split('.').map { it.toIntOrNull() ?: 0 }
        val core =
            when {
                parsedCore.size >= SEMVER_CORE_PARTS -> parsedCore.take(SEMVER_CORE_PARTS)
                else -> parsedCore + List(SEMVER_CORE_PARTS - parsedCore.size) { 0 }
            }
        return ParsedSemver(core, preStr?.split('.'))
    }

    private const val FOSS_SUFFIX = "-foss"
    private const val SEMVER_CORE_PARTS = 3

    private val APK_NAME_REGEX =
        Regex("""(?i)^point-and-shoot-(.+)\.apk$""")

    private val CORE_VERSION_REGEX = Regex("""\d+\.\d+\.\d+""")
}
