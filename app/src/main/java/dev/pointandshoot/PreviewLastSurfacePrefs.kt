package dev.pointandshoot

import android.content.Context

/** Tray surface the user last had open before leaving preview (cold-start restore). */
enum class PreviewLastSurface {
    Photo,
    Video,
    Gallery,
    ;

    companion object {
        fun fromStored(name: String?): PreviewLastSurface =
            when (name) {
                Video.name -> Video
                Gallery.name -> Gallery
                else -> Photo
            }
    }
}

/**
 * Persists photo / video / in-app gallery tray state across process death and cold start.
 * ADB / [MediaStore] capture intents bypass restore via [PreviewEngineScreen] seed logic.
 */
object PreviewLastSurfacePrefs {
    private const val PREFS = "pns_preview_last_surface"
    private const val KEY_SURFACE = "last_surface"

    fun load(context: Context): PreviewLastSurface {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PreviewLastSurface.fromStored(prefs.getString(KEY_SURFACE, null))
    }

    fun save(context: Context, surface: PreviewLastSurface) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SURFACE, surface.name)
            .apply()
    }
}
