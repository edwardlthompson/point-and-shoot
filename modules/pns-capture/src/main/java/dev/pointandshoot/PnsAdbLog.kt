package dev.pointandshoot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Gates **`PNS.AdbValidation`** log lines so release builds do not pay string formatting / logcat
 * overhead unless the APK is debuggable (`:app` may also gate via [dev.pointandshoot.DiagnosticsMode]).
 */
object PnsAdbLog {
    const val TAG: String = "PNS.AdbValidation"

    fun isEnabled(context: Context): Boolean {
        val app = context.applicationContext
        return (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun i(context: Context, message: String) {
        if (!isEnabled(context)) return
        Log.i(TAG, message)
    }

    fun w(context: Context, message: String) {
        if (!isEnabled(context)) return
        Log.w(TAG, message)
    }

    fun e(context: Context, message: String) {
        if (!isEnabled(context)) return
        Log.e(TAG, message)
    }
}
