package dev.pointandshoot

import android.content.Context
import org.json.JSONArray

/**
 * Recent + favorite probe hub entries (Milestone **10.15**). Keys are [DebugMenuScreen] entry titles
 * so relaunch does not depend on internal route wiring changing.
 */
object ProbeHubRecentsStore {
    private const val PREFS = "pns_probehub_nav"
    private const val KEY_RECENTS = "recent_titles_json"
    private const val KEY_FAVORITES = "favorite_titles_json"
    private const val MAX_RECENTS = 6

    fun recordOpen(context: Context, entryTitle: String) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = readJsonArray(p.getString(KEY_RECENTS, "[]") ?: "[]").toMutableList()
        cur.remove(entryTitle)
        cur.add(0, entryTitle)
        while (cur.size > MAX_RECENTS) cur.removeAt(cur.lastIndex)
        val ja = JSONArray()
        for (t in cur) ja.put(t)
        p.edit().putString(KEY_RECENTS, ja.toString()).apply()
    }

    fun recentTitles(context: Context): List<String> {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readJsonArray(p.getString(KEY_RECENTS, "[]") ?: "[]")
    }

    fun favoriteTitles(context: Context): Set<String> {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readJsonArray(p.getString(KEY_FAVORITES, "[]") ?: "[]").toSet()
    }

    /** Returns whether [entryTitle] is a favorite after the toggle. */
    fun toggleFavoriteTitle(context: Context, entryTitle: String): Boolean {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = readJsonArray(p.getString(KEY_FAVORITES, "[]") ?: "[]").toMutableSet()
        val nowFavorite = if (!cur.remove(entryTitle)) {
            cur.add(entryTitle)
            true
        } else {
            false
        }
        val ja = JSONArray()
        for (t in cur) ja.put(t)
        p.edit().putString(KEY_FAVORITES, ja.toString()).apply()
        return nowFavorite
    }

    fun clearAll(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().remove(KEY_RECENTS).remove(KEY_FAVORITES).apply()
    }

    private fun readJsonArray(raw: String): List<String> {
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    add(a.getString(i))
                }
            }
        }.getOrDefault(emptyList())
    }
}
