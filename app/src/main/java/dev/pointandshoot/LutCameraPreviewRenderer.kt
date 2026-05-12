package dev.pointandshoot

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders Camera2 preview from [GL_TEXTURE_EXTERNAL_OES] through [Lut3D].
 * Owns the [SurfaceTexture] passed to [PreviewController.onSurfaceTextureAvailable].
 */
class LutCameraPreviewRenderer(
    private val assetLoader: (String) -> String,
    private val mainHandler: Handler,
    /** Per-frame RGB multipliers for readout WB (length 3; not mutated by the renderer). */
    private val readoutWbRgb: () -> FloatArray = { floatArrayOf(1f, 1f, 1f) },
    /** Focus-peaking uniforms for `lut_preview_external.frag.glsl` (read each draw frame). */
    private val focusPeakingUniforms: () -> FocusPeakingGlUniforms =
        { FocusPeakingGlUniforms(false, 0f, 0f, 0f, 0f) },
    private val onSurfaceTextureAvailable: (SurfaceTexture, Int, Int) -> Unit,
    private val onSurfaceTextureSizeChanged: (Int, Int) -> Unit,
    private val onSurfaceTextureDestroyed: (SurfaceTexture) -> Unit,
    private val onPreviewFramePresented: () -> Unit,
) : GLSurfaceView.Renderer {

    private var program: LutExternalOesShaderProgram? = null
    private var quadBuffer: FloatBuffer? = null
    private var oesTextureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surfacePosted: Boolean = false
    private var lutTextureId: Int = 0
    private var lutTextureSize: Int = 0

    private val stMatrix = FloatArray(16)
    private val pendingLut: AtomicReference<LutUpdate?> = AtomicReference(null)
    private val activeLut: AtomicReference<Lut3D?> = AtomicReference(null)
    /** Latest LUT from [setLut]; replayed after EGL context loss so preview survives pause/resume. */
    private val lastLutRequested: AtomicReference<Lut3D?> = AtomicReference(null)
    private val geometry = AtomicReference(Geometry(1, 1, 0, 0, coverCrop = true))

    private var glSurfaceViewRef: GLSurfaceView? = null

    fun attachView(view: GLSurfaceView) {
        glSurfaceViewRef = view
    }

    fun detachView() {
        glSurfaceViewRef = null
    }

    fun surfaceTextureOrNull(): SurfaceTexture? = surfaceTexture

    fun setGeometry(viewW: Int, viewH: Int, bufferW: Int, bufferH: Int, coverCrop: Boolean) {
        geometry.set(
            Geometry(
                viewW.coerceAtLeast(1),
                viewH.coerceAtLeast(1),
                bufferW.coerceAtLeast(0),
                bufferH.coerceAtLeast(0),
                coverCrop,
            ),
        )
    }

    fun setLut(lut: Lut3D?) {
        lastLutRequested.set(lut)
        pendingLut.set(LutUpdate(lut))
    }

    /**
     * Run on the GL thread while the EGL context is still current (e.g. inside
     * [GLSurfaceView.queueEvent] before [GLSurfaceView.onPause]).
     */
    fun releaseGlThread() {
        val st = surfaceTexture
        surfaceTexture = null
        surfacePosted = false
        if (st != null) {
            runCatching { st.setOnFrameAvailableListener(null) }
            runCatching { st.release() }
        }
        if (oesTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = 0
        }
        if (lutTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
            lutTextureSize = 0
        }
        activeLut.set(null)
        program?.release()
        program = null
        quadBuffer = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGlThread()

        val vert = assetLoader(LutExternalOesShaderProgram.Source.VERTEX_ASSET_PATH)
        val frag = assetLoader(LutExternalOesShaderProgram.Source.FRAGMENT_ASSET_PATH)
        program = LutExternalOesShaderProgram.create(vert, frag)
        quadBuffer =
            ByteBuffer.allocateDirect(LutShaderProgram.Source.FULL_SCREEN_QUAD.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(LutShaderProgram.Source.FULL_SCREEN_QUAD)
                    position(0)
                }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        check(oesTextureId != 0) { "glGenTextures(OES) returned 0" }
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )

        val st = SurfaceTexture(oesTextureId)
        surfaceTexture = st
        st.setOnFrameAvailableListener {
            glSurfaceViewRef?.requestRender()
        }
        // Compose often does not re-run LUT [LaunchedEffect] after EGL recreate; replay last request.
        pendingLut.set(LutUpdate(lastLutRequested.get()))
        glSurfaceViewRef?.requestRender()
        Log.i(TAG, "GLES external-OES preview program ready")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val st = surfaceTexture ?: return
        if (!surfacePosted) {
            surfacePosted = true
            mainHandler.post {
                onSurfaceTextureAvailable(st, width, height)
            }
        } else {
            mainHandler.post {
                onSurfaceTextureSizeChanged(width, height)
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val prog = program ?: return
        val st = surfaceTexture ?: return
        consumePendingLut()
        runCatching { st.updateTexImage() }
        st.getTransformMatrix(stMatrix)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val g = geometry.get()
        val lut = activeLut.get()
        val wb = readoutWbRgb()
        prog.bindPerFrameUniforms(
            stMatrix16 = stMatrix,
            viewW = g.viewW,
            viewH = g.viewH,
            bufW = g.bufferW,
            bufH = g.bufferH,
            coverCrop = g.coverCrop,
            lut = lut,
            readoutWbRgb = wb,
            focusPeaking = focusPeakingUniforms(),
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        if (lutTextureId != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        }

        val buf = quadBuffer ?: return
        buf.position(0)
        GLES20.glEnableVertexAttribArray(prog.attribPosition)
        GLES20.glVertexAttribPointer(prog.attribPosition, 2, GLES20.GL_FLOAT, false, 16, buf)
        buf.position(2)
        GLES20.glEnableVertexAttribArray(prog.attribTexCoord)
        GLES20.glVertexAttribPointer(prog.attribTexCoord, 2, GLES20.GL_FLOAT, false, 16, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(prog.attribPosition)
        GLES20.glDisableVertexAttribArray(prog.attribTexCoord)

        onPreviewFramePresented()
    }

    private fun consumePendingLut() {
        val update = pendingLut.getAndSet(null) ?: return
        val newLut = update.lut
        if (newLut == null || newLut.isIdentity()) {
            if (lutTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
                lutTextureId = 0
                lutTextureSize = 0
            }
            activeLut.set(null)
            return
        }
        if (lutTextureId != 0 && newLut.size != lutTextureSize) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
        }
        if (lutTextureId == 0) {
            lutTextureId = LutShaderProgram.uploadLutTexture(newLut)
            lutTextureSize = newLut.size
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
            val pixels =
                ByteBuffer.allocateDirect(newLut.samples.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(newLut.samples)
                        position(0)
                    }
            GLES30.glTexSubImage3D(
                GLES30.GL_TEXTURE_3D,
                0,
                0,
                0,
                0,
                newLut.size,
                newLut.size,
                newLut.size,
                GLES20.GL_RGB,
                GLES30.GL_FLOAT,
                pixels,
            )
        }
        activeLut.set(newLut)
    }

    fun notifyHostSurfaceTextureDestroyed() {
        val st = surfaceTexture
        if (st != null) {
            mainHandler.post {
                onSurfaceTextureDestroyed(st)
            }
        }
    }

    private data class LutUpdate(val lut: Lut3D?)

    private data class Geometry(
        val viewW: Int,
        val viewH: Int,
        val bufferW: Int,
        val bufferH: Int,
        val coverCrop: Boolean,
    )

    companion object {
        private const val TAG = "PNS.GLES.Preview"
    }
}
