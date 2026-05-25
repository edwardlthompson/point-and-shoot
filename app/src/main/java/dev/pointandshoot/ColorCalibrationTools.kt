package dev.pointandshoot

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Sprint **CC.3** — chart calibration I/O (export / import) without an in-app RAW editor.
 * Profiles are produced by [CalibrateScreen] / live chart overlay + [CalibrationProfileStorage].
 */
object ColorCalibrationTools {
    private const val TAG = "PNS.ColorCal"

    data class ExportResult(
        val file: File,
        val profile: CalibrationProfile,
    )

    /** Copy the newest saved profile to `files/color_calibration/export_<utc>.json`. */
    fun exportLatestProfile(context: Context): ExportResult? {
        val app = context.applicationContext
        val name = CalibrationProfileStorage.list(app).firstOrNull() ?: return null
        val profile = runCatching { CalibrationProfileStorage.load(name) }.getOrNull() ?: return null
        val dir = File(app.filesDir, "color_calibration").apply { mkdirs() }
        val utc = CalibrationProfileStorage.nowUtcTimestamp()
        val out = File(dir, "export_$utc.json")
        val json = CalibrationProfileJsonAdapter.encode(profile)
        out.writeText(json, Charsets.UTF_8)
        Log.i(TAG, "exportLatest path=${out.absolutePath} target=${profile.targetId}")
        return ExportResult(out, profile)
    }

    /** Import JSON from [uri] (SAF) into [CalibrationProfileStorage]. */
    fun importProfileFromUri(context: Context, uri: android.net.Uri): CalibrationProfile? {
        val app = context.applicationContext
        val json =
            app.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return null
        val profile = runCatching { CalibrationProfileJsonAdapter.decode(json) }.getOrNull() ?: return null
        val saved = CalibrationProfileStorage.save(app, profile) ?: return null
        Log.i(TAG, "importProfile ok path=${saved.absolutePath} target=${profile.targetId}")
        return profile
    }

    fun summaryLine(profile: CalibrationProfile): String =
        "WB r=${"%.3f".format(profile.wbGains.r)} g=${"%.3f".format(profile.wbGains.g)} " +
            "b=${"%.3f".format(profile.wbGains.b)} target=${profile.targetId} cam=${profile.cameraId}"
}
