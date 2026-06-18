package dev.pointandshoot.preview.session

import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import android.view.Surface
import dev.pointandshoot.HfrInterleavedPreviewSupport
import dev.pointandshoot.InAppVideoRecordingSupport

/**
 * HFR target pick + encoder-only output assembly (H.CRI-5 slice 9).
 *
 * Extracted from `PreviewEngineScreen.pickHighSpeedTarget` and
 * `encoderOnlyRecordSessionOutputs`.
 */
object PreviewSessionHighSpeedOutputs {
    data class PickTargetInput(
        val streamConfigurationMap: StreamConfigurationMap?,
        val desiredFps: Int,
        val encodeSizePref: Size?,
        val preferSub4kCapture: Boolean,
        val useInterleavedMcPreview: Boolean,
        val inAppVideoRecordingArmed: Boolean,
        val recorderPresent: Boolean,
    )

    fun pickHighSpeedTarget(input: PickTargetInput): Pair<Size, Range<Int>>? =
        if (input.useInterleavedMcPreview && input.inAppVideoRecordingArmed && input.recorderPresent) {
            InAppVideoRecordingSupport.pickInterleavedHighSpeedVideoTarget(
                input.streamConfigurationMap,
                input.desiredFps,
                input.encodeSizePref,
            ) ?: InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
                input.streamConfigurationMap,
                input.desiredFps,
                input.encodeSizePref,
                preferSub4kCapture = input.preferSub4kCapture,
            )
        } else {
            InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
                input.streamConfigurationMap,
                input.desiredFps,
                input.encodeSizePref,
                preferSub4kCapture = input.preferSub4kCapture,
            )
        }

    data class InterleavedPreferenceInput(
        val desiredFps: Int,
        val wantsMediaCodecPath: Boolean,
        val encodePrefWidth: Int,
        val encodePrefHeight: Int,
        val hsCaptureWidth: Int,
        val hsCaptureHeight: Int,
        val preferSub4kCapture: Boolean,
        val forceInterleavedAfterConfigureFail: Boolean,
    )

    fun prefersInterleavedOverEncoderOnly(input: InterleavedPreferenceInput): Boolean =
        HfrInterleavedPreviewSupport.prefersInterleavedOverEncoderOnlyFor4KEncode(
            desiredFps = input.desiredFps,
            wantsMediaCodecPath = input.wantsMediaCodecPath,
            encodePrefWidth = input.encodePrefWidth,
            encodePrefHeight = input.encodePrefHeight,
            hsCaptureWidth = input.hsCaptureWidth,
            hsCaptureHeight = input.hsCaptureHeight,
            preferSub4kCapture = input.preferSub4kCapture,
            forceInterleavedAfterConfigureFail = input.forceInterleavedAfterConfigureFail,
        )

    fun isEncoderOnlyHfrRecording(
        useInterleavedMcPreview: Boolean,
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
    ): Boolean =
        useInterleavedMcPreview &&
            inAppVideoRecordingArmed &&
            recorderPresent

    data class LogLine(
        val tag: String,
        val message: String,
        val level: Level = Level.INFO,
    ) {
        enum class Level {
            INFO,
            WARN,
            ERROR,
        }
    }

    data class EncoderOutputInput(
        val surfaces: List<Surface>,
        val previewSurface: Surface,
        val encoderSurface: Surface?,
        val encOnlyHfr: Boolean,
        val encOnlyUhd: Boolean,
        val skipEncoderOnlyMonitor: Boolean,
        val hfrMonitorStartSucceeded: Boolean,
        val uhdMonitorStartSucceeded: Boolean,
        val desiredFps: Int,
        val bufferWidth: Int,
        val bufferHeight: Int,
        val preferInterleavedFallback: Boolean,
        val hfrInterleavedTag: String,
        val uhd60Tag: String,
        val chromeUxTag: String = "PNS.ChromeUx",
    )

    data class EncoderOutputPlan(
        val outputs: List<Surface>,
        val routeId: String? = null,
        val logs: List<LogLine> = emptyList(),
        val chromeUxLine: String? = null,
        val monitorGl: Boolean? = null,
        val hintEncoderOnlyMcRecord: Boolean = false,
    )

    fun resolveEncoderOutputPlan(input: EncoderOutputInput): EncoderOutputPlan {
        if (!input.encOnlyHfr && !input.encOnlyUhd) {
            return EncoderOutputPlan(outputs = input.surfaces.filter { it.isValid })
        }
        val enc =
            input.encoderSurface?.takeIf { it.isValid }
                ?: return EncoderOutputPlan(outputs = input.surfaces.filter { it.isValid })
        val logs = mutableListOf<LogLine>()
        var chromeUx: String? = null
        var routeId: String? = null
        var monitorGl: Boolean? = null
        var hintEncoderOnly = false

        if (input.encOnlyHfr) {
            if (!input.skipEncoderOnlyMonitor && input.hfrMonitorStartSucceeded) {
                routeId = "encoder_only_monitor"
                logs +=
                    LogLine(
                        input.hfrInterleavedTag,
                        "HFR encoder-only HS encodeFps=${input.desiredFps} " +
                            "buffer=${input.bufferWidth}x${input.bufferHeight}",
                    )
                chromeUx = "hfrEncoderOnly active=true encodeFps=${input.desiredFps} monitorFinder=yuv"
                return EncoderOutputPlan(listOf(enc), routeId, logs, chromeUx)
            }
            if (input.skipEncoderOnlyMonitor) {
                routeId = "interleaved_primary"
                logs +=
                    LogLine(
                        input.hfrInterleavedTag,
                        "4K HFR — skip encoder-only monitor; use interleaved preview+encoder " +
                            "buffer=${input.bufferWidth}x${input.bufferHeight}",
                    )
            }
        } else if (input.uhdMonitorStartSucceeded) {
            hintEncoderOnly = true
            logs +=
                LogLine(
                    input.uhd60Tag,
                    "uhd60 encoder-only REGULAR encodeFps=${input.desiredFps} monitor=yuv",
                )
            chromeUx = "uhd60EncoderOnly active=true encodeFps=${input.desiredFps} monitorFinder=yuv"
            monitorGl = true
            return EncoderOutputPlan(
                outputs = listOf(enc),
                routeId = routeId,
                logs = logs,
                chromeUxLine = chromeUx,
                monitorGl = monitorGl,
                hintEncoderOnlyMcRecord = hintEncoderOnly,
            )
        }

        val prev = input.previewSurface.takeIf { it.isValid }
        if (prev != null) {
            val interleavedTag = if (input.encOnlyHfr) input.hfrInterleavedTag else input.uhd60Tag
            if (input.encOnlyHfr && input.preferInterleavedFallback) {
                routeId = "interleaved_fallback"
                logs +=
                    LogLine(
                        interleavedTag,
                        "HFR interleaved preview+encoder encodeFps=${input.desiredFps} " +
                            "buffer=${input.bufferWidth}x${input.bufferHeight}",
                    )
            } else {
                if (input.encOnlyHfr) routeId = "interleaved_monitor_unavailable"
                logs +=
                    LogLine(
                        interleavedTag,
                        "monitor unavailable — interleaved preview+encoder fallback encodeFps=${input.desiredFps}",
                        LogLine.Level.WARN,
                    )
            }
            chromeUx =
                if (input.encOnlyHfr) {
                    "hfrEncoderOnly active=false encodeFps=${input.desiredFps} monitorFinder=interleaved"
                } else {
                    "uhd60EncoderOnly active=false encodeFps=${input.desiredFps} monitorFinder=interleaved"
                }
            monitorGl = false
            return EncoderOutputPlan(
                outputs = listOf(prev, enc),
                routeId = routeId,
                logs = logs,
                chromeUxLine = chromeUx,
                monitorGl = monitorGl,
            )
        }

        logs +=
            LogLine(
                if (input.encOnlyHfr) input.hfrInterleavedTag else input.uhd60Tag,
                "record: no monitor and no preview surface",
                LogLine.Level.ERROR,
            )
        return EncoderOutputPlan(outputs = listOf(enc), logs = logs)
    }
}
