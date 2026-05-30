package dev.pointandshoot.fleet

import dev.pointandshoot.CommandDialMode
import dev.pointandshoot.PreviewFpsSupport
import dev.pointandshoot.VideoCodec
import dev.pointandshoot.VideoFormat

/**
 * Maps consumer chrome surfaces to [CameraCapabilityCatalog] ids (Milestone **17.5**).
 */
object FleetChromeVisibility {
    fun commandDialFeatureId(mode: CommandDialMode): String? =
        when (mode) {
            CommandDialMode.H -> "hud.highlight_meter"
            CommandDialMode.BKT -> "still.bracket"
            CommandDialMode.M -> "af.manual"
            CommandDialMode.Macro -> "af.macro"
            CommandDialMode.Qr -> "preview.qr"
            CommandDialMode.Dual -> "video.dual"
            CommandDialMode.Night -> "camerax.night"
            CommandDialMode.Bokeh -> "camerax.bokeh"
            else -> null
        }

    fun filterCommandDialModes(
        modes: List<CommandDialMode>,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): List<CommandDialMode> =
        modes.filter { mode ->
            commandDialFeatureId(mode)?.let { FleetUiVisibilityGate.visible(it, ctx) } ?: true
        }

    fun modeDialMenuSemantics(modes: List<CommandDialMode>): String {
        val labels = modes.joinToString(", ") { it.label }
        return "Shooting mode menu. Opens: $labels."
    }

    fun videoFormatFeatureId(format: VideoFormat): String? =
        videoFormatFeatureId(format.codec, format.resolution.width, format.resolution.height, format.frameRate)

    fun videoFormatFeatureId(codec: VideoCodec, width: Int, height: Int, fps: Int): String? {
        if (fps >= 120) return "video.hfr"
        if (width == 1920 && height == 1080 && fps == 30) {
            return "video.regular.1080p30"
        }
        return when (codec) {
            VideoCodec.H264 -> "video.h264"
            VideoCodec.H265, VideoCodec.H265_10BIT -> "video.hevc"
            VideoCodec.AV1 -> "video.av1"
            VideoCodec.VP9 -> "video.vp9"
            VideoCodec.DCG -> "video.dcg_hdr"
        }
    }

    fun filterVideoFormats(
        catalog: List<VideoFormat>,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): List<VideoFormat> =
        catalog.filter { format ->
            videoFormatFeatureId(format)?.let { FleetUiVisibilityGate.visible(it, ctx) } ?: true
        }

    fun filterFpsOptions(
        options: List<PreviewFpsSupport.QuickFpsOption>,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): List<PreviewFpsSupport.QuickFpsOption> =
        options.filter { opt ->
            if (opt.targetFps < 120) return@filter true
            if (opt.requiresRoot) return@filter true
            FleetUiVisibilityGate.visible("video.hfr", ctx)
        }

    fun showReadoutStabChip(
        stabChipLabel: String?,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): Boolean {
        if (stabChipLabel.isNullOrBlank()) return false
        return FleetUiVisibilityGate.visible("lens.ois", ctx) ||
            FleetUiVisibilityGate.visible("lens.eis", ctx)
    }

    fun showReadoutImgChip(
        primaryPhoto: Boolean,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): Boolean {
        if (!primaryPhoto) return false
        return FleetUiVisibilityGate.visible("raw.dng", ctx) ||
            FleetUiVisibilityGate.visible("still.avif", ctx)
    }
}
