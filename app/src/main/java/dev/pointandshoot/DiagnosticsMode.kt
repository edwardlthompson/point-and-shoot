package dev.pointandshoot

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Diagnostics mode per BUILD_PLAN §9 ("Testability hooks + diagnostics mode").
 *
 *   * In-process flag persisted to [SharedPreferences] (`pns_diagnostics`).
 *   * When ON: [dump] writes a compact diagnostics report to logcat (tag
 *     [TAG]) and to an app-private external file alongside `PROBE_RESULTS.md`
 *     so `adb pull` from `pns_hfr_autorun.ps1` automation can collect it.
 *   * When OFF: dump is a no-op (and returns null), keeping release builds
 *     quiet by default.
 */
object DiagnosticsMode {

    const val TAG = "PNS.Diagnostics"
    private const val PREFS_NAME = "pns_diagnostics"
    private const val KEY_ENABLED = "enabled"

    private val tsFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

    fun isEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "diagnostics mode -> ${if (enabled) "ON" else "OFF"}")
    }

    /**
     * Build a diagnostics report (device facts + per-camera capability summary
     * + advertised vendor key names so [VendorKeyGuard] usage is auditable
     * + active color/LUT state so support tickets are reproducible),
     * write it to logcat, and persist a copy to app external files.
     *
     * @param colorState snapshot of the runtime color-pipeline state. Defaults
     *   to [LutDiagnosticsBuilder.ActiveColorState.Default] (identity LUT,
     *   no calibration). The engine may pass its own snapshot once the UI
     *   wires LUT selection through Preferences.
     * @return the absolute path of the persisted report, or null if diagnostics
     *   mode is disabled.
     */
    fun dump(
        context: Context,
        colorState: LutDiagnosticsBuilder.ActiveColorState = LutDiagnosticsBuilder.ActiveColorState.Default,
    ): String? {
        if (!isEnabled(context)) return null

        val report = buildReport(context.applicationContext, colorState)
        Log.i(TAG, "\n$report")

        val outDir = context.applicationContext.getExternalFilesDir(null) ?: return null
        outDir.mkdirs()
        val ts = tsFormatter.format(Instant.now())
        val out = File(outDir, "diagnostics_$ts.txt")
        runCatching { out.writeText(report, Charsets.UTF_8) }
            .onFailure { Log.w(TAG, "diagnostics dump file write failed", it) }
        return out.absolutePath
    }

    private fun buildReport(
        ctx: Context,
        colorState: LutDiagnosticsBuilder.ActiveColorState,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Point & Shoot - diagnostics report")
        sb.appendLine("- Generated (UTC): ${tsFormatter.format(Instant.now())}")
        sb.appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, ABI ${Build.SUPPORTED_ABIS.joinToString()})")
        sb.appendLine("- Storage env: ${Environment.getExternalStorageState()}")
        sb.appendLine()

        sb.appendLine("## Permissions")
        listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        ).forEach { perm ->
            val granted = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            sb.appendLine("- $perm: ${if (granted) "GRANTED" else "DENIED"}")
        }
        sb.appendLine()

        sb.appendLine("## Cameras")
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cm == null) {
            sb.appendLine("- CameraManager unavailable")
            return sb.toString()
        }

        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        sb.appendLine("- IDs: ${ids.joinToString()}")

        for (id in ids) {
            val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
            if (cc == null) {
                sb.appendLine("### $id")
                sb.appendLine("- characteristics unavailable")
                continue
            }
            sb.appendLine("### Camera $id")
            sb.appendLine("- LENS_FACING: ${facingName(cc.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine(
                "- INFO_SUPPORTED_HARDWARE_LEVEL: ${cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}",
            )
            sb.appendLine(
                "- physicalCameraIds: ${runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())}",
            )
            val vendorReq = runCatching { cc.availableCaptureRequestKeys }.getOrNull()
                ?.map { it.name }
                ?.filter { it.contains("com.") || it.contains("oplus", true) || it.contains("vendor", true) }
                ?.sorted()
                .orEmpty()
            sb.appendLine("- vendor request keys (${vendorReq.size}): ${vendorReq.joinToString().ifBlank { "(none)" }}")
            val vendorSession = runCatching { cc.availableSessionKeys }.getOrNull()
                ?.map { it.name }
                ?.filter { it.contains("com.") || it.contains("oplus", true) || it.contains("vendor", true) }
                ?.sorted()
                .orEmpty()
            sb.appendLine("- vendor session keys (${vendorSession.size}): ${vendorSession.joinToString().ifBlank { "(none)" }}")
            sb.appendLine()
        }

        sb.append(LutDiagnosticsBuilder.buildSection(colorState))

        return sb.toString()
    }

    private fun facingName(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }
}
