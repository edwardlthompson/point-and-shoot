package dev.pointandshoot

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Root-only CPH2583 lane for max-resolution unlock experiments.
 */
object ExperimentalMaxResolutionUnlock {
    private const val TAG = "PNS.MaxResUnlock"
    private const val PREFS = "pns_max_res_unlock"
    private const val KEY_LAST_ATTEMPT = "last_attempt_json"
    private const val KEY_LAST_ACTIVE = "last_active"
    private const val PROP_PREVIEW_SIZE = "persist.vendor.camera.preview.size"
    private const val TARGET_SIZE = "8192x6144"
    private const val SU_TIMEOUT_SEC = 4L

    data class SessionResult(
        val active: Boolean,
        val applied: Boolean,
        val requestedValue: String?,
        val observedValue: String?,
        val reason: String,
        val cameraId: String,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("active", active)
                put("applied", applied)
                put("requestedValue", requestedValue ?: JSONObject.NULL)
                put("observedValue", observedValue ?: JSONObject.NULL)
                put("reason", reason)
                put("cameraId", cameraId)
                put("timestampUtc", java.time.Instant.now().toString())
            }
    }

    fun isCph2583LaneDevice(): Boolean {
        val model = Build.MODEL.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val device = Build.DEVICE.orEmpty()
        return listOf(model, product, device).any { it.contains("CPH2583", ignoreCase = true) }
    }

    fun applyForSession(
        context: Context,
        cameraId: String,
        stillMode: PhotoResolutionMode,
        settings: HudSettings,
    ): SessionResult {
        val app = context.applicationContext
        val safeMode = ExperimentalSafeModeStore.isSafeModeActive(app)
        val rootGranted = RootCapabilityStore.loadOrUnknown(app).grantsPrivileged
        val enabled =
            settings.enableExperimentalAppBreakingFeatures &&
                settings.enableExperimentalMaxResolutionUnlock &&
                rootGranted &&
                !safeMode &&
                stillMode == PhotoResolutionMode.MaxResolution &&
                isCph2583LaneDevice()
        if (!enabled) {
            val clearReason =
                when {
                    safeMode -> "safe_mode_active"
                    !settings.enableExperimentalAppBreakingFeatures -> "master_toggle_off"
                    !settings.enableExperimentalMaxResolutionUnlock -> "lane_toggle_off"
                    !rootGranted -> "root_not_granted"
                    stillMode != PhotoResolutionMode.MaxResolution -> "still_mode_not_max_resolution"
                    !isCph2583LaneDevice() -> "device_not_cph2583"
                    else -> "disabled"
                }
            clearOverrideIfSetByApp(app)
            return SessionResult(
                active = false,
                applied = false,
                requestedValue = null,
                observedValue = readCurrentProperty(app),
                reason = clearReason,
                cameraId = cameraId,
            ).also { persistLastAttempt(app, it) }
        }
        val setExit = runSu(app, "setprop $PROP_PREVIEW_SIZE $TARGET_SIZE").first
        val observed = readCurrentProperty(app)
        val applied = setExit == 0 && observed.equals(TARGET_SIZE, ignoreCase = true)
        if (!applied) {
            // Fail closed: if the set did not stick, clear to avoid carrying a bad state forward.
            runSu(app, "setprop $PROP_PREVIEW_SIZE \"\"")
        }
        val result =
            SessionResult(
                active = true,
                applied = applied,
                requestedValue = TARGET_SIZE,
                observedValue = observed,
                reason = if (applied) "applied" else "verify_failed",
                cameraId = cameraId,
            )
        persistLastAttempt(app, result)
        Log.i(
            TAG,
            "session active=${result.active} applied=${result.applied} cam=$cameraId req=$TARGET_SIZE obs=${result.observedValue} reason=${result.reason}",
        )
        return result
    }

    fun snapshotForMatrix(context: Context): JSONObject {
        val app = context.applicationContext
        val raw = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_ATTEMPT, null)
        val parsed = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
        return JSONObject().apply {
            put("deviceEligibleCph2583", isCph2583LaneDevice())
            put("rootGranted", RootCapabilityStore.loadOrUnknown(app).grantsPrivileged)
            put("safeModeActive", ExperimentalSafeModeStore.isSafeModeActive(app))
            put("currentlyObservedProp", readCurrentProperty(app) ?: JSONObject.NULL)
            put("lastActive", app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LAST_ACTIVE, false))
            put("lastAttempt", parsed ?: JSONObject.NULL)
        }
    }

    private fun persistLastAttempt(context: Context, result: SessionResult) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAST_ACTIVE, result.active)
            .putString(KEY_LAST_ATTEMPT, result.toJson().toString())
            .apply()
    }

    private fun clearOverrideIfSetByApp(context: Context) {
        val observed = readCurrentProperty(context)
        if (observed.isNullOrBlank()) return
        runSu(context, "setprop $PROP_PREVIEW_SIZE \"\"")
        Log.i(TAG, "clearOverride observed=$observed")
    }

    private fun readCurrentProperty(context: Context): String? {
        val (exit, out) = runSu(context, "getprop $PROP_PREVIEW_SIZE")
        if (exit != 0) return null
        val v = out.trim()
        return v.ifBlank { null }
    }

    private fun runSu(context: Context, cmd: String): Pair<Int, String> {
        val pb = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true)
        return runCatching {
            val p = pb.start()
            val done = p.waitFor(SU_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!done) {
                runCatching { p.destroyForcibly() }
                return@runCatching 124 to "timeout"
            }
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            p.exitValue() to out
        }.getOrElse { e ->
            Log.w(TAG, "su command failed cmd=$cmd err=${e.message}")
            1 to (e.message ?: "error")
        }
    }
}

