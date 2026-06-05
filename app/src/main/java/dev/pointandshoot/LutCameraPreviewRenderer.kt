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
import java.util.concurrent.atomic.AtomicBoolean
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
    /** Sprint **15.16** — [VideoColorProfile.previewGlMode] (0 = SDR). */
    private val videoColorProfileMode: () -> Float = { 0f },
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
    private val composedStMatrixScratch = FloatArray(16)
    /** Set by [SurfaceTexture.setOnFrameAvailableListener]; consumed in [onDrawFrame]. */
    private val frameAvailable = AtomicBoolean(false)
    /** Front aux texture — only [SurfaceTexture.updateTexImage] when set (avoids streaks). */
    private val frontFrameAvailable = AtomicBoolean(false)
    private var rearTextureReady: Boolean = false
    private var frontTextureReady: Boolean = false
    @Volatile private var lastBufferW: Int = 0
    @Volatile private var lastBufferH: Int = 0
    private val pendingLut: AtomicReference<LutUpdate?> = AtomicReference(null)
    private val activeLut: AtomicReference<Lut3D?> = AtomicReference(null)
    /** Latest LUT from [setLut]; replayed after EGL context loss so preview survives pause/resume. */
    private val lastLutRequested: AtomicReference<Lut3D?> = AtomicReference(null)
    private val geometry = AtomicReference(Geometry(1, 1, 0, 0, 0, 0, coverCrop = true))
    private val dualSplitEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Milestone **20.3** — concurrent rear+rear inset (reuses aux OES path). */
    private val pipInsetEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val hfrYuvMonitorEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
    private var hfrYuvProgram: HfrYuvMonitorShaderProgram? = null
    private var hfrYuvQuadBuffer: FloatBuffer? = null
    private var hfrYuvQuadCacheKey: Long = Long.MIN_VALUE
    private var hfrYuvTexY: Int = 0
    private var hfrYuvTexU: Int = 0
    private var hfrYuvTexV: Int = 0
    private var hfrYuvTexW: Int = 0
    private var hfrYuvTexH: Int = 0
    private val pendingHfrYuvFrame = AtomicReference<HfrYuvMonitorFrame?>(null)
    @Volatile private var lastHfrYuvDrawFrame: HfrYuvMonitorFrame? = null
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

    private var frontOesDiagFrames: Long = 0L
    private val maxFrontFrameErrors: Int = 10

    private var glSurfaceViewRef: GLSurfaceView? = null

    fun attachView(view: GLSurfaceView) {
        glSurfaceViewRef = view
    }

    fun detachView() {
        glSurfaceViewRef = null
    }

    fun surfaceTextureOrNull(): SurfaceTexture? = surfaceTexture

    /**
     * Latest [SurfaceTexture.getTransformMatrix] for face/eye HUD alignment (main thread).
     * Matches the matrix fed to [lut_preview_external.vert.glsl] on the last drawn frame.
     */
    fun readSurfaceTransformMatrix(dest: FloatArray): Boolean {
        if (dest.size < 16) return false
        val st = surfaceTexture ?: return false
        return runCatching {
            st.getTransformMatrix(dest)
            true
        }.getOrDefault(false)
    }

    /** Apply [setGeometry] on the GL thread; keeps last non-zero buffer dims across layout churn. */
    fun queueSetGeometry(
        viewW: Int,
        viewH: Int,
        bufferW: Int,
        bufferH: Int,
        coverCrop: Boolean,
    ) {
        val bw: Int
        val bh: Int
        if (bufferW > 0 && bufferH > 0) {
            lastBufferW = bufferW
            lastBufferH = bufferH
            bw = bufferW
            bh = bufferH
        } else if (lastBufferW > 0 && lastBufferH > 0) {
            bw = lastBufferW
            bh = lastBufferH
        } else {
            bw = 0
            bh = 0
        }
        val glv = glSurfaceViewRef ?: return
        glv.queueEvent {
            setGeometry(
                viewW = viewW,
                viewH = viewH,
                bufferW = bw,
                bufferH = bh,
                coverCrop = coverCrop,
                aspectW = bw,
                aspectH = bh,
            )
        }
        glv.requestRender()
    }

    fun setGeometry(
        viewW: Int,
        viewH: Int,
        bufferW: Int,
        bufferH: Int,
        coverCrop: Boolean,
        aspectW: Int = bufferW,
        aspectH: Int = bufferH,
    ) {
        val g =
            Geometry(
                viewW.coerceAtLeast(1),
                viewH.coerceAtLeast(1),
                bufferW.coerceAtLeast(0),
                bufferH.coerceAtLeast(0),
                aspectW.coerceAtLeast(0),
                aspectH.coerceAtLeast(0),
                coverCrop,
            )
        val prev = geometry.getAndSet(g)
        if (
            prev.bufferW != g.bufferW ||
                prev.bufferH != g.bufferH ||
                prev.viewW != g.viewW ||
                prev.viewH != g.viewH ||
                prev.coverCrop != g.coverCrop
        ) {
            Log.i(
                TAG,
                "previewGeometry view=${g.viewW}x${g.viewH} buf=${g.bufferW}x${g.bufferH} " +
                    "coverCrop=${g.coverCrop}",
            )
        }
    }

    fun setLut(lut: Lut3D?) {
        lastLutRequested.set(lut)
        pendingLut.set(LutUpdate(lut))
    }

    fun setFrontBufferSize(bufferW: Int, bufferH: Int) {
        frontBufferSize.set(intArrayOf(bufferW.coerceAtLeast(0), bufferH.coerceAtLeast(0)))
    }

    /**
     * Resize the camera-facing external-OES [SurfaceTexture] on the GL thread so
     * [Surface.isValid] stays true for [OutputConfiguration] (main-thread
     * [SurfaceTexture.setDefaultBufferSize] abandons the producer queue).
     */
    fun queueSetPreviewBufferSize(bufferW: Int, bufferH: Int, onComplete: () -> Unit) {
        val w = bufferW.coerceAtLeast(0)
        val h = bufferH.coerceAtLeast(0)
        val glv = glSurfaceViewRef
        if (glv == null || w <= 0 || h <= 0) {
            mainHandler.post(onComplete)
            return
        }
        glv.queueEvent {
            val st = surfaceTexture
            if (st != null) {
                runCatching { st.setDefaultBufferSize(w, h) }
                lastBufferW = w
                lastBufferH = h
            }
            glv.requestRender()
            mainHandler.postDelayed(onComplete, PREVIEW_BUFFER_QUEUE_PUBLISH_DELAY_MS)
        }
    }

    /** Stacked front (top) + rear (bottom) for Sprint **14.12** / **15.5** dual video. */
    fun setDualSplitEnabled(
        enabled: Boolean,
        onFrontSurfaceReady: ((SurfaceTexture, Int, Int) -> Unit)?,
    ) {
        dualSplitEnabled.set(enabled)
        if (enabled) pipInsetEnabled.set(false)
        onDualFrontReady.set(onFrontSurfaceReady)
        glSurfaceViewRef?.queueEvent {
            if (enabled) {
                ensureFrontOesTextureOnGlThread()
            } else {
                releaseFrontOesOnGlThread()
            }
        }
    }

    /** Concurrent rear+rear PiP inset (Milestone **20.3**). */
    fun setPipInsetEnabled(
        enabled: Boolean,
        onAuxSurfaceReady: ((SurfaceTexture, Int, Int) -> Unit)?,
    ) {
        pipInsetEnabled.set(enabled)
        if (enabled) dualSplitEnabled.set(false)
        onDualFrontReady.set(onAuxSurfaceReady)
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
     * During HFR record, draw CPU-drained YUV planes from an HS [android.media.ImageReader]
     * on the **same** camera (not the starved primary preview ST). LUT off.
     */
    fun setHfrYuvMonitorEnabled(enabled: Boolean) {
        hfrYuvMonitorEnabled.set(enabled)
        glSurfaceViewRef?.queueEvent {
            if (enabled) {
                ensureHfrYuvGlResourcesOnGlThread()
            } else {
                releaseHfrYuvGlResourcesOnGlThread()
            }
        }
    }

    /** Called after [HfrYuvImageCopier.copy] on the camera handler; uploads on next draw. */
    fun deliverHfrYuvFrame(frame: HfrYuvMonitorFrame) {
        if (!hfrYuvMonitorEnabled.get()) return
        pendingHfrYuvFrame.set(frame)
        glSurfaceViewRef?.requestRender()
    }

    /**
     * Run on the GL thread while the EGL context is still current (e.g. inside
     * [GLSurfaceView.queueEvent] before [GLSurfaceView.onPause]).
     */
    fun releaseGlThread() {
        lastBufferW = 0
        lastBufferH = 0
        val st = surfaceTexture
        surfaceTexture = null
        surfacePosted = false
        if (st != null) {
            runCatching { st.setOnFrameAvailableListener(null) }
            runCatching { st.release() }
        }
        releaseFrontOesOnGlThread()
        releaseHfrYuvGlResourcesOnGlThread()
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
            frameAvailable.set(true)
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
        consumePendingLut()

        val yuvMonitorMode = hfrYuvMonitorEnabled.get()
        val yuvFrame = if (yuvMonitorMode) pendingHfrYuvFrame.getAndSet(null) else null

        val dual = dualSplitEnabled.get()
        val pip = pipInsetEnabled.get()
        val rearSynced = syncRearOesTexture()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val g = geometry.get()
        val frontOk =
            if (dual || pip) {
                syncFrontOesTextureForDual()
            } else {
                false
            }
        if (dual) {
            if (frontOk) {
                drawStackedComposite(prog, g, frontReady = true)
            } else if (g.bufferW > 0 && g.bufferH > 0) {
                // Front not ready yet — full rear finder (avoid black/streak split).
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
        } else if (pip) {
            if (frontOk) {
                drawPipInsetComposite(prog, g, auxReady = true)
            } else if (g.bufferW > 0 && g.bufferH > 0) {
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
        } else if (yuvMonitorMode) {
            var frame = yuvFrame
            if (frame != null) {
                lastHfrYuvDrawFrame = frame
            } else {
                frame = lastHfrYuvDrawFrame
            }
            if (frame != null) {
                drawHfrYuvFrame(frame, g)
            }
            // else: primary ST is starved during encoder-only HS — keep cleared until monitor YUV arrives
        } else if (g.bufferW > 0 && g.bufferH > 0) {
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
            if (dual) {
                if (rearSynced) {
                sink.recordFrame { rw, rh ->
                    // Textures already latched above — do not call updateTexImage twice per vsync.
                    val recGeo =
                        Geometry(rw, rh, g.bufferW, g.bufferH, g.aspectW, g.aspectH, g.coverCrop)
                    drawStackedComposite(prog, recGeo, frontReady = frontOk)
                }
                }
            } else {
                recordDrawCounter++
                if (recordDrawCounter % 2 == 0) {
                    sink.recordFrame { rw, rh ->
                        val recGeo =
                            Geometry(rw, rh, g.bufferW, g.bufferH, g.aspectW, g.aspectH, g.coverCrop)
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
        val frontBandH =
            (g.viewH * DualVideoRecordingController.STACKED_FRONT_HEIGHT_FRACTION).toInt()
                .coerceIn(1, g.viewH - 1)
        val rearBandH = g.viewH - frontBandH
        val rearBufW = g.bufferW.takeIf { it > 0 } ?: g.aspectW.coerceAtLeast(1)
        val rearBufH = g.bufferH.takeIf { it > 0 } ?: g.aspectH.coerceAtLeast(1)
        val fb = frontBufferSize.get()
        val frontBufW = fb[0].takeIf { it > 0 } ?: rearBufW
        val frontBufH = fb[1].takeIf { it > 0 } ?: rearBufH
        val rearGeo =
            Geometry(
                viewW = g.viewW,
                viewH = rearBandH,
                bufferW = rearBufW,
                bufferH = rearBufH,
                aspectW = rearBufW,
                aspectH = rearBufH,
                coverCrop = false,
            )
        val frontGeo =
            Geometry(
                viewW = g.viewW,
                viewH = frontBandH,
                bufferW = frontBufW,
                bufferH = frontBufH,
                aspectW = frontBufW,
                aspectH = frontBufH,
                coverCrop = false,
            )
        // GLES viewY=0 is bottom — rear on bottom, front on top; center-fit via vertex shader per band.
        clearSubViewport(0, 0, g.viewW, rearBandH)
        drawOesToViewport(
            prog,
            rearGeo,
            oesTextureId,
            stMatrix,
            viewX = 0,
            viewY = 0,
            viewW = g.viewW,
            viewH = rearBandH,
            applyLut = true,
        )
        clearSubViewport(0, rearBandH, g.viewW, frontBandH)
        if (frontReady && frontOesTextureId != 0) {
            drawOesToViewport(
                prog = prog,
                g = frontGeo,
                oesId = frontOesTextureId,
                matrix = frontStMatrix,
                viewX = 0,
                viewY = rearBandH,
                viewW = g.viewW,
                viewH = frontBandH,
                applyLut = false,
                readoutWb = IDENTITY_WB,
            )
        }
        GLES20.glViewport(0, 0, g.viewW, g.viewH)
    }

    /** Top-right inset aux rear preview (~28% width). */
    private fun drawPipInsetComposite(prog: LutExternalOesShaderProgram, g: Geometry, auxReady: Boolean) {
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
        if (!auxReady || frontOesTextureId == 0) return
        val insetW = (g.viewW * ConcurrentPipPreviewController.INSET_WIDTH_FRACTION).toInt().coerceAtLeast(1)
        val insetH = (insetW * 4) / 3
        val margin = (g.viewW * 0.04f).toInt().coerceAtLeast(4)
        val fb = frontBufferSize.get()
        val auxBufW = fb[0].takeIf { it > 0 } ?: g.bufferW
        val auxBufH = fb[1].takeIf { it > 0 } ?: g.bufferH
        val auxGeo =
            Geometry(
                viewW = insetW,
                viewH = insetH,
                bufferW = auxBufW,
                bufferH = auxBufH,
                aspectW = auxBufW,
                aspectH = auxBufH,
                coverCrop = false,
            )
        val insetY = g.viewH - insetH - margin
        clearSubViewport(margin, insetY, insetW, insetH)
        drawOesToViewport(
            prog = prog,
            g = auxGeo,
            oesId = frontOesTextureId,
            matrix = frontStMatrix,
            viewX = margin,
            viewY = insetY,
            viewW = insetW,
            viewH = insetH,
            applyLut = false,
            readoutWb = IDENTITY_WB,
        )
        GLES20.glViewport(0, 0, g.viewW, g.viewH)
    }

    private fun syncRearOesTexture(): Boolean {
        if (hfrYuvMonitorEnabled.get()) return false
        val st = surfaceTexture ?: return false
        return runCatching {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
            rearTextureReady = true
            true
        }.getOrDefault(false)
    }

    /** Front OES: latch only on [frontFrameAvailable] (unconsumed updates cause streaks). */
    private fun syncFrontOesTextureForDual(): Boolean {
        val fst = frontSurfaceTexture ?: return false
        if (!frontTextureInitialized) return false
        if (frontFrameAvailable.compareAndSet(true, false)) {
            frontTextureReady =
                runCatching {
                    fst.updateTexImage()
                    fst.getTransformMatrix(frontStMatrix)
                    frontOesDiagFrames++
                    true
                }.getOrDefault(false)
            if (!frontTextureReady) {
                frontFrameCount++
                if (frontFrameCount > maxFrontFrameErrors) {
                    Log.e(TAG, "dualFront texture update failed repeatedly")
                    frontTextureInitialized = false
                }
            } else {
                frontFrameCount = 0
            }
        }
        return frontTextureReady
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
        /** Dual stacked halves only — main finder preview uses shader [Geometry.coverCrop] like HEAD. */
        fitContainInMatrix: Boolean = false,
    ) {
        GLES20.glViewport(viewX, viewY, viewW.coerceAtLeast(1), viewH.coerceAtLeast(1))
        val lut = if (applyLut) activeLut.get() else null
        val bufW = g.bufferW
        val bufH = g.bufferH
        val fitInMatrix = fitContainInMatrix && bufW > 0 && bufH > 0
        val matrixForShader =
            if (fitInMatrix) {
                TexturePreviewFit.composeExternalOesPreviewMatrix(
                    destColumnMajor4x4 = composedStMatrixScratch,
                    viewWidthPx = viewW,
                    viewHeightPx = viewH,
                    bufferWidthPx = bufW,
                    bufferHeightPx = bufH,
                    coverCrop = false,
                    surfaceTransformColumnMajor4x4 = matrix,
                )
                composedStMatrixScratch
            } else {
                matrix
            }
        prog.bindPerFrameUniforms(
            stMatrix16 = matrixForShader,
            viewW = viewW,
            viewH = viewH,
            texW = bufW,
            texH = bufH,
            aspectW = g.aspectW.takeIf { it > 0 } ?: bufW,
            aspectH = g.aspectH.takeIf { it > 0 } ?: bufH,
            coverCrop = if (fitInMatrix) false else g.coverCrop,
            fitAppliedInMatrix = fitInMatrix,
            lut = lut,
            readoutWbRgb = readoutWb,
            focusPeaking = if (applyLut) focusPeakingUniforms() else FocusPeakingGlUniforms.disabled(),
            videoColorProfileMode = if (applyLut) videoColorProfileMode() else 0f,
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
            val existing = frontSurfaceTexture
            if (existing != null) {
                val buf = frontBufferSize.get()
                val w = buf.getOrNull(0)?.takeIf { it > 0 } ?: 960
                val h = buf.getOrNull(1)?.takeIf { it > 0 } ?: 540
                onDualFrontReady.get()?.let { cb ->
                    mainHandler.post { cb(existing, w, h) }
                }
                Log.d(TAG, "Front OES texture already exists — rebinding surface $w×$h")
            }
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
            
            val w = 640
            val h = 480
            fst.setDefaultBufferSize(w, h)
            fst.setOnFrameAvailableListener {
                frontFrameAvailable.set(true)
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

    private fun drawHfrYuvFrame(frame: HfrYuvMonitorFrame, g: Geometry) {
        val yuvProg = hfrYuvProgram ?: return
        val quad = hfrYuvQuadForFrame(frame) ?: return
        ensureHfrYuvTexturesSized(frame.width, frame.height)
        HfrYuvMonitorShaderProgram.uploadPlaneR8(hfrYuvTexY, frame.width, frame.height, frame.y)
        val uvW = frame.width / 2
        val uvH = frame.height / 2
        HfrYuvMonitorShaderProgram.uploadPlaneR8(hfrYuvTexU, uvW, uvH, frame.u)
        HfrYuvMonitorShaderProgram.uploadPlaneR8(hfrYuvTexV, uvW, uvH, frame.v)
        val rot = ((frame.textureRotationDeg % 360) + 360) % 360
        val crop = frame.textureCrop
        val uvSpanU = crop.u1 - crop.u0
        val uvSpanV = crop.v1 - crop.v0
        val dispW =
            ((if (rot == 90 || rot == 270) frame.height else frame.width) * uvSpanU).toInt()
                .coerceAtLeast(1)
        val dispH =
            ((if (rot == 90 || rot == 270) frame.width else frame.height) * uvSpanV).toInt()
                .coerceAtLeast(1)
        val vp =
            if (g.coverCrop) {
                coverCropViewport(g.viewW, g.viewH, dispW, dispH)
            } else {
                intArrayOf(0, 0, g.viewW, g.viewH)
            }
        yuvProg.draw(
            quad,
            hfrYuvTexY,
            hfrYuvTexU,
            hfrYuvTexV,
            vp[0],
            vp[1],
            vp[2],
            vp[3],
        )
    }

    private fun hfrYuvQuadForFrame(frame: HfrYuvMonitorFrame): FloatBuffer? {
        val normalized = ((frame.textureRotationDeg % 360) + 360) % 360
        val crop = frame.textureCrop
        val key =
            (normalized.toLong() shl 32) xor
                (crop.u0.toBits().toLong() shl 16) xor
                crop.v0.toBits().toLong() xor
                crop.u1.toBits().toLong() xor
                crop.v1.toBits().toLong()
        if (key != hfrYuvQuadCacheKey) {
            hfrYuvQuadCacheKey = key
            hfrYuvQuadBuffer =
                ByteBuffer.allocateDirect(LutShaderProgram.Source.FULL_SCREEN_QUAD.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(HfrYuvTexCoord.rotatedCroppedFullScreenQuad(normalized, crop))
                        position(0)
                    }
        }
        return hfrYuvQuadBuffer
    }

    private fun clearSubViewport(viewX: Int, viewY: Int, viewW: Int, viewH: Int) {
        GLES20.glViewport(viewX, viewY, viewW.coerceAtLeast(1), viewH.coerceAtLeast(1))
        GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    /** Center-crop buffer aspect into the view (matches OES cover-crop intent). */
    private fun coverCropViewport(viewW: Int, viewH: Int, bufW: Int, bufH: Int): IntArray {
        if (bufW <= 0 || bufH <= 0) return intArrayOf(0, 0, viewW, viewH)
        val viewAspect = viewW.toFloat() / viewH.coerceAtLeast(1)
        val bufAspect = bufW.toFloat() / bufH
        return if (bufAspect > viewAspect) {
            val w = (viewH * bufAspect).toInt().coerceAtMost(viewW)
            val x = (viewW - w) / 2
            intArrayOf(x, 0, w, viewH)
        } else {
            val h = (viewW / bufAspect).toInt().coerceAtMost(viewH)
            val y = (viewH - h) / 2
            intArrayOf(0, y, viewW, h)
        }
    }

    private fun ensureHfrYuvGlResourcesOnGlThread() {
        if (hfrYuvProgram != null) return
        runCatching {
            val vert = assetLoader(HfrYuvMonitorShaderProgram.VERTEX_ASSET_PATH)
            val frag = assetLoader(HfrYuvMonitorShaderProgram.FRAGMENT_ASSET_PATH)
            hfrYuvProgram = HfrYuvMonitorShaderProgram.create(vert, frag)
            hfrYuvQuadBuffer =
                ByteBuffer.allocateDirect(LutShaderProgram.Source.FULL_SCREEN_QUAD.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(LutShaderProgram.Source.FULL_SCREEN_QUAD)
                        position(0)
                    }
            Log.i(TAG, "HFR YUV monitor shader ready")
        }.onFailure { e ->
            Log.e(TAG, "HFR YUV shader setup failed: ${e.message}", e)
        }
    }

    private fun ensureHfrYuvTexturesSized(w: Int, h: Int) {
        if (hfrYuvTexY != 0 && hfrYuvTexW == w && hfrYuvTexH == h) return
        releaseHfrYuvTextureIds()
        val ids = IntArray(3)
        GLES20.glGenTextures(3, ids, 0)
        hfrYuvTexY = ids[0]
        hfrYuvTexU = ids[1]
        hfrYuvTexV = ids[2]
        hfrYuvTexW = w
        hfrYuvTexH = h
    }

    private fun releaseHfrYuvTextureIds() {
        val toDelete = intArrayOf(hfrYuvTexY, hfrYuvTexU, hfrYuvTexV).filter { it != 0 }.toIntArray()
        if (toDelete.isNotEmpty()) GLES20.glDeleteTextures(toDelete.size, toDelete, 0)
        hfrYuvTexY = 0
        hfrYuvTexU = 0
        hfrYuvTexV = 0
        hfrYuvTexW = 0
        hfrYuvTexH = 0
    }

    private fun releaseHfrYuvGlResourcesOnGlThread() {
        pendingHfrYuvFrame.set(null)
        lastHfrYuvDrawFrame = null
        hfrYuvProgram?.release()
        hfrYuvProgram = null
        hfrYuvQuadBuffer = null
        hfrYuvQuadCacheKey = Long.MIN_VALUE
        releaseHfrYuvTextureIds()
    }

    private fun releaseFrontOesOnGlThread() {
        Log.i(TAG, "Releasing front OES texture")
        val fst = frontSurfaceTexture
        frontSurfaceTexture = null
        frontTextureInitialized = false
        frontFrameCount = 0
        frontFrameAvailable.set(false)
        frontTextureReady = false
        rearTextureReady = false
        
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
        val aspectW: Int,
        val aspectH: Int,
        val coverCrop: Boolean,
    )

    companion object {
        private const val TAG = "PNS.GLES.Preview"
        private val IDENTITY_WB = floatArrayOf(1f, 1f, 1f)

        /** Delay after GL-thread buffer resize before Camera2 binds [OutputConfiguration]. */
        private const val PREVIEW_BUFFER_QUEUE_PUBLISH_DELAY_MS = 300L
    }
}
