package dev.pointandshoot

/**
 * Quiet donate + GitHub update rules (same method as Continuum Calendar).
 *
 * Product versions come from installer filenames, not git/template tags.
 */
object PnsProductUpdate {
    const val MS_DAY = 86_400_000L
    const val MS_HOUR = 3_600_000L
    const val MS_MINUTE = 60_000L
    private const val BYTES_PER_MIB = 1_048_576L
    private const val HALF_MIB = 524_288L

    const val RELEASES_API: String = PNS_GITHUB_RELEASES_API_URL

    const val RELEASES_PAGE: String = PNS_GITHUB_RELEASES_LATEST_URL

    data class NamedAsset(val name: String, val url: String, val sizeBytes: Long = 0L)

    data class ProductAsset(
        val version: String,
        val url: String,
        val sha256Url: String? = null,
        val sizeBytes: Long = 0L,
    )

    sealed class LaunchPrompt {
        data object None : LaunchPrompt()

        data class Donate(val currentVersion: String) : LaunchPrompt()

        data class Update(
            val version: String,
            val url: String,
            val sha256Url: String? = null,
            val sizeBytes: Long = 0L,
        ) : LaunchPrompt()
    }

    data class LaunchEvaluation(
        val prompt: LaunchPrompt,
        val markSeenVersion: String? = null,
        val markCheckedAt: Long? = null,
        val fetchSucceeded: Boolean = false,
        val markKnownGithubVersion: String? = null,
        val markReleaseNotes: String? = null,
    )

    fun formatLastChecked(lastCheckAt: Long?, now: Long): String? {
        if (lastCheckAt == null || lastCheckAt <= 0L || now < lastCheckAt) return null
        val elapsed = now - lastCheckAt
        val label =
            when {
                elapsed < MS_MINUTE -> "just now"
                elapsed < MS_HOUR -> "${elapsed / MS_MINUTE}m ago"
                elapsed < MS_DAY -> "${elapsed / MS_HOUR}h ago"
                else -> "${elapsed / MS_DAY}d ago"
            }
        return "Last checked $label"
    }

    fun sanitizeReleaseNotes(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.replace(HTML_TAG_REGEX, " ")
        text = text.replace("&nbsp;", " ", ignoreCase = true)
        text = text.replace("&amp;", "&", ignoreCase = true)
        text = text.replace("&lt;", "<", ignoreCase = true)
        text = text.replace("&gt;", ">", ignoreCase = true)
        text = text.replace(WHITESPACE_REGEX, " ")
        text = text.replace(MULTI_BLANK_REGEX, "\n\n")
        return text.trim().take(NOTES_MAX_CHARS)
    }

    fun formatMegabytes(bytes: Long): String? {
        if (bytes <= 0L) return null
        val mb = (bytes + HALF_MIB) / BYTES_PER_MIB
        return "${mb.coerceAtLeast(1L)} MB"
    }

    fun pendingVersionAlreadyInstalled(pendingVersion: String?, installedVersion: String): Boolean {
        val pending = pendingVersion?.trim().orEmpty()
        return pending.isNotEmpty() && pending == installedVersion.trim()
    }

    fun formatSha256Short(hex: String?): String? {
        val clean = hex?.trim()?.lowercase().orEmpty()
        if (clean.length < SHA256_SHORT_CHARS) return null
        if (!clean.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return clean.take(SHA256_SHORT_CHARS)
    }

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
        val shaByApk =
            assets
                .filter { it.name.endsWith(SHA256_SUFFIX, ignoreCase = true) && it.url.isNotBlank() }
                .associate { it.name.dropLast(SHA256_SUFFIX.length).lowercase() to it.url }
        val matches =
            assets.mapNotNull { asset ->
                val version = parseApkVersion(asset.name) ?: return@mapNotNull null
                if (asset.url.isBlank()) return@mapNotNull null
                ProductAsset(
                    version = version,
                    url = asset.url,
                    sha256Url = shaByApk[asset.name.lowercase()],
                    sizeBytes = asset.sizeBytes,
                )
            }
        return matches.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    /** First token of `hash  filename` or a bare 64-char hex digest. */
    fun parseSha256Sidecar(text: String): String? {
        val token = text.trim().substringBefore(' ').substringBefore('\t')
        if (token.length != SHA256_HEX_LEN) return null
        if (!token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return token.lowercase()
    }

    fun evaluateLaunch(
        currentVersion: String,
        lastSeenVersion: String?,
        lastCheckAt: Long?,
        dismissedVersion: String?,
        now: Long,
        fetchLatest: () -> GithubRelease?,
        skipNetwork: Boolean = false,
        forceCheck: Boolean = false,
    ): LaunchEvaluation {
        if (!forceCheck && shouldNudgeDonate(lastSeenVersion, currentVersion)) {
            return LaunchEvaluation(LaunchPrompt.Donate(currentVersion))
        }
        if (!forceCheck && !shouldCheckDaily(lastCheckAt, now)) {
            return LaunchEvaluation(LaunchPrompt.None, markSeenVersion = currentVersion)
        }
        if (skipNetwork) {
            return LaunchEvaluation(LaunchPrompt.None, markSeenVersion = currentVersion)
        }
        val release =
            try {
                fetchLatest()
            } catch (_: Exception) {
                null
            }
        if (release == null) {
            return LaunchEvaluation(LaunchPrompt.None, markSeenVersion = currentVersion)
        }
        val asset = selectApkAsset(release.assets)
        val dismissed = if (forceCheck) null else dismissedVersion
        if (asset == null || !shouldPromptUpdate(currentVersion, asset.version, dismissed)) {
            return LaunchEvaluation(
                LaunchPrompt.None,
                markSeenVersion = currentVersion,
                markCheckedAt = now,
                fetchSucceeded = true,
                markKnownGithubVersion = asset?.version,
                markReleaseNotes = sanitizeReleaseNotes(release.notes).takeIf { it.isNotBlank() },
            )
        }
        val url =
            asset.url.ifBlank { null }
                ?: release.htmlUrl.takeIf { it.isNotBlank() }
                ?: RELEASES_PAGE
        return LaunchEvaluation(
            LaunchPrompt.Update(asset.version, url, asset.sha256Url, asset.sizeBytes),
            markSeenVersion = currentVersion,
            markCheckedAt = now,
            fetchSucceeded = true,
            markKnownGithubVersion = asset.version,
            markReleaseNotes = sanitizeReleaseNotes(release.notes).takeIf { it.isNotBlank() },
        )
    }

    data class GithubRelease(
        val htmlUrl: String,
        val assets: List<NamedAsset>,
        val notes: String = "",
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
    private const val SHA256_SUFFIX = ".sha256"
    internal const val SHA256_HEX_LEN = 64
    internal const val SHA256_SHORT_CHARS = 12
    private const val NOTES_MAX_CHARS = 2_000
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val WHITESPACE_REGEX = Regex("[\\t ]+")
    private val MULTI_BLANK_REGEX = Regex("\\n{3,}")

    private val APK_NAME_REGEX =
        Regex("""(?i)^point-and-shoot-(.+)\.apk$""")

    private val CORE_VERSION_REGEX = Regex("""\d+\.\d+\.\d+""")
}
