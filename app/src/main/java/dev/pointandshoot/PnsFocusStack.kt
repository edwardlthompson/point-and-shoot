@file:Suppress("MagicNumber")

package dev.pointandshoot

/** N focus distances from near to far (diopter-linear). */
object PnsFocusStack {
    fun distancesDiopter(nearM: Float, farM: Float, count: Int): List<Float> {
        val n = count.coerceIn(2, 12)
        val nearD = 1f / nearM.coerceAtLeast(0.05f)
        val farD = 1f / farM.coerceAtLeast(nearM + 0.01f)
        return (0 until n).map { i ->
            val t = i.toFloat() / (n - 1).toFloat()
            val diopter = nearD + (farD - nearD) * t
            1f / diopter
        }
    }
}
