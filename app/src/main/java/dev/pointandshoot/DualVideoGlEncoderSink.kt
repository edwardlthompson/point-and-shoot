package dev.pointandshoot

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * Renders the current GLES composite into a [MediaCodec] input [Surface] using the **same**
 * EGL context as [LutCameraPreviewRenderer] ([EGL_RECORDABLE_ANDROID] window surface).
 */
class DualVideoGlEncoderSink {
    private var eglRecordSurface: EGLSurface? = null
    private var width: Int = 0
    private var height: Int = 0
    private var savedDraw: EGLSurface? = null
    private var savedRead: EGLSurface? = null
    private var frameIndex: Long = 0L

    fun setEncoderTarget(surface: Surface?, w: Int, h: Int) {
        releaseEglSurface()
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        if (surface == null || !surface.isValid) return
        val display = EGL14.eglGetCurrentDisplay()
        val context = EGL14.eglGetCurrentContext()
        if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT) {
            Log.w(DualVideoRecordingController.TAG, "dualEncoderSink: no current EGL context")
            return
        }
        val config = queryConfig(display, context) ?: run {
            Log.w(DualVideoRecordingController.TAG, "dualEncoderSink: no EGL config")
            return
        }
        val attribs =
            intArrayOf(
                EGLExt.EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE,
            )
        val eglSurf = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        if (eglSurf == null || eglSurf == EGL14.EGL_NO_SURFACE) {
            val err = EGL14.eglGetError()
            Log.e(DualVideoRecordingController.TAG, "dualEncoderSink: eglCreateWindowSurface failed err=$err")
            return
        }
        eglRecordSurface = eglSurf
        frameIndex = 0L
        Log.i(
            DualVideoRecordingController.TAG,
            "dualEncoderSink ready ${width}x$height recordable=true",
        )
    }

    /**
     * @param draw invoked with encoder width/height while the recordable EGL surface is current.
     */
    fun recordFrame(draw: (recordW: Int, recordH: Int) -> Unit): Boolean {
        val recSurf = eglRecordSurface ?: return false
        val display = EGL14.eglGetCurrentDisplay()
        val context = EGL14.eglGetCurrentContext()
        if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT) return false
        savedDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        savedRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        if (!EGL14.eglMakeCurrent(display, recSurf, recSurf, context)) {
            Log.w(DualVideoRecordingController.TAG, "dualEncoderSink: eglMakeCurrent(record) failed")
            return false
        }
        GLES20Clear()
        draw(width, height)
        val ptsNs = System.nanoTime()
        EGLExt.eglPresentationTimeANDROID(display, recSurf, ptsNs)
        val swapped = EGL14.eglSwapBuffers(display, recSurf)
        if (!swapped) {
            Log.w(DualVideoRecordingController.TAG, "dualEncoderSink: eglSwapBuffers failed")
        }
        EGL14.eglMakeCurrent(display, savedDraw, savedRead, context)
        savedDraw = null
        savedRead = null
        frameIndex++
        return swapped
    }

    fun release() {
        releaseEglSurface()
        width = 0
        height = 0
        frameIndex = 0L
    }

    private fun releaseEglSurface() {
        val display = EGL14.eglGetCurrentDisplay()
        val surf = eglRecordSurface
        if (surf != null && surf != EGL14.EGL_NO_SURFACE && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(display, surf)
        }
        eglRecordSurface = null
    }

    private fun queryConfig(
        display: android.opengl.EGLDisplay,
        context: android.opengl.EGLContext,
    ): EGLConfig? {
        val num = IntArray(1)
        EGL14.eglQueryContext(display, context, EGL14.EGL_CONFIG_ID, num, 0)
        val configs = arrayOfNulls<EGLConfig>(1)
        val attribs = intArrayOf(EGL14.EGL_CONFIG_ID, num[0], EGL14.EGL_NONE)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        return configs[0]
    }

    private fun GLES20Clear() {
        android.opengl.GLES20.glViewport(0, 0, width, height)
        android.opengl.GLES20.glClearColor(0f, 0f, 0f, 1f)
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
    }
}
