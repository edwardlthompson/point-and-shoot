package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.max as kotlinMax

/**
 * Small histogram (256 bins) over the preview.
 *
 * - When [rgbBins] is null: renders [bins] as a white luma waveform (original behaviour).
 * - Sprint 13.9: When [rgbBins] is non-null, renders three overlapping colour channels
 *   (R red, G green, B blue) at reduced alpha so they blend naturally.
 *   [bins] luma waveform is still drawn underneath as a dim white reference.
 */
@Composable
fun PreviewHistogramOverlay(
    bins: IntArray?,
    modifier: Modifier = Modifier,
    rgbBins: PreviewLumaHistogram.RgbHistogramBins? = null,
) {
    if (bins == null || bins.size != PreviewLumaHistogram.BIN_COUNT) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val barW = kotlinMax(w / PreviewLumaHistogram.BIN_COUNT, 1f)

        if (rgbBins != null) {
            // RGB mode: find a shared normalisation max across all three channels so relative
            // channel weights are preserved, then draw luma as a dim reference underneath.
            val lumaMax = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
            val rgbMax = kotlinMax(
                kotlinMax(rgbBins.r.maxOrNull() ?: 0, rgbBins.g.maxOrNull() ?: 0),
                rgbBins.b.maxOrNull() ?: 0,
            ).coerceAtLeast(1)

            // Dim luma reference
            for (i in 0 until PreviewLumaHistogram.BIN_COUNT) {
                val c = bins[i]
                if (c <= 0) continue
                val bh = (c.toFloat() / lumaMax) * h * 0.88f
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(i * barW, h - bh),
                    size = Size(barW.coerceAtLeast(1f), bh),
                )
            }
            // R channel
            for (i in 0 until PreviewLumaHistogram.BIN_COUNT) {
                val c = rgbBins.r[i]
                if (c <= 0) continue
                val bh = (c.toFloat() / rgbMax) * h * 0.88f
                drawRect(
                    color = Color(1f, 0.18f, 0.18f, 0.72f),
                    topLeft = Offset(i * barW, h - bh),
                    size = Size(barW.coerceAtLeast(1f), bh),
                )
            }
            // G channel
            for (i in 0 until PreviewLumaHistogram.BIN_COUNT) {
                val c = rgbBins.g[i]
                if (c <= 0) continue
                val bh = (c.toFloat() / rgbMax) * h * 0.88f
                drawRect(
                    color = Color(0.18f, 1f, 0.35f, 0.60f),
                    topLeft = Offset(i * barW, h - bh),
                    size = Size(barW.coerceAtLeast(1f), bh),
                )
            }
            // B channel
            for (i in 0 until PreviewLumaHistogram.BIN_COUNT) {
                val c = rgbBins.b[i]
                if (c <= 0) continue
                val bh = (c.toFloat() / rgbMax) * h * 0.88f
                drawRect(
                    color = Color(0.18f, 0.55f, 1f, 0.72f),
                    topLeft = Offset(i * barW, h - bh),
                    size = Size(barW.coerceAtLeast(1f), bh),
                )
            }
        } else {
            // Luma-only mode (original behaviour)
            val maxCount = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
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
}
