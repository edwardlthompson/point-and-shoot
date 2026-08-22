@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context

/** Last stream set that opened — used after a camera fault to tell the user what to retry. */
object PnsLastGoodSession {
    private const val PREFS = "pns_session_recovery"
    private const val KEY_CAMERA = "camera_id"
    private const val KEY_FPS = "fps"
    private const val KEY_RAW = "want_raw"
    private const val KEY_SUMMARY = "summary"

    data class Snapshot(
        val cameraId: String,
        val fps: Int,
        val wantRaw: Boolean,
        val summary: String,
    )

    fun save(context: Context, snapshot: Snapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CAMERA, snapshot.cameraId)
            .putInt(KEY_FPS, snapshot.fps)
            .putBoolean(KEY_RAW, snapshot.wantRaw)
            .putString(KEY_SUMMARY, snapshot.summary)
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val camera = prefs.getString(KEY_CAMERA, null)?.takeIf { it.isNotBlank() } ?: return null
        return Snapshot(
            cameraId = camera,
            fps = prefs.getInt(KEY_FPS, 30),
            wantRaw = prefs.getBoolean(KEY_RAW, false),
            summary = prefs.getString(KEY_SUMMARY, "") ?: "",
        )
    }

    fun formatHint(snapshot: Snapshot?): String? {
        if (snapshot == null) return null
        return "Last good session: camera ${snapshot.cameraId}, ${snapshot.fps} fps" +
            if (snapshot.wantRaw) ", RAW on" else ""
    }
}
