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
        fun fromHud(settings: HudSettings): FocusPeakingGlUniforms {
            if (!settings.focusPeakingEnabled()) {
                return FocusPeakingGlUniforms(false, 0f, 0f, 0f, 0f)
            }
            val c = settings.focusPeakingColor.toOverlayColor()
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
