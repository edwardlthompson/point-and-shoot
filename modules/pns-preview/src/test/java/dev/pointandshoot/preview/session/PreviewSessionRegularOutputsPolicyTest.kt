package dev.pointandshoot.preview.session

import dev.pointandshoot.CommandDialMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionRegularOutputsPolicyTest {

    @Test
    fun leanVideoWarmup_videoPrimaryWithoutRecord() {
        val input =
            PreviewSessionRegularOutputsPolicy.LeanVideoWarmupInput(
                videoPrimarySession = true,
                inAppVideoRecordingArmed = false,
                wantsRawVideoLane = false,
                adbPendingRawStillAutomationCount = 0,
                adbScriptedStillAutomationActive = false,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.isLeanVideoWarmup(input))
    }

    @Test
    fun leanVideoWarmup_falseWhenRecordingArmed() {
        val input =
            PreviewSessionRegularOutputsPolicy.LeanVideoWarmupInput(
                videoPrimarySession = true,
                inAppVideoRecordingArmed = true,
                wantsRawVideoLane = false,
                adbPendingRawStillAutomationCount = 0,
                adbScriptedStillAutomationActive = false,
            )
        assertFalse(PreviewSessionRegularOutputsPolicy.isLeanVideoWarmup(input))
    }

    @Test
    fun shouldConfigureStillReaders_blockedByLeanWarmup() {
        assertFalse(
            PreviewSessionRegularOutputsPolicy.shouldConfigureStillReaders(
                recorderPresent = false,
                leanVideoWarmup = true,
            ),
        )
    }

    @Test
    fun wantsYuvAnalysis_hDialUnder120() {
        val input =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = false,
                commandDialMode = CommandDialMode.H,
                desiredFps = 60,
                automationSuppressFacePipeline = false,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = false,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(input))
    }

    @Test
    fun wantsYuvAnalysis_hDialWhenFacePipelineSuppressed() {
        val input =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = false,
                commandDialMode = CommandDialMode.H,
                desiredFps = 60,
                automationSuppressFacePipeline = true,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = false,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(input))
    }

    @Test
    fun wantsYuvAnalysis_readoutChaseEvenWhenFacePipelineSuppressed() {
        val input =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = false,
                commandDialMode = CommandDialMode.Auto,
                desiredFps = 60,
                automationSuppressFacePipeline = true,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = false,
                smileStillEnabled = false,
                wantsReadoutExposureChase = true,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(input))
    }

    @Test
    fun wantsYuvAnalysis_hDialEvenWhenLifecyclePaused() {
        val input =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = true,
                commandDialMode = CommandDialMode.H,
                desiredFps = 60,
                automationSuppressFacePipeline = false,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = false,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(input))
    }

    @Test
    fun wantsYuvAnalysis_faceHudSkippedWhenLifecyclePaused() {
        val input =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = true,
                commandDialMode = CommandDialMode.Auto,
                desiredFps = 60,
                automationSuppressFacePipeline = false,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = true,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
            )
        assertFalse(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(input))
    }

    @Test
    fun wantsYuvAnalysis_omitPureHalRawSkipsFaceHudButKeepsH() {
        val face =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = false,
                commandDialMode = CommandDialMode.Auto,
                desiredFps = 60,
                automationSuppressFacePipeline = false,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = true,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
                omitYuvForPureHalRawStillSession = true,
            )
        assertFalse(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(face))
        val h =
            PreviewSessionRegularOutputsPolicy.YuvAnalysisInput(
                lifecycleBackgroundPaused = false,
                commandDialMode = CommandDialMode.H,
                desiredFps = 60,
                automationSuppressFacePipeline = false,
                previewHistogramEnabled = false,
                highlightClipZebraEnabled = false,
                hudFaceOverlayEnabled = true,
                smileStillEnabled = false,
                wantsReadoutExposureChase = false,
                omitYuvForPureHalRawStillSession = true,
            )
        assertTrue(PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis(h))
    }
}
