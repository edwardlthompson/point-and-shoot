package dev.pointandshoot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PnsGithubRelease {
    private const val TIMEOUT_MS = 10_000
    private const val HTTP_OK = 200
    private const val HTTP_NOT_MODIFIED = 304
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY = 429

    enum class Status { Ok, RateLimited, Failed }

    @Volatile
    var lastStatus: Status = Status.Failed
        private set

    fun fetchLatest(context: Context): PnsProductUpdate.GithubRelease? {
        val prefs = PnsUpdatePrefs(context)
        val conn = URL(PnsProductUpdate.RELEASES_API).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Point-and-Shoot/${PnsAppInfo.versionName(context)}")
            val etag = prefs.etag()
            if (!etag.isNullOrBlank()) {
                conn.setRequestProperty("If-None-Match", etag)
            }
            when (conn.responseCode) {
                HTTP_NOT_MODIFIED -> {
                    lastStatus = Status.Ok
                    prefs.cachedReleaseJson()?.let { parseList(it) }
                }
                HTTP_OK -> {
                    lastStatus = Status.Ok
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    prefs.saveFetchCache(conn.getHeaderField("ETag"), body)
                    parseList(body)
                }
                HTTP_FORBIDDEN, HTTP_TOO_MANY -> {
                    lastStatus = Status.RateLimited
                    null
                }
                else -> {
                    lastStatus = Status.Failed
                    null
                }
            }
        } catch (_: Exception) {
            lastStatus = Status.Failed
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Single GitHub release object. */
    fun parse(json: String): PnsProductUpdate.GithubRelease? {
        return try {
            parseObject(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    /** GitHub `/releases` array, including pre-releases (drafts skipped). */
    fun parseList(json: String): PnsProductUpdate.GithubRelease? {
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("{")) return parse(trimmed)
            val arr = JSONArray(trimmed)
            val releases = mutableListOf<PnsProductUpdate.GithubRelease>()
            for (i in 0 until arr.length()) {
                val parsed = parsePublishedRelease(arr.optJSONObject(i)) ?: continue
                releases.add(parsed)
            }
            val assets = releases.flatMap { it.assets }
            val picked = PnsProductUpdate.selectApkAsset(assets)
            val winner =
                releases.firstOrNull { rel ->
                    rel.assets.any { it.url == picked?.url }
                }
            PnsProductUpdate.GithubRelease(
                htmlUrl = winner?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: releases.firstOrNull()?.htmlUrl
                    ?: PnsProductUpdate.RELEASES_PAGE,
                assets = assets,
                notes = winner?.notes.orEmpty(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePublishedRelease(item: JSONObject?): PnsProductUpdate.GithubRelease? {
        if (item == null || item.optBoolean("draft")) return null
        return parseObject(item)
    }

    private fun parseObject(root: JSONObject): PnsProductUpdate.GithubRelease? {
        return try {
            val htmlUrl = root.optString("html_url", PnsProductUpdate.RELEASES_PAGE)
            val notes = root.optString("body", "")
            val assets = mutableListOf<PnsProductUpdate.NamedAsset>()
            val arr = root.optJSONArray("assets")
                ?: return PnsProductUpdate.GithubRelease(htmlUrl, assets, notes)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val url = item.optString("browser_download_url")
                if (name.isNotBlank() && url.isNotBlank()) {
                    assets.add(
                        PnsProductUpdate.NamedAsset(
                            name = name,
                            url = url,
                            sizeBytes = item.optLong("size", 0L),
                        ),
                    )
                }
            }
            PnsProductUpdate.GithubRelease(htmlUrl, assets, notes)
        } catch (_: Exception) {
            null
        }
    }
}
