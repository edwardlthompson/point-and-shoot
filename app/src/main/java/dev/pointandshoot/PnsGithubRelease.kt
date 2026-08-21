package dev.pointandshoot

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PnsGithubRelease {
    private const val TIMEOUT_MS = 10_000
    private const val HTTP_OK = 200

    fun fetchLatest(context: Context): PnsProductUpdate.GithubRelease? {
        val conn = URL(PnsProductUpdate.RELEASES_API).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Point-and-Shoot/${PnsAppInfo.versionName(context)}")
            if (conn.responseCode != HTTP_OK) return null
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String): PnsProductUpdate.GithubRelease? {
        return try {
            val root = JSONObject(json)
            val htmlUrl = root.optString("html_url", PnsProductUpdate.RELEASES_PAGE)
            val assets = mutableListOf<PnsProductUpdate.NamedAsset>()
            val arr = root.optJSONArray("assets") ?: return PnsProductUpdate.GithubRelease(htmlUrl, assets)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val url = item.optString("browser_download_url")
                if (name.isNotBlank() && url.isNotBlank()) {
                    assets.add(PnsProductUpdate.NamedAsset(name, url))
                }
            }
            PnsProductUpdate.GithubRelease(htmlUrl, assets)
        } catch (_: Exception) {
            null
        }
    }
}
