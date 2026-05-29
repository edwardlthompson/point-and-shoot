package dev.pointandshoot

/**
 * Coarse false-color grid over the YUV analysis frame (Sprint **15.21**).
 *
 * @param cellArgb row-major ARGB tint per cell; **0** = no tint (normal exposure band).
 */
data class FalseColorFrame(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cellSizePx: Int,
    val cols: Int,
    val rows: Int,
    val cellArgb: IntArray,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(cellSizePx > 0)
        require(cols > 0 && rows > 0)
        require(cellArgb.size == cols * rows)
    }
}
