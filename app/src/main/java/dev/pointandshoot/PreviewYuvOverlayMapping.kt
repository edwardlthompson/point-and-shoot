package dev.pointandshoot

/** Maps YUV analysis pixel → preview buffer → view (shared by zebra / false-color overlays). */
internal fun yuvCornerToView(
    yuvX: Float,
    yuvY: Float,
    yuvWidth: Int,
    yuvHeight: Int,
    bufferWidthPx: Int,
    bufferHeightPx: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
    coverCrop: Boolean,
    mirrorHorizontally: Boolean,
): Pair<Float, Float> {
    val (bx, by) =
        TexturePreviewFit.mapYuvPixelToBufferPixel(
            yuvX,
            yuvY,
            yuvWidth,
            yuvHeight,
            bufferWidthPx,
            bufferHeightPx,
            coverCrop,
        )
    var (vx, vy) =
        TexturePreviewFit.mapBufferToView(
            bx,
            by,
            viewWidthPx,
            viewHeightPx,
            bufferWidthPx,
            bufferHeightPx,
            coverCrop,
        )
    if (mirrorHorizontally) {
        vx = viewWidthPx.toFloat() - vx
    }
    return vx to vy
}
