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

/**
 * Buffer dimensions for preview **aspect** (layout + GLES `viewToBufferUv`), after sensor
 * quarter-turn. Raw HAL sizes are often landscape (e.g. 1920×1440) while [SurfaceTexture]
 * + `SENSOR_ORIENTATION` 90° presents portrait — using raw WxH in the shader squashes the image.
 */
internal fun previewBufferDimensionsForDisplay(
    bufW: Int,
    bufH: Int,
    @Suppress("UNUSED_PARAMETER") sensorOrientationDeg: Int?,
): Pair<Int, Int> {
    if (bufW <= 0 || bufH <= 0) return bufW to bufH
    // Portrait chrome: use taller-than-wide extent. HAL often reports 1920×1440 while the
    // stream is already 1440×1920 after setDefaultBufferSize — only swap when still landscape.
    return if (bufW > bufH) bufH to bufW else bufW to bufH
}
