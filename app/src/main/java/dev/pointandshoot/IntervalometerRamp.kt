package dev.pointandshoot

/** Linear ISO / shutter ramp across intervalometer ticks. */
object IntervalometerRamp {
    fun step(start: Int, end: Int, index: Int, total: Int): Int {
        if (total <= 1) return start
        val t = index.coerceIn(0, total - 1).toFloat() / (total - 1).toFloat()
        return (start + (end - start) * t).toInt()
    }

    fun shutterNs(startNs: Long, endNs: Long, index: Int, total: Int): Long {
        if (total <= 1L) return startNs
        val t = index.coerceIn(0, total - 1).toDouble() / (total - 1).toDouble()
        return (startNs + (endNs - startNs) * t).toLong()
    }
}
