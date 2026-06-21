package dev.pointandshoot.preview.session

import dev.pointandshoot.CommandDialMode
import dev.pointandshoot.PreviewVideoConstants

/**
 * Pure gates for REGULAR (non-HFR) session output assembly in `createSession` (H.CRI-5 slice 5).
 */
object PreviewSessionRegularOutputsPolicy {
    data class LeanVideoWarmupInput(
        val videoPrimarySession: Boolean,
        val inAppVideoRecordingArmed: Boolean,
        val wantsRawVideoLane: Boolean,
        val adbPendingRawStillAutomationCount: Int,
        val adbScriptedStillAutomationActive: Boolean,
    )

    /** Video-primary preview without record arm — defer RAW/JPEG still surfaces until record starts. */
    fun isLeanVideoWarmup(input: LeanVideoWarmupInput): Boolean =
        input.videoPrimarySession &&
            !input.inAppVideoRecordingArmed &&
            !input.wantsRawVideoLane &&
            input.adbPendingRawStillAutomationCount <= 0 &&
            !input.adbScriptedStillAutomationActive

    fun shouldConfigureStillReaders(
        recorderPresent: Boolean,
        leanVideoWarmup: Boolean,
    ): Boolean = !recorderPresent && !leanVideoWarmup

    data class YuvAnalysisInput(
        val lifecycleBackgroundPaused: Boolean,
        val commandDialMode: CommandDialMode,
        val desiredFps: Int,
        val automationSuppressFacePipeline: Boolean,
        val previewHistogramEnabled: Boolean,
        val highlightClipZebraEnabled: Boolean,
        val hudFaceOverlayEnabled: Boolean,
        val smileStillEnabled: Boolean,
        val wantsReadoutExposureChase: Boolean,
    )

    fun wantsYuvAnalysis(input: YuvAnalysisInput): Boolean {
        if (input.lifecycleBackgroundPaused) return false
        val underHfr = input.desiredFps < PreviewVideoConstants.HFR_THRESHOLD_FPS
        if (input.commandDialMode == CommandDialMode.H && underHfr) return true
        val facePipelineOk = !input.automationSuppressFacePipeline
        val wantsFaceOrQrYuv =
            facePipelineOk &&
                underHfr &&
                (
                    input.commandDialMode == CommandDialMode.Qr ||
                        input.previewHistogramEnabled ||
                        input.highlightClipZebraEnabled ||
                        input.hudFaceOverlayEnabled ||
                        input.smileStillEnabled
                )
        return wantsFaceOrQrYuv || (input.wantsReadoutExposureChase && underHfr)
    }
}
