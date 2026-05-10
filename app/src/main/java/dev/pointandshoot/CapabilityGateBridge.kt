package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraManager

/**
 * Builds [HardwareCaps] for the wide / primary rear camera and returns
 * human-readable [CapabilityGate] lines for in-app settings (HUD, Developer menu).
 */
object CapabilityGateBridge {
    fun uiLines(context: Context): List<String> =
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList.toList()
            if (ids.isEmpty()) return@runCatching emptyList()
            val roles = BackCameraRoleResolver.resolve(cm, ids)
            val active =
                roles.wide
                    ?: ids.firstOrNull { it != "1" }
                    ?: ids.first()
            val caps = HardwareCapsSnapshot.build(cm, active, ids)
            formatEvaluateLines(CapabilityGate.evaluate(caps))
        }.getOrElse { emptyList() }

    fun formatEvaluateLines(results: List<GateResult>): List<String> =
        results.map { r ->
            val status = if (r.enabled) "ok" else "off"
            val extra = r.disabledReason?.let { reason -> " - ${reason.take(96)}" } ?: ""
            "${r.feature.displayName}: $status$extra"
        }
}
