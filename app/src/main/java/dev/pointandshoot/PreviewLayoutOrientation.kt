package dev.pointandshoot

/**
 * Effective static preview rotation from [PreviewChromePreferences.staticPreviewRotationDeg] only.
 * We intentionally do **not** add portrait vs landscape offsets here: coupling rotation to
 * [Configuration] caused the finder to jump when the device rotated; the preview stays visually
 * locked unless the user changes "Spin (preview)".
 *
 * Stored Spin values are quarter-turn steps; we apply a fixed **−90°** correction so the default
 * finder matches portrait-held sensor buffers on reference hardware (see **Spin (preview)** label).
 */
internal fun effectivePreviewStaticRotationDeg(staticDeg: Int, @Suppress("UNUSED_PARAMETER") layoutPortrait: Boolean): Int =
    PreviewChromePreferences.normalizeStaticRotation(staticDeg - 90)
