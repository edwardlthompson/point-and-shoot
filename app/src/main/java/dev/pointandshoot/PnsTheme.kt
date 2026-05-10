package dev.pointandshoot

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Point & Shoot design tokens (Material 3 + photo chrome).
 *
 * **Principles:** one dark surfaces base, high-contrast monospace readouts, orange
 * for *selected* photo controls and primary actions, record red isolated to video,
 * electric blue reserved for *root-only* affordances so they never read as generic
 * chrome. Touch targets ≥ 48dp; quick settings use short labels; diagnostics copy
 * is plain language with technical detail one level deeper.
 */
object PnsColors {
    /** Hasselblad orange - photo shutter / still-capture affordances (`#FF5C00`). */
    val PhotoOrange = Color(0xFFFF5C00)

    /** Record red - video tally + record button (`#E00000`). */
    val RecordRed = Color(0xFFE00000)

    /** Charcoal background tone (matches the launcher icon background). */
    val Charcoal = Color(0xFF181A1B)

    /** Soft warning amber for non-fatal pipeline messages. */
    val WarnAmber = Color(0xFFE6A23C)

    /** Soft success green for successful captures / probe rows. */
    val OkGreen = Color(0xFF4CAF50)

    /**
     * Root-only / vendor-unlock affordances — contrasts with [PhotoOrange] so
     * stock-safe vs elevated paths are obvious in quick settings and badges.
     */
    val RootAccentBlue = Color(0xFF4DA3FF)

    /** Dimmed preview scrim (top / bottom) for Hasselblad-style chrome legibility. */
    val PreviewScrim = Color(0xCC000000)
}

object PnsDimens {
    val chromeHorizontalPadding: Dp = 16.dp
    val chromeVerticalPadding: Dp = 12.dp
    val quickSettingsChipMinWidth: Dp = 52.dp
}

// Monospaced typography for technical readouts (Part 4 spec: JetBrains Mono).
// Vendored from upstream v2.304 (SIL OFL 1.1) - see
// `app/src/main/assets/fonts/jetbrainsmono/SOURCE.txt` for the pinned URL,
// SHA-256, and refresh procedure. The .ttf lives at
// `res/font/jetbrainsmono_regular.ttf` so Compose's `Font(R.font.*)` lookup
// works without additional asset-loader plumbing.
val MonoFamily: FontFamily = FontFamily(Font(R.font.jetbrainsmono_regular))

/**
 * Compact typography preset for HUD readouts (timecode, ISO, shutter, FPS, etc.).
 * Always monospaced so columns stay aligned even as values change.
 */
val PnsTypography: Typography = Typography(
    displayMedium = TextStyle(fontFamily = MonoFamily, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = MonoFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val DarkColors = darkColorScheme(
    primary = PnsColors.PhotoOrange,
    onPrimary = Color.Black,
    secondary = PnsColors.RecordRed,
    onSecondary = Color.White,
    background = PnsColors.Charcoal,
    surface = PnsColors.Charcoal,
)

private val LightColors = lightColorScheme(
    primary = PnsColors.PhotoOrange,
    onPrimary = Color.White,
    secondary = PnsColors.RecordRed,
    onSecondary = Color.White,
)

/**
 * Apply Point & Shoot's typography + brand colors on top of Material 3.
 *
 * Use `PnsTheme { ... }` at the top of any screen that should pick up the monospace
 * HUD typography and the photo/video brand colors. Probe-only screens can keep
 * using bare `MaterialTheme` if they want the platform defaults.
 */
@Composable
fun PnsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PnsTypography,
        content = content,
    )
}
