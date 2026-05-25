package dev.pointandshoot

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Sprint **UX.1** — appearance prefs (separate from HUD capture toggles). */
enum class PnsThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromStorage(name: String?): PnsThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: System
    }
}

object UxSettings {
    const val PREFS_NAME = "pns_ux_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun loadThemeMode(context: Context): PnsThemeMode {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PnsThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))
    }

    fun saveThemeMode(context: Context, mode: PnsThemeMode) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .commit()
    }

    /** Uses [Configuration.UI_MODE_NIGHT] — reliable with edge-to-edge / immersive preview. */
    fun isSystemDarkTheme(context: Context): Boolean {
        val night =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    fun resolveDarkTheme(mode: PnsThemeMode, context: Context): Boolean =
        when (mode) {
            PnsThemeMode.Dark -> true
            PnsThemeMode.Light -> false
            PnsThemeMode.System -> isSystemDarkTheme(context)
        }

    @Composable
    fun resolveDarkTheme(mode: PnsThemeMode): Boolean {
        val context = LocalContext.current
        return resolveDarkTheme(mode, context.applicationContext)
    }
}
