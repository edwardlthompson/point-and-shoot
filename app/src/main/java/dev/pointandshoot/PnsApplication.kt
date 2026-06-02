package dev.pointandshoot

import android.content.Context
import android.app.Application
import androidx.multidex.MultiDex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Process entry for startup tracing ([PnsStartupTrace]) used by [PERFORMANCE_BUDGETS.md].
 * [MainActivity] continues to own [PnsLog.init] so diagnostics policy stays unchanged.
 */
class PnsApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        ExperimentalSafeModeStore.recordAppLaunchAttempt(this)
        if (ExperimentalSafeModeStore.isSafeModeActive(this)) {
            ExperimentalSafeModeStore.disableExperimentalFlags(this)
        }
        PnsStartupTrace.recordApplicationOnCreate()
        GlobalScope.launch { MediaCodecCapabilityProbe.probe() }
        GlobalScope.launch { CameraXExtensionProbe.probe(this@PnsApplication) }
        GlobalScope.launch { SceneVendorHintProbe.probe(this@PnsApplication) }
    }
}
