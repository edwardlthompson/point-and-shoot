package dev.pointandshoot

/**
 * Per-frame uniforms for focus peaking in [LutExternalOesShaderProgram] / `lut_preview_external.frag.glsl`.
 */
data class FocusPeakingGlUniforms(
    val enabled: Boolean,
    val r: Float,
    val g: Float,
    val b: Float,
    val sensitivity: Float,
) {
    companion object {
        /**
         * @param forceForManualVideo When true (M dial + in-app recording), peaking is drawn even if
         * the HUD color is Off — uses [FocusPeakingColor.Red] so manual-focus video is visible without
         * persisting a user preference change.
         */
        fun fromHud(
            settings: HudSettings,
            forceForManualVideo: Boolean = false,
        ): FocusPeakingGlUniforms {
            val userEnabled = settings.focusPeakingEnabled()
            if (!userEnabled && !forceForManualVideo) {
                return FocusPeakingGlUniforms(false, 0f, 0f, 0f, 0f)
            }
            val color =
                if (userEnabled) {
                    settings.focusPeakingColor
                } else {
                    FocusPeakingColor.Red
                }
            val c = color.toOverlayColor()
            return FocusPeakingGlUniforms(
                enabled = true,
                r = c.red,
                g = c.green,
                b = c.blue,
                sensitivity = settings.focusPeakingStrength.shaderSensitivity(),
            )
        }
    }
}
