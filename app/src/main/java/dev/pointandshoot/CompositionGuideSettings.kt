package dev.pointandshoot

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Width ÷ height of the visible crop guide box. */
enum class CropGuideAspect(val label: String, val widthOverHeight: Float) {
    OFF("Off", 0f),
    R1_1("1 : 1", 1f),
    R4_3("4 : 3", 4f / 3f),
    R3_2("3 : 2", 3f / 2f),
    R16_9("16 : 9", 16f / 9f),
    R235_1("2.39 : 1", 2.39f),
    R9_16("9 : 16", 9f / 16f),
    ;

    fun next(): CropGuideAspect = entries[(ordinal + 1) % entries.size]
}

enum class GridOverlayMode(val label: String) {
    OFF("Off"),
    RULE_OF_THIRDS("Rule of thirds"),
    GOLDEN_RATIO_LINES("Golden ratio"),
    GOLDEN_SPIRAL("Golden spiral"),
    DIAGONALS("Diagonals"),
    SQUARE_3X3("3 × 3 grid"),
    ;

    fun next(): GridOverlayMode = entries[(ordinal + 1) % entries.size]
}

data class CompositionGuideSettings(
    val cropGuide: CropGuideAspect = CropGuideAspect.OFF,
    val gridMode: GridOverlayMode = GridOverlayMode.OFF,
) {
    companion object {
        const val PREFS_NAME = "pns_composition_guides"

        private const val KEY_CROP = "crop_guide"
        private const val KEY_GRID = "grid_mode"

        fun load(context: Context): CompositionGuideSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = CompositionGuideSettings()
            val cropOrd = prefs.getInt(KEY_CROP, defaults.cropGuide.ordinal)
            val gridOrd = prefs.getInt(KEY_GRID, defaults.gridMode.ordinal)
            return CompositionGuideSettings(
                cropGuide = CropGuideAspect.entries.getOrElse(cropOrd) { CropGuideAspect.OFF },
                gridMode = GridOverlayMode.entries.getOrElse(gridOrd) { GridOverlayMode.OFF },
            )
        }

        fun save(context: Context, value: CompositionGuideSettings) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_CROP, value.cropGuide.ordinal)
                .putInt(KEY_GRID, value.gridMode.ordinal)
                .apply()
        }
    }
}

@Composable
fun rememberCompositionGuideSettings(): CompositionGuideSettingsState {
    val context = LocalContext.current
    var current by remember { mutableStateOf(CompositionGuideSettings.load(context)) }

    LaunchedEffect(Unit) {
        current = CompositionGuideSettings.load(context)
    }

    return remember(current) {
        CompositionGuideSettingsState(
            current = current,
            update = { next ->
                CompositionGuideSettings.save(context, next)
                current = next
            },
        )
    }
}

class CompositionGuideSettingsState(
    val current: CompositionGuideSettings,
    val update: (CompositionGuideSettings) -> Unit,
)
