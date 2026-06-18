package dev.pointandshoot.preview.session

import dev.pointandshoot.CommandDialMode
import dev.pointandshoot.ReadoutAeCoupling

/**
 * Pure log-line builders for `PNS.PreviewSessionCtx` (extracted from PreviewEngineScreen — H.CRI-5 slice 2).
 */
object PreviewSessionContextDiag {
    fun regularSessionLogLine(
        displayHz: Float?,
        desiredFps: Int,
        commandDialMode: CommandDialMode,
        manualIsoOverride: Int?,
        manualExposureNsOverride: Long?,
        wantChase: Boolean,
        wantYuv: Boolean,
        yuvAttached: Boolean,
        recordSurfacePresent: Boolean,
        automationSuppressFacePipeline: Boolean,
        sessionGen: Long,
    ): String =
        buildString {
            append("PNS.PreviewSessionCtx ")
            append("defaultDisplayHz=")
            append(if (displayHz != null) "%.1f".format(displayHz) else "?")
            append(" desiredFps=").append(desiredFps)
            append(" dial=").append(commandDialMode.name)
            append(" aeCoupling=")
            append(ReadoutAeCoupling.fromOverrides(manualIsoOverride, manualExposureNsOverride).name)
            append(" wantChase=").append(wantChase)
            append(" useHighSpeed=").append(false)
            append(" wantYuv=").append(wantYuv)
            append(" yuvAttached=").append(yuvAttached)
            append(" recordSurface=").append(recordSurfacePresent)
            append(" suppressFacePipeline=").append(automationSuppressFacePipeline)
            append(" sessionGen=").append(sessionGen)
        }

    fun hfrSessionLogLine(
        displayHz: Float?,
        desiredFps: Int,
        commandDialMode: CommandDialMode,
        automationSuppressFacePipeline: Boolean,
        sessionGen: Long,
    ): String =
        buildString {
            append("PNS.PreviewSessionCtx ")
            append("defaultDisplayHz=")
            append(if (displayHz != null) "%.1f".format(displayHz) else "?")
            append(" desiredFps=").append(desiredFps)
            append(" dial=").append(commandDialMode.name)
            append(" useHighSpeed=").append(true)
            append(" wantYuv=").append(false)
            append(" yuvAttached=").append(false)
            append(" suppressFacePipeline=").append(automationSuppressFacePipeline)
            append(" sessionGen=").append(sessionGen)
        }
}
