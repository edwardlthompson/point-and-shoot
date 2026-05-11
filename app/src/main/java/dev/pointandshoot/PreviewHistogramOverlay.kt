package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.max as kotlinMax

/**
 * Small luma histogram (256 bins) over the preview when [bins] is non-null and sized per
 * [PreviewLumaHistogram.BIN_COUNT].
 */
@Composable
fun PreviewHistogramOverlay(
    bins: IntArray?,
    modifier: Modifier = Modifier,
) {
    if (bins == null || bins.size != PreviewLumaHistogram.BIN_COUNT) return
    val maxCount = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val barW = kotlinMax(w / PreviewLumaHistogram.BIN_COUNT, 1f)
        for (i in 0 until PreviewLumaHistogram.BIN_COUNT) {
            val c = bins[i]
            if (c <= 0) continue
            val bh = (c.toFloat() / maxCount.toFloat()) * h * 0.88f
            drawRect(
                color = Color.White.copy(alpha = 0.58f),
                topLeft = Offset(i * barW, h - bh),
                size = Size(barW.coerceAtLeast(1f), bh),
            )
        }
    }
}
