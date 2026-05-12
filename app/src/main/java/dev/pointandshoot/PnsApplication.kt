package dev.pointandshoot

import android.app.Application

/**
 * Process entry for startup tracing ([PnsStartupTrace]) used by [PERFORMANCE_BUDGETS.md].
 * [MainActivity] continues to own [PnsLog.init] so diagnostics policy stays unchanged.
 */
class PnsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PnsStartupTrace.recordApplicationOnCreate()
    }
}
