package dev.pointandshoot.preview.session

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.Range
import android.view.Surface
import dev.pointandshoot.HardwareCapsSnapshot
import dev.pointandshoot.HudSettings
import dev.pointandshoot.PreviewAeAntibanding
import dev.pointandshoot.VideoEffectsProcessor
import dev.pointandshoot.VendorKeyGuard
import java.util.concurrent.Executor

/**
 * Super-macro vendor session-parameters path for REGULAR preview sessions (H.CRI-5 slice 7).
 *
 * Extracted from `PreviewEngineScreen.createSession` macro branch.
 */
object PreviewSessionMacroParameters {
    const val OUTPUT_CONFIG_RETRY_DELAY_MS = 48L

    fun shouldAttemptMacroSessionParameters(
        wantsMacroProgram: Boolean,
        superMacroAdbProbe: Boolean,
        sdkInt: Int,
        camId: String,
        ultraWideCameraId: String?,
    ): Boolean {
        if (!wantsMacroProgram && !superMacroAdbProbe) return false
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
        val uw = ultraWideCameraId ?: return false
        return uw == camId
    }

    data class SessionParametersResult(
        val appliedKind: String?,
        val sessionParameters: CaptureRequest?,
    )

    fun buildMacroSessionParameters(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        prefs: HudSettings,
        previewFpsRange: Range<Int>?,
        macroKeyName: String = HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST,
    ): SessionParametersResult {
        val sessionReqBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        val appliedKind =
            VendorKeyGuard.trySetVendorSessionEnable(sessionReqBuilder, characteristics, macroKeyName)
                ?: VendorKeyGuard.trySetVendorRequestEnable(sessionReqBuilder, characteristics, macroKeyName)
        if (appliedKind == null) {
            return SessionParametersResult(appliedKind = null, sessionParameters = null)
        }
        PreviewAeAntibanding.applyToRequest(sessionReqBuilder, characteristics)
        VideoEffectsProcessor.applyToVideoPreviewRequest(
            sessionReqBuilder,
            characteristics,
            prefs,
            previewFpsRange = previewFpsRange,
            manualSensor = false,
        )
        return SessionParametersResult(appliedKind = appliedKind, sessionParameters = sessionReqBuilder.build())
    }

    data class MacroSessionLifecycle(
        val sessionGeneration: Long,
        val isStale: () -> Boolean,
        val onConfigured: (CameraCaptureSession) -> Unit,
        val onConfigureFailed: (CameraCaptureSession) -> Unit,
        val onStaleConfigured: (CameraCaptureSession) -> Unit,
    )

    fun macroSessionStateCallback(
        logTag: String,
        lifecycle: MacroSessionLifecycle,
        onAsyncConfigurePendingCleared: () -> Unit,
    ): CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(sess: CameraCaptureSession) {
                try {
                    if (lifecycle.isStale()) {
                        Log.w(
                            logTag,
                            "onConfigured ignored (stale gen=${lifecycle.sessionGeneration})",
                        )
                        lifecycle.onStaleConfigured(sess)
                        return
                    }
                    lifecycle.onConfigured(sess)
                } finally {
                    onAsyncConfigurePendingCleared()
                }
            }

            override fun onConfigureFailed(sess: CameraCaptureSession) {
                try {
                    if (!lifecycle.isStale()) {
                        lifecycle.onConfigureFailed(sess)
                    }
                } finally {
                    onAsyncConfigurePendingCleared()
                }
            }
        }

    sealed class CreateAttempt {
        data object OutputConfigFailed : CreateAttempt()

        data object Submitted : CreateAttempt()

        data class Threw(
            val error: Throwable,
        ) : CreateAttempt()
    }

    fun createMacroCaptureSession(
        logTag: String,
        camera: CameraDevice,
        surfaces: List<Surface>,
        chosenPreviewDr: Long?,
        sessionParameters: CaptureRequest,
        handler: Handler,
        lifecycle: MacroSessionLifecycle,
        buildOutputConfigurations: (
            surfaces: List<Surface>,
            previewDynamicRangeProfile: Long?,
        ) -> List<OutputConfiguration>,
        onOutputConfigFailed: () -> Unit,
        onAsyncConfigurePendingSet: () -> Unit,
        onAsyncConfigurePendingCleared: () -> Unit,
    ): CreateAttempt {
        val outputConfigs =
            runCatching {
                buildOutputConfigurations(surfaces, chosenPreviewDr)
            }.getOrElse { e ->
                Log.w(
                    logTag,
                    "macro session create: OutputConfiguration failed " +
                        "(${e.javaClass.simpleName}: ${e.message}); scheduling restart",
                )
                onOutputConfigFailed()
                return CreateAttempt.OutputConfigFailed
            }
        val executor: Executor = Executor { cmd -> handler.post(cmd) }
        val sessionConfig =
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                executor,
                macroSessionStateCallback(logTag, lifecycle, onAsyncConfigurePendingCleared),
            )
        sessionConfig.setSessionParameters(sessionParameters)
        onAsyncConfigurePendingSet()
        val createErr = runCatching { camera.createCaptureSession(sessionConfig) }.exceptionOrNull()
        if (createErr != null) {
            onAsyncConfigurePendingCleared()
            Log.w(
                logTag,
                "createCaptureSession(SessionConfiguration macro) threw " +
                    "${createErr::class.java.simpleName}: ${createErr.message}",
            )
            return CreateAttempt.Threw(createErr)
        }
        return CreateAttempt.Submitted
    }
}
