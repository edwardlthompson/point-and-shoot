package dev.pointandshoot

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * Renders the current GLES composite into a [MediaCodec] input [Surface].
 *
 * Preview [GLSurfaceView] contexts are often **not** [EGL_RECORDABLE_ANDROID]; we create a
 * **shared** recordable EGLContext for the encoder window surface (same display as preview).
 */
class DualVideoGlEncoderSink {
    /** [EGL_OPENGL_ES3_BIT_KHR] — preview [GLSurfaceView] uses ES 3. */
    private companion object {
        const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040
    }

    @Volatile
    var isEncoderSurfaceReady: Boolean = false
        private set

    private var eglRecordSurface: EGLSurface? = null
    private var width: Int = 0
    private var height: Int = 0
    private var savedDraw: EGLSurface? = null
    private var savedRead: EGLSurface? = null
    private var savedContext: EGLContext? = null
    private var previewContext: EGLContext? = null
    private var recordContext: EGLContext? = null
    private var recordConfig: EGLConfig? = null
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
        previewContext = context
        val recCtx = ensureRecordContext(display, context) ?: return
        val config = recordConfig ?: return
        val eglSurf =
            EGL14.eglCreateWindowSurface(
                display,
                config,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
        if (eglSurf == null || eglSurf == EGL14.EGL_NO_SURFACE) {
            val err = EGL14.eglGetError()
            isEncoderSurfaceReady = false
            Log.e(DualVideoRecordingController.TAG, "dualEncoderSink: eglCreateWindowSurface failed err=$err")
            return
        }
        eglRecordSurface = eglSurf
        isEncoderSurfaceReady = true
        frameIndex = 0L
        Log.i(
            DualVideoRecordingController.TAG,
            "dualEncoderSink ready ${width}x$height recordable=true sharedCtx=${recCtx != context}",
        )
    }

    /**
     * @param draw invoked with encoder width/height while the recordable EGL surface is current.
     */
    fun recordFrame(draw: (recordW: Int, recordH: Int) -> Unit): Boolean {
        val recSurf = eglRecordSurface ?: return false
        val recCtx = recordContext ?: previewContext ?: return false
        val display = EGL14.eglGetCurrentDisplay()
        if (display == EGL14.EGL_NO_DISPLAY) return false
        savedDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        savedRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        savedContext = EGL14.eglGetCurrentContext()
        if (!EGL14.eglMakeCurrent(display, recSurf, recSurf, recCtx)) {
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
        val restoreCtx = savedContext ?: previewContext
        if (restoreCtx != null) {
            EGL14.eglMakeCurrent(display, savedDraw, savedRead, restoreCtx)
        }
        savedDraw = null
        savedRead = null
        savedContext = null
        frameIndex++
        return swapped
    }

    fun release() {
        releaseEglSurface()
        releaseRecordContext()
        width = 0
        height = 0
        frameIndex = 0L
        previewContext = null
        isEncoderSurfaceReady = false
    }

    private fun releaseEglSurface() {
        val display = EGL14.eglGetCurrentDisplay()
        val surf = eglRecordSurface
        if (surf != null && surf != EGL14.EGL_NO_SURFACE && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(display, surf)
        }
        eglRecordSurface = null
        isEncoderSurfaceReady = false
    }

    private fun releaseRecordContext() {
        val display = EGL14.eglGetCurrentDisplay()
        val ctx = recordContext
        if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroyContext(display, ctx)
        }
        recordContext = null
        recordConfig = null
    }

    private fun ensureRecordContext(display: EGLDisplay, shareContext: EGLContext): EGLContext? {
        recordContext?.let { return it }
        val config = queryRecordableConfig(display) ?: run {
            Log.w(DualVideoRecordingController.TAG, "dualEncoderSink: no recordable EGL config")
            return null
        }
        val attribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val ctx =
            EGL14.eglCreateContext(display, config, shareContext, attribs, 0)
                ?: EGL14.EGL_NO_CONTEXT
        if (ctx == EGL14.EGL_NO_CONTEXT) {
            Log.e(
                DualVideoRecordingController.TAG,
                "dualEncoderSink: eglCreateContext failed err=${EGL14.eglGetError()}",
            )
            return null
        }
        recordContext = ctx
        recordConfig = config
        return ctx
    }

    /** Window surface for [MediaCodec] must use a recordable config (preview GLSurfaceView config often is not). */
    private fun queryRecordableConfig(display: EGLDisplay): EGLConfig? {
        val attribs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES3_BIT_KHR,
                EGLExt.EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(8)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, configs.size, num, 0)) {
            return null
        }
        return if (num[0] > 0) configs[0] else null
    }

    private fun GLES20Clear() {
        android.opengl.GLES20.glViewport(0, 0, width, height)
        android.opengl.GLES20.glClearColor(0f, 0f, 0f, 1f)
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
    }
}
