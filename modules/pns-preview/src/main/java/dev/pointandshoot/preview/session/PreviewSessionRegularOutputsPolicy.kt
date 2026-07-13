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
        /**
         * ProShot-parity RAW session: drop face/hist/zebra/QR analysis YUV after H/chase gates.
         * Wired from still IQ + pure-HAL + RAW reader present — never via
         * [automationSuppressFacePipeline] alone for sequential RAW.
         */
        val omitYuvForPureHalRawStillSession: Boolean = false,
    )

    fun wantsYuvAnalysis(input: YuvAnalysisInput): Boolean {
        val underHfr = input.desiredFps < PreviewVideoConstants.HFR_THRESHOLD_FPS
        // Highlight + readout chase must keep the analysis surface in the session graph even if
        // createSession races a brief ON_PAUSE (lifecycleBackgroundPaused). Processing still
        // no-ops when paused; omitting the surface leaves H stuck with wantYuv=false forever.
        if (input.commandDialMode == CommandDialMode.H && underHfr) return true
        if (input.wantsReadoutExposureChase && underHfr) return true
        if (input.lifecycleBackgroundPaused) return false
        // ProShot still path has no analysis YUV; strip face/hist/zebra/QR only (H/chase already returned).
        if (input.omitYuvForPureHalRawStillSession) return false
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
        return wantsFaceOrQrYuv
    }
}
