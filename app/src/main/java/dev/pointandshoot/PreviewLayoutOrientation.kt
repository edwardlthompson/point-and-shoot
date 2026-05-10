package dev.pointandshoot

/**
 * Effective static preview rotation from [PreviewChromePreferences.staticPreviewRotationDeg] only.
 * We intentionally do **not** add portrait vs landscape offsets here: coupling rotation to
 * [Configuration] caused the finder to jump when the device rotated; the preview stays visually
 * locked unless the user changes "Spin (preview)".
 */
internal fun effectivePreviewStaticRotationDeg(staticDeg: Int, @Suppress("UNUSED_PARAMETER") layoutPortrait: Boolean): Int {
    return ((staticDeg % 360) + 360) % 360
}
