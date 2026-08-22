package dev.pointandshoot

import android.content.Context

/** Optional JPEG Artist / Copyright. Never written to DNG. */
class PnsJpegCreditPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun artist(): String = prefs.getString(KEY_ARTIST, "")?.trim().orEmpty()

    fun copyright(): String = prefs.getString(KEY_COPYRIGHT, "")?.trim().orEmpty()

    fun saveArtist(value: String) {
        prefs.edit().putString(KEY_ARTIST, value.trim().take(CREDIT_MAX)).apply()
    }

    fun saveCopyright(value: String) {
        prefs.edit().putString(KEY_COPYRIGHT, value.trim().take(CREDIT_MAX)).apply()
    }

    companion object {
        const val PREFS = "pns_jpeg_credits"
        const val CREDIT_MAX = 128
        private const val KEY_ARTIST = "artist"
        private const val KEY_COPYRIGHT = "copyright"
    }
}
