@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor

/** Optional corner byline on JPEG pixels only (never DNG). */
object JpegOnImageCredit {
    fun draw(bitmap: Bitmap, credit: String): Bitmap {
        val text = credit.trim()
        if (text.isEmpty()) return bitmap
        val out = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = (out.width / 42).toFloat().coerceIn(18f, 48f)
                setShadowLayer(4f, 0f, 1f, AndroidColor.BLACK)
            }
        val x = 16f
        val y = out.height - 16f
        canvas.drawText(text, x, y, paint)
        return out
    }
}
