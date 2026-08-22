@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keywords, collections, ratings, stack hero — on-device only. */
object GalleryLibrary {
    private const val PREFS: String = "pns_gallery_library"

    data class Meta(
        val keywords: List<String> = emptyList(),
        val collections: List<String> = emptyList(),
        val rating: Int = 0,
        val rejected: Boolean = false,
        val hero: Boolean = false,
        val peopleKey: String? = null,
    )

    fun load(context: Context, uriKey: String): Meta {
        val raw = prefs(context).getString(key(uriKey), null) ?: return Meta()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(Meta())
    }

    fun save(context: Context, uriKey: String, meta: Meta) {
        prefs(context).edit().putString(key(uriKey), encode(meta).toString()).apply()
    }

    fun setRating(context: Context, uriKey: String, rating: Int) {
        val cur = load(context, uriKey)
        save(context, uriKey, cur.copy(rating = rating.coerceIn(0, 5)))
    }

    fun toggleReject(context: Context, uriKey: String): Meta {
        val cur = load(context, uriKey)
        val next = cur.copy(rejected = !cur.rejected)
        save(context, uriKey, next)
        return next
    }

    fun setHero(context: Context, groupKeys: List<String>, heroKey: String) {
        groupKeys.forEach { k ->
            val cur = load(context, k)
            save(context, k, cur.copy(hero = k == heroKey, rejected = if (k == heroKey) false else cur.rejected))
        }
    }

    fun addKeyword(context: Context, uriKey: String, word: String) {
        val cleaned = word.trim().lowercase()
        if (cleaned.isEmpty()) return
        val cur = load(context, uriKey)
        if (cur.keywords.contains(cleaned)) return
        save(context, uriKey, cur.copy(keywords = cur.keywords + cleaned))
    }

    fun addCollection(context: Context, uriKey: String, name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        val cur = load(context, uriKey)
        if (cur.collections.contains(cleaned)) return
        save(context, uriKey, cur.copy(collections = cur.collections + cleaned))
    }

    fun allCollections(context: Context): List<String> {
        val names = linkedSetOf<String>()
        prefs(context).all.values.forEach { value ->
            val raw = value as? String ?: return@forEach
            runCatching {
                val arr = JSONObject(raw).optJSONArray("collections") ?: return@runCatching
                for (i in 0 until arr.length()) names += arr.optString(i)
            }
        }
        return names.toList().sorted()
    }

    fun dayKey(epochSec: Long): String {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        day.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return day.format(java.util.Date(epochSec * 1000L))
    }

    fun groupByDay(items: List<MediaItem>): Map<String, List<MediaItem>> =
        items.groupBy { dayKey(it.date) }.toSortedMap(compareByDescending { it })

    fun travelBuckets(items: List<MediaItem>): Map<String, List<MediaItem>> {
        val withGps = items.filter { it.hasLocation }
        return withGps.groupBy { dayKey(it.date) }.toSortedMap(compareByDescending { it })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(uriKey: String): String = "m:${uriKey.hashCode()}"

    private fun encode(meta: Meta): JSONObject =
        JSONObject()
            .put("keywords", JSONArray(meta.keywords))
            .put("collections", JSONArray(meta.collections))
            .put("rating", meta.rating)
            .put("rejected", meta.rejected)
            .put("hero", meta.hero)
            .put("people", meta.peopleKey ?: "")

    private fun decode(obj: JSONObject): Meta {
        fun arr(name: String): List<String> {
            val a = obj.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        return Meta(
            keywords = arr("keywords"),
            collections = arr("collections"),
            rating = obj.optInt("rating"),
            rejected = obj.optBoolean("rejected"),
            hero = obj.optBoolean("hero"),
            peopleKey = obj.optString("people").takeIf { it.isNotBlank() },
        )
    }
}
