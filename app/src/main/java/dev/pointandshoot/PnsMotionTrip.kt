@file:Suppress("MagicNumber")

package dev.pointandshoot

/** Arm / wait / fire when luma moves enough. */
object PnsMotionTrip {
    const val DEFAULT_DELTA: Float = 8f

    fun shouldFire(previousMean: Float?, currentMean: Float, threshold: Float = DEFAULT_DELTA): Boolean {
        if (previousMean == null) return false
        return kotlin.math.abs(currentMean - previousMean) >= threshold
    }

    fun lumaMean(yPlane: ByteArray, sampleStride: Int = 16): Float {
        if (yPlane.isEmpty()) return 0f
        var sum = 0L
        var n = 0
        var i = 0
        while (i < yPlane.size) {
            sum += yPlane[i].toInt() and 0xff
            n++
            i += sampleStride.coerceAtLeast(1)
        }
        return if (n == 0) 0f else sum.toFloat() / n.toFloat()
    }
}
