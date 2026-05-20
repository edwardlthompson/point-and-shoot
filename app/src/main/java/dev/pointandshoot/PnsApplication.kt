package dev.pointandshoot

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Process entry for startup tracing ([PnsStartupTrace]) used by [PERFORMANCE_BUDGETS.md].
 * [MainActivity] continues to own [PnsLog.init] so diagnostics policy stays unchanged.
 */
class PnsApplication : Application() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        PnsStartupTrace.recordApplicationOnCreate()
        GlobalScope.launch { MediaCodecCapabilityProbe.probe() }
        GlobalScope.launch { CameraXExtensionProbe.probe(this@PnsApplication) }
        GlobalScope.launch { SceneVendorHintProbe.probe(this@PnsApplication) }
    }
}
