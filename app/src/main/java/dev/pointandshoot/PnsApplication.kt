package dev.pointandshoot

import android.content.Context
import android.app.Application
import androidx.multidex.MultiDex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Process entry for startup tracing ([PnsStartupTrace]) used by [PERFORMANCE_BUDGETS.md].
 * [MainActivity] continues to own [PnsLog.init] so diagnostics policy stays unchanged.
 */
class PnsApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        ExperimentalSafeModeStore.recordAppLaunchAttempt(this)
        if (ExperimentalSafeModeStore.isSafeModeActive(this)) {
            ExperimentalSafeModeStore.disableExperimentalFlags(this)
        }
        PnsStartupTrace.recordApplicationOnCreate()
        appScope.launch { MediaCodecCapabilityProbe.probe() }
        appScope.launch { CameraXExtensionProbe.probe(this@PnsApplication) }
        appScope.launch { SceneVendorHintProbe.probe(this@PnsApplication) }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
