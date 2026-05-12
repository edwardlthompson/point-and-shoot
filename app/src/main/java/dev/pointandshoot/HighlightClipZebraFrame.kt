package dev.pointandshoot

/**
 * Coarse grid over the **YUV analysis** frame: cells where any luma exceeded the near-clip
 * threshold (spec: ~0.95 of full scale → bin ≥ 242 on 8-bit Y).
 */
data class HighlightClipZebraFrame(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cellSizePx: Int,
    val cols: Int,
    val rows: Int,
    /** Row-major: index `row * cols + col`; true = draw zebra hatch in that cell. */
    val nearClip: BooleanArray,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(cellSizePx > 0)
        require(cols > 0 && rows > 0)
        require(nearClip.size == cols * rows)
    }
}
