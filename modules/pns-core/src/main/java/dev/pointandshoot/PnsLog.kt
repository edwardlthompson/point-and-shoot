package dev.pointandshoot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Centralized logging facade per BUILD_PLAN §9 "Release hardening gates":
 * "Release builds do not emit verbose debug logs by default".
 *
 * Behavior:
 *   * `v` / `d` are **muted** in non-debuggable (release) builds, unless the
 *     diagnostics toggle is on (see [DiagnosticsMode]).
 *   * `i` / `w` / `e` are always emitted - they carry product-level signal
 *     and need to survive in release for crash triage.
 *   * The "is this a debuggable build" check uses
 *     `ApplicationInfo.FLAG_DEBUGGABLE` so we don't have to enable the
 *     `BuildConfig` build feature just for one boolean.
 *
 * The first call to [init] caches the policy. Subsequent calls are cheap
 * (just a tag prefix + a flag check). If [init] is never called (e.g., from
 * a unit test or a tiny tool), the logger defaults to "release-like": only
 * `i` / `w` / `e` are written, which is the safest fallback.
 */
object PnsLog {

    @Volatile
    private var debuggable: Boolean = false

    @Volatile
    private var diagnosticsEnabled: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    /**
     * Cache the application's debuggable flag and the diagnostics-mode bit.
     * Safe to call from `Application.onCreate()` or `MainActivity.onCreate()`.
     * Idempotent: subsequent calls update the cached values.
     */
    fun init(context: Context) {
        val appCtx = context.applicationContext
        debuggable = (appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        diagnosticsEnabled = readDiagnosticsEnabled(appCtx)
        initialized = true
    }

    /** Refresh the cached diagnostics flag without re-reading `applicationInfo`. */
    fun refreshDiagnosticsFlag(context: Context) {
        diagnosticsEnabled = readDiagnosticsEnabled(context.applicationContext)
    }

    private fun readDiagnosticsEnabled(context: Context): Boolean =
        context.getSharedPreferences("pns_diagnostics", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun v(tag: String, message: String) {
        if (verboseEnabled()) safeAndroidLog { Log.v(prefixed(tag), message) }
    }

    fun d(tag: String, message: String) {
        if (verboseEnabled()) safeAndroidLog { Log.d(prefixed(tag), message) }
    }

    fun i(tag: String, message: String) {
        safeAndroidLog { Log.i(prefixed(tag), message) }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        safeAndroidLog {
            if (throwable == null) Log.w(prefixed(tag), message) else Log.w(prefixed(tag), message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        safeAndroidLog {
            if (throwable == null) Log.e(prefixed(tag), message) else Log.e(prefixed(tag), message, throwable)
        }
    }

    /** JVM unit tests lack `liblog.so`; swallow linkage failures only. */
    private inline fun safeAndroidLog(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }

    /**
     * Pure helper exposed for unit tests: returns whether `v` / `d` would be
     * emitted given a snapshot of the policy bits. Mirrors [verboseEnabled].
     */
    fun shouldEmitVerbose(isDebuggable: Boolean, isDiagnosticsEnabled: Boolean): Boolean =
        isDebuggable || isDiagnosticsEnabled

    private fun verboseEnabled(): Boolean {
        if (!initialized) return false // safe default
        return shouldEmitVerbose(debuggable, diagnosticsEnabled)
    }

    private fun prefixed(tag: String): String =
        if (tag.startsWith("PNS.")) tag else "PNS.$tag"
}
