package dev.pointandshoot

/**
 * Sprint **13V.13** — minutes of video remaining at the current encode bitrate (JVM-testable).
 */
object PreviewVideoStorageEstimate {
    /** Warn when fewer than this many minutes remain at the estimated record rate. */
    const val LOW_STORAGE_WARNING_MINUTES = 5.0

    /** Refuse a new record start when remaining time is below this. */
    const val MIN_RECORD_START_MINUTES = 1.0

    /** Typical AAC stereo camcorder overhead (bits per second). */
    const val ESTIMATED_AUDIO_BITS_PER_SEC = 128_000

    /** Mux / container headroom on encoded MP4. */
    const val ENCODED_CONTAINER_FACTOR = 1.05

    data class Session(
        val encodeWidth: Int,
        val encodeHeight: Int,
        val targetFps: Int,
        val rawVideoLane: Boolean,
        val enableResearchDcgHdr: Boolean,
        val adbPreviewVideoDcg: Boolean = false,
        val adbPreviewVideoTenBit: Boolean = false,
    )

    data class Result(
        val bytesPerSecond: Long,
        val minutesRemaining: Double?,
        val lowStorageWarning: Boolean,
        val bitrateBps: Int,
        val rawLane: Boolean,
    )

    fun estimate(session: Session): Result {
        val w = session.encodeWidth.coerceAtLeast(1)
        val h = session.encodeHeight.coerceAtLeast(1)
        val fps = session.targetFps.coerceAtLeast(1)
        val bytesPerSecond =
            if (session.rawVideoLane) {
                rawBytesPerSecond(w, h, fps)
            } else {
                val codec = pickCodec(session)
                val bitrate = VideoFormatPresets.calculateBitrate(w, h, fps, codec)
                encodedBytesPerSecond(bitrate)
            }
        return Result(
            bytesPerSecond = bytesPerSecond,
            minutesRemaining = null,
            lowStorageWarning = false,
            bitrateBps =
                if (session.rawVideoLane) {
                    0
                } else {
                    VideoFormatPresets.calculateBitrate(
                        w,
                        h,
                        fps,
                        pickCodec(session),
                    )
                },
            rawLane = session.rawVideoLane,
        )
    }

    fun withAvailableBytes(estimate: Result, availableBytes: Long?): Result {
        val minutes = minutesRemaining(availableBytes, estimate.bytesPerSecond)
        return estimate.copy(
            minutesRemaining = minutes,
            lowStorageWarning = isLowStorageWarning(minutes),
        )
    }

    fun minutesRemaining(availableBytes: Long?, bytesPerSecond: Long): Double? {
        if (availableBytes == null || bytesPerSecond <= 0L) return null
        if (availableBytes <= 0L) return 0.0
        return availableBytes.toDouble() / bytesPerSecond.toDouble() / 60.0
    }

    fun isLowStorageWarning(minutes: Double?): Boolean =
        minutes != null && minutes < LOW_STORAGE_WARNING_MINUTES

    fun shouldRefuseRecordStart(minutes: Double?): Boolean =
        minutes != null && minutes < MIN_RECORD_START_MINUTES

    fun shouldStopRecordForEmpty(minutes: Double?): Boolean =
        minutes != null && minutes <= 0.0

    fun encodedBytesPerSecond(videoBitrateBps: Int): Long {
        val totalBps = videoBitrateBps + ESTIMATED_AUDIO_BITS_PER_SEC
        return (totalBps / 8.0 * ENCODED_CONTAINER_FACTOR).toLong().coerceAtLeast(1L)
    }

    /** 16-bit RAW plane payload + small per-frame container header. */
    fun rawBytesPerSecond(width: Int, height: Int, fps: Int): Long {
        val framePayload = width.toLong() * height * 2L
        val frameOverhead = 16L
        return (framePayload + frameOverhead) * fps.coerceAtLeast(1)
    }

    fun pickCodec(session: Session): VideoCodec {
        val wantDcg =
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = session.enableResearchDcgHdr,
                adbPreviewVideoDcg = session.adbPreviewVideoDcg,
            )
        return when {
            wantDcg -> VideoCodec.DCG
            session.adbPreviewVideoTenBit -> VideoCodec.H265_10BIT
            else -> VideoCodec.H265
        }
    }

    fun formatMinutesRemaining(minutes: Double?): String =
        when {
            minutes == null -> "—"
            minutes < 1.0 -> "<1 min"
            minutes < 60.0 -> "${minutes.toInt()} min"
            else -> {
                val hrs = (minutes / 60.0).toInt()
                val mins = (minutes % 60.0).toInt()
                if (mins == 0) "${hrs}h" else "${hrs}h ${mins}m"
            }
        }
}
