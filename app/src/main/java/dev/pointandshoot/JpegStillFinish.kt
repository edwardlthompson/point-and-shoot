package dev.pointandshoot

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** JPEG-only finish: optional on-image credit + LUT bake. Never opens DNG. */
object JpegStillFinish {
    fun apply(
        context: Context,
        bitmap: Bitmap,
        @Suppress("UnusedParameter") displayName: String,
        lut: Lut3D?,
    ): Bitmap {
        var working = bitmap
        if (lut != null && !lut.isIdentity()) {
            val baked = LogLutBakeExport.applyLut(working, lut)
            if (baked !== working && working !== bitmap) working.recycle()
            working = baked
        }
        if (PnsProductPrefs.jpegCreditEnabled(context) || PnsJpegCreditPrefs(context).artist().isNotBlank()) {
            val prefs = PnsJpegCreditPrefs(context)
            val credit =
                listOf(prefs.artist(), prefs.copyright())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "Point & Shoot" }
            val credited = JpegOnImageCredit.draw(working, credit)
            if (credited !== working && working !== bitmap) working.recycle()
            working = credited
        }
        return working
    }

    fun isJpegName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
    }

    fun refuseDng(uri: Uri): Boolean = uri.toString().lowercase().endsWith(".dng")
}
