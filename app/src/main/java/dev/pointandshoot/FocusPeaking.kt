package dev.pointandshoot

import androidx.compose.ui.graphics.Color

/**
 * Focus-peaking false-color choices common on cinema cameras and mirrorless bodies
 * (Sony red, Fuji magenta, Canon yellow assist, green EVF, and high-contrast white).
 */
enum class FocusPeakingColor(val displayName: String) {
    Off("Off"),
    Red("Red"),
    Magenta("Magenta"),
    Yellow("Yellow"),
    Green("Green"),
    Cyan("Cyan"),
    Blue("Blue"),
    White("White"),
    ;

    /** Short token for compact labels (settings summaries, future on-screen tags). */
    fun chipToken(): String =
        when (this) {
            Off -> "—"
            Red -> "R"
            Magenta -> "M"
            Yellow -> "Y"
            Green -> "G"
            Cyan -> "C"
            Blue -> "B"
            White -> "W"
        }

    fun toOverlayColor(): Color =
        when (this) {
            Off -> Color.Transparent
            Red -> Color(0xFFFF3B30)
            Magenta -> Color(0xFFFF2D92)
            Yellow -> Color(0xFFFFCC00)
            Green -> Color(0xFF34C759)
            Cyan -> Color(0xFF32ADE6)
            Blue -> Color(0xFF007AFF)
            White -> Color(0xFFF2F2F7)
        }
}

/** Edge-detection aggressiveness for the future peaking shader. */
enum class FocusPeakingStrength(val displayName: String) {
    Low("Low"),
    Medium("Medium"),
    High("High"),
    ;

    fun chipToken(): String =
        when (this) {
            Low -> "L"
            Medium -> "M"
            High -> "H"
        }

    /** Higher values lower the edge threshold (more aggressive peaking in the preview shader). */
    fun shaderSensitivity(): Float =
        when (this) {
            Low -> 0.28f
            Medium -> 0.55f
            High -> 0.85f
        }
}
