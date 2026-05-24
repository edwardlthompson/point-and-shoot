package dev.pointandshoot

import android.content.Context

/**
 * Gallery behavior preferences for Point & Shoot app.
 */
object GalleryPrefs {
    private const val PREFS = "pns_gallery_prefs"
    private const val KEY_USE_BESPOKE_GALLERY = "use_bespoke_gallery"
    private const val DEFAULT_USE_BESPOKE_GALLERY = true

    fun useBespokeGallery(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_BESPOKE_GALLERY, DEFAULT_USE_BESPOKE_GALLERY)
    }

    fun setUseBespokeGallery(context: Context, useBespoke: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_USE_BESPOKE_GALLERY, useBespoke)
            .commit()
    }
}
