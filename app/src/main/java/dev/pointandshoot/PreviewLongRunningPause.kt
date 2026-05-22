package dev.pointandshoot

/**
 * Sprint **PO.2** — lifecycle pause for expensive preview background work (YUV analysis, FPS sweep).
 *
 * While paused, [PreviewController.lifecycleBackgroundPaused] suppresses optional YUV paths;
 * sweep coroutines should call [shouldContinueSweep] between steps.
 */
object PreviewLongRunningPause {
    @Volatile
    var paused: Boolean = false
        private set

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun shouldContinueSweep(): Boolean = !paused
}
