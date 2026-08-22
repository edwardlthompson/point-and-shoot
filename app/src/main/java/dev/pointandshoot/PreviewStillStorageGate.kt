package dev.pointandshoot

/**
 * Refuse a still when shared storage cannot hold the planned frame count.
 * Fail open if [StatFs] is unavailable so USB gates are not blocked by a probe miss.
 */
object PreviewStillStorageGate {
    /** ~25 MB DNG + JPEG companion + MediaStore slack. */
    const val MIN_FREE_BYTES: Long = 48L * 1024L * 1024L

    /** Intervalometer must have room for at least two stills before arming. */
    const val INTERVALOMETER_MIN_FRAMES: Int = 2

    /** Toast a heads-up when free space drops below this many planned stills. */
    const val FEW_STILLS_FRAMES: Int = 3

    fun requiredBytes(frameCount: Int): Long =
        MIN_FREE_BYTES * frameCount.coerceAtLeast(1)

    fun hasRoomForStill(availableBytes: Long?, frameCount: Int = 1): Boolean {
        if (availableBytes == null) return true
        return availableBytes >= requiredBytes(frameCount)
    }

    fun hasRoomForIntervalometer(availableBytes: Long?): Boolean =
        hasRoomForStill(availableBytes, INTERVALOMETER_MIN_FRAMES)

    fun isFewStillsWarning(availableBytes: Long?, frameCount: Int = 1): Boolean {
        if (availableBytes == null) return false
        val need = requiredBytes(frameCount)
        return availableBytes < requiredBytes(frameCount * FEW_STILLS_FRAMES) && availableBytes >= need
    }

    fun holdBurstFrameBudget(writesRawAndJpeg: Boolean): Int = if (writesRawAndJpeg) 2 else 1

    fun remainingShots(availableBytes: Long?, frameCount: Int = 1): Int? {
        if (availableBytes == null) return null
        val need = requiredBytes(frameCount)
        if (need <= 0L) return null
        return (availableBytes / need).toInt()
    }

    fun plannedFrameCount(
        hdrStill: Boolean,
        hdrShotCount: Int,
        burstEnabled: Boolean,
        burstCount: Int,
        bracketEnabled: Boolean = false,
        bracketCount: Int = 1,
        nightScapeEnabled: Boolean = false,
        nightScapeCount: Int = 1,
    ): Int =
        when {
            nightScapeEnabled -> nightScapeCount.coerceAtLeast(1)
            hdrStill -> hdrShotCount.coerceAtLeast(1)
            burstEnabled -> burstCount.coerceAtLeast(1)
            bracketEnabled -> bracketCount.coerceAtLeast(1)
            else -> 1
        }

    fun intervalometerTickFrames(
        videoMode: Boolean,
        nightScapeEnabled: Boolean,
        nightScapeCount: Int,
        hdrStill: Boolean,
        hdrShotCount: Int,
        burstEnabled: Boolean,
        burstCount: Int,
        bracketEnabled: Boolean,
        bracketCount: Int,
    ): Int {
        if (videoMode) return 1
        return plannedFrameCount(
            hdrStill = hdrStill,
            hdrShotCount = hdrShotCount,
            burstEnabled = burstEnabled,
            burstCount = burstCount,
            bracketEnabled = bracketEnabled,
            bracketCount = bracketCount,
            nightScapeEnabled = nightScapeEnabled,
            nightScapeCount = nightScapeCount,
        )
    }
}
