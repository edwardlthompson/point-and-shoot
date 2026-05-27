package dev.pointandshoot

/**
 * Sprint **15.38** — dual-ISO HDR merge stub (real merge deferred to M16).
 */
object DualIsoVideoMerger {
    fun mergePassThrough(frame: ByteArray): ByteArray = frame

    fun isSupportedOnDevice(multiResMapPresent: Boolean): Boolean = multiResMapPresent
}
