package dev.pointandshoot

/**
 * Host-side helper for Sprint **13.12** storage UI — estimates bytes/sec from WxH × bpp × fps.
 */
object VideoStorageEstimate {
    fun bytesPerSecond(
        width: Int,
        height: Int,
        bytesPerPixel: Int,
        fps: Int,
    ): Long {
        if (width <= 0 || height <= 0 || bytesPerPixel <= 0 || fps <= 0) return 0L
        return width.toLong() * height * bytesPerPixel * fps
    }

    fun formatMegabytesPerMinute(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "—"
        val mbPerMin = bytesPerSec * 60.0 / (1024.0 * 1024.0)
        return String.format("%.1f MB/min", mbPerMin)
    }
}
