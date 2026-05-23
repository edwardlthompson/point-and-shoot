package dev.pointandshoot

import android.graphics.Matrix

/** Texture-coordinate rotation for packed YUV monitor frames (no SurfaceTexture matrix). */
internal object HfrYuvTexCoord {
    fun rotatedFullScreenQuad(degrees: Int): FloatArray {
        val normalized = ((degrees % 360) + 360) % 360
        val out = LutShaderProgram.Source.FULL_SCREEN_QUAD.clone()
        val m = Matrix()
        if (normalized != 0) {
            m.setRotate(normalized.toFloat(), 0.5f, 0.5f)
        }
        // Packed YUV lacks SurfaceTexture matrix. After rotation, mirror V (not U) so the
        // on-screen correction is horizontal, matching the main rear finder.
        m.postScale(1f, -1f, 0.5f, 0.5f)
        for (i in 0 until 4) {
            val uv = floatArrayOf(out[i * 4 + 2], out[i * 4 + 3])
            m.mapPoints(uv)
            out[i * 4 + 2] = uv[0]
            out[i * 4 + 3] = uv[1]
        }
        return out
    }

    fun rotatedCroppedFullScreenQuad(degrees: Int, crop: HfrMonitorTextureCrop): FloatArray {
        val out = rotatedFullScreenQuad(degrees)
        if (crop.u0 == 0f && crop.v0 == 0f && crop.u1 == 1f && crop.v1 == 1f) return out
        for (i in 0 until 4) {
            val u = out[i * 4 + 2]
            val v = out[i * 4 + 3]
            out[i * 4 + 2] = crop.u0 + u * (crop.u1 - crop.u0)
            out[i * 4 + 3] = crop.v0 + v * (crop.v1 - crop.v0)
        }
        return out
    }
}
