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
    private val dualSplitEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val recordCompositeToEncoder = java.util.concurrent.atomic.AtomicBoolean(false)
    private val encoderSinkRef = AtomicReference<DualVideoGlEncoderSink?>(null)
    private var frontOesTextureId: Int = 0
    private var frontSurfaceTexture: SurfaceTexture? = null
    private val frontStMatrix = FloatArray(16)
    private val onDualFrontReady =
        AtomicReference<((SurfaceTexture, Int, Int) -> Unit)?>(null)
    private val frontBufferSize = AtomicReference(intArrayOf(0, 0))
    private var recordDrawCounter: Int = 0
    @Volatile private var frontTextureInitialized: Boolean = false
    @Volatile private var frontFrameCount: Int = 0
    private val maxFrontFrameErrors: Int = 10

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

    fun setFrontBufferSize(bufferW: Int, bufferH: Int) {
        frontBufferSize.set(intArrayOf(bufferW.coerceAtLeast(0), bufferH.coerceAtLeast(0)))
    }

    /** Stacked rear (top) + front (bottom) for Sprint **14.12** dual video (LG dual-recording heritage). */
    fun setDualSplitEnabled(
        enabled: Boolean,
        onFrontSurfaceReady: ((SurfaceTexture, Int, Int) -> Unit)?,
    ) {
        dualSplitEnabled.set(enabled)
        onDualFrontReady.set(onFrontSurfaceReady)
        glSurfaceViewRef?.queueEvent {
            if (enabled) {
                ensureFrontOesTextureOnGlThread()
            } else {
                releaseFrontOesOnGlThread()
            }
        }
    }

    fun setEncoderCompositeSink(sink: DualVideoGlEncoderSink?, record: Boolean) {
        encoderSinkRef.set(sink)
        recordCompositeToEncoder.set(record && sink != null)
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
        releaseFrontOesOnGlThread()
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
        val dual = dualSplitEnabled.get()
        if (dual) {
            val fst = frontSurfaceTexture
            var frontValid = false
            if (fst != null && frontTextureInitialized) {
                try {
                    fst.updateTexImage()
                    fst.getTransformMatrix(frontStMatrix)
                    frontValid = true
                    frontFrameCount++
                    // Reset error count on successful frame
                    if (frontFrameCount > 0) {
                        frontFrameCount = 0
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Front texture update failed: ${e.message}")
                    frontFrameCount++
                    if (frontFrameCount > maxFrontFrameErrors) {
                        Log.e(TAG, "Too many front texture errors, marking as unhealthy")
                        frontTextureInitialized = false
                    }
                }
            }
            drawStackedComposite(prog, g, frontReady = dual && frontValid)
        } else {
            drawOesToViewport(
                prog = prog,
                g = g,
                oesId = oesTextureId,
                matrix = stMatrix,
                viewX = 0,
                viewY = 0,
                viewW = g.viewW,
                viewH = g.viewH,
                applyLut = true,
            )
        }

        val sink = encoderSinkRef.get()
        if (recordCompositeToEncoder.get() && sink != null) {
            recordDrawCounter++
            if (recordDrawCounter % 2 == 0) {
                sink.recordFrame { rw, rh ->
                    val recGeo = Geometry(rw, rh, g.bufferW, g.bufferH, g.coverCrop)
                    if (dual && frontSurfaceTexture != null) {
                        drawStackedComposite(prog, recGeo, frontReady = true)
                    } else {
                        drawOesToViewport(
                            prog,
                            recGeo,
                            oesTextureId,
                            stMatrix,
                            0,
                            0,
                            rw,
                            rh,
                            applyLut = true,
                        )
                    }
                }
            }
        } else {
            recordDrawCounter = 0
        }

        onPreviewFramePresented()
    }

    private fun drawStackedComposite(
        prog: LutExternalOesShaderProgram,
        g: Geometry,
        frontReady: Boolean,
    ) {
        val frontH =
            (g.viewH * DualVideoRecordingController.STACKED_FRONT_HEIGHT_FRACTION).toInt()
                .coerceIn(1, g.viewH - 1)
        val rearH = g.viewH - frontH
        val fb = frontBufferSize.get()
        val frontBufW = fb[0].takeIf { it > 0 } ?: g.bufferW
        val frontBufH = fb[1].takeIf { it > 0 } ?: g.bufferH
        val frontGeo =
            Geometry(
                viewW = g.viewW,
                viewH = frontH,
                bufferW = frontBufW,
                bufferH = frontBufH,
                coverCrop = true,
            )
        drawOesToViewport(
            prog,
            g,
            oesTextureId,
            stMatrix,
            viewX = 0,
            viewY = frontH,
            viewW = g.viewW,
            viewH = rearH,
            applyLut = true,
        )
        if (frontReady && frontOesTextureId != 0) {
            drawOesToViewport(
                prog = prog,
                g = frontGeo,
                oesId = frontOesTextureId,
                matrix = frontStMatrix,
                viewX = 0,
                viewY = 0,
                viewW = g.viewW,
                viewH = frontH,
                applyLut = false,
                readoutWb = IDENTITY_WB,
            )
        } else {
            GLES20.glViewport(0, 0, g.viewW, frontH)
            GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glViewport(0, 0, g.viewW, g.viewH)
    }

    private fun drawOesToViewport(
        prog: LutExternalOesShaderProgram,
        g: Geometry,
        oesId: Int,
        matrix: FloatArray,
        viewX: Int,
        viewY: Int,
        viewW: Int,
        viewH: Int,
        applyLut: Boolean = true,
        readoutWb: FloatArray = readoutWbRgb(),
    ) {
        GLES20.glViewport(viewX, viewY, viewW.coerceAtLeast(1), viewH.coerceAtLeast(1))
        val lut = if (applyLut) activeLut.get() else null
        prog.bindPerFrameUniforms(
            stMatrix16 = matrix,
            viewW = viewW,
            viewH = viewH,
            bufW = g.bufferW,
            bufH = g.bufferH,
            coverCrop = g.coverCrop,
            lut = lut,
            readoutWbRgb = readoutWb,
            focusPeaking = if (applyLut) focusPeakingUniforms() else FocusPeakingGlUniforms.disabled(),
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesId)
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
    }

    private fun ensureFrontOesTextureOnGlThread() {
        if (frontOesTextureId != 0) {
            Log.d(TAG, "Front OES texture already exists")
            return
        }
        
        try {
            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            frontOesTextureId = texIds[0]
            if (frontOesTextureId == 0) {
                Log.e(TAG, "Failed to generate front OES texture")
                return
            }
            
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frontOesTextureId)
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
            
            val fst = SurfaceTexture(frontOesTextureId)
            frontSurfaceTexture = fst
            frontTextureInitialized = false
            frontFrameCount = 0
            
            val w = 960
            val h = 540
            fst.setDefaultBufferSize(w, h)
            fst.setOnFrameAvailableListener { 
                frontTextureInitialized = true
                glSurfaceViewRef?.requestRender() 
            }
            
            onDualFrontReady.get()?.let { cb -> mainHandler.post { cb(fst, w, h) } }
            Log.i(TAG, "dual front OES texture ready ${w}x$h, textureId=$frontOesTextureId")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating front OES texture: ${e.message}", e)
            // Clean up on failure
            if (frontOesTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(frontOesTextureId), 0)
                frontOesTextureId = 0
            }
        }
    }

    private fun releaseFrontOesOnGlThread() {
        Log.i(TAG, "Releasing front OES texture")
        val fst = frontSurfaceTexture
        frontSurfaceTexture = null
        frontTextureInitialized = false
        frontFrameCount = 0
        
        if (fst != null) {
            runCatching { fst.setOnFrameAvailableListener(null) }
            runCatching { fst.release() }
        }
        
        if (frontOesTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(frontOesTextureId), 0)
            frontOesTextureId = 0
        }
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
        private val IDENTITY_WB = floatArrayOf(1f, 1f, 1f)
    }
}
