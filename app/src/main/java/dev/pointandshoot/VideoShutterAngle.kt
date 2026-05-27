package dev.pointandshoot

/**
 * Sprint **15.11** — video shutter-angle presets (exposure as fraction of frame interval).
 */
enum class VideoShutterAngle(val label: String, val fractionOfFrame: Double?) {
    Free("Free", null),
    Angle360("360°", 1.0),
    Angle180("180°", 0.5),
    Angle90("90°", 0.25),
    Angle45("45°", 0.125),
    ;

    /** Exposure duration in nanoseconds for [fps] (null = user/free). */
    fun exposureNsForFps(fps: Int): Long? {
        val frac = fractionOfFrame ?: return null
        val safeFps = fps.coerceAtLeast(1)
        val frameNs = 1_000_000_000.0 / safeFps.toDouble()
        return (frameNs * frac).toLong().coerceAtLeast(1L)
    }

    fun chipLabel(): String? = if (this == Free) null else label

    companion object {
        fun fromStorage(id: String?): VideoShutterAngle =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: Free
    }
}
