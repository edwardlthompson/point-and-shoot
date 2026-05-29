package dev.pointandshoot

/**
 * Sprint **15.22** — eye-region AE weighting inside a face track box (buffer coordinates).
 */
object FacePriorityMetering {
    const val TAG = "PNS.FaceMeter"

    data class BufferRectF(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Sub-region for AE: prefer eye landmark cluster; else upper ~38% of face box.
     */
    fun eyeSubRegionInFace(
        face: BufferRectF,
        eyePositionsBuffer: List<Pair<Float, Float>>,
    ): BufferRectF {
        val inFace =
            eyePositionsBuffer.filter { (x, y) ->
                x >= face.left && x <= face.right && y >= face.top && y <= face.bottom
            }
        if (inFace.isNotEmpty()) {
            var l = inFace.minOf { it.first }
            var r = inFace.maxOf { it.first }
            var t = inFace.minOf { it.second }
            var b = inFace.maxOf { it.second }
            val padX = (face.width * 0.12f).coerceAtLeast(8f)
            val padY = (face.height * 0.10f).coerceAtLeast(8f)
            l = (l - padX).coerceAtLeast(face.left)
            r = (r + padX).coerceAtMost(face.right)
            t = (t - padY).coerceAtLeast(face.top)
            b = (b + padY).coerceAtMost(face.bottom)
            if (r > l && b > t) {
                return BufferRectF(l, t, r, b)
            }
        }
        val bandH = face.height * 0.38f
        return BufferRectF(face.left, face.top, face.right, face.top + bandH)
    }
}
