package dev.pointandshoot

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES 3.0 renderer that draws a single source [Bitmap] through the bundled
 * [LutShaderProgram] (BUILD_PLAN \u00a77 "Apply path" -> "Live preview /
 * video"). It is intentionally Camera2-agnostic: the source texture is fed
 * from the host (we currently use [TestPattern] as the source while the
 * Phase 1 capture engine is still pending), but the same wiring will accept
 * a [android.graphics.SurfaceTexture]-backed `samplerExternalOES` later
 * with a 5-line shader swap.
 *
 * Threading model:
 *
 *   * The render thread owns every GLES handle (program, source 2D texture,
 *     LUT 3D texture, vertex VBO).
 *   * The UI thread schedules state changes via [setLut] and
 *     [setSourceBitmap]; both writes go through [AtomicReference] slots and
 *     are picked up by the next [onDrawFrame] invocation. No locks held
 *     across `glXxx` calls.
 *   * [release] is called from the render thread when the surface is
 *     destroyed; the host should cancel any pending GLSurfaceView before
 *     calling it directly.
 */
class LutPreviewRenderer(
    private val assetLoader: (path: String) -> String,
) : GLSurfaceView.Renderer {

    private var program: LutShaderProgram? = null
    private var sourceTextureId: Int = 0
    private var lutTextureId: Int = 0
    private var lutTextureSize: Int = 0
    private var quadBuffer: FloatBuffer? = null
    private var viewportW: Int = 0
    private var viewportH: Int = 0

    private val pendingSource: AtomicReference<Bitmap?> = AtomicReference(null)
    /**
     * Pending LUT update from the UI thread. The wrapper [LutUpdate]
     * disambiguates "no pending change" (`null` slot) from "user
     * explicitly requested identity / bypass" (`LutUpdate(null)` slot).
     */
    private val pendingLut: AtomicReference<LutUpdate?> = AtomicReference(null)
    private val activeLut: AtomicReference<Lut3D?> = AtomicReference(null)

    /**
     * Schedule a new [Lut3D] for the next frame. Pass `null` to reset to the
     * identity bypass path. Safe to call from any thread.
     */
    fun setLut(lut: Lut3D?) {
        pendingLut.set(LutUpdate(lut))
    }

    /**
     * Schedule a new source [Bitmap] for the next frame. Pass `null` to
     * leave the existing source in place. Safe to call from any thread; the
     * caller retains ownership of the bitmap (the renderer copies the
     * pixels into a GLES texture and does not retain a reference past the
     * upload).
     */
    fun setSourceBitmap(bitmap: Bitmap?) {
        pendingSource.set(bitmap)
    }

    /** Active LUT after the last frame's pending swap; testable without GLES. */
    fun activeLutForTesting(): Lut3D? = activeLut.get()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vert = assetLoader(LutShaderProgram.Source.VERTEX_ASSET_PATH)
        val frag = assetLoader(LutShaderProgram.Source.FRAGMENT_ASSET_PATH)
        program = LutShaderProgram.create(vert, frag)
        quadBuffer = ByteBuffer
            .allocateDirect(BITMAP_VFLIP_QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(BITMAP_VFLIP_QUAD)
                position(0)
            }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        Log.i(TAG, "GLES program ready (program=${program?.programId})")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width
        viewportH = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val prog = program ?: return
        consumePendingSource()
        consumePendingLut()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (sourceTextureId == 0) return

        prog.bindUniforms(activeLut.get())

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId)
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

    private fun consumePendingSource() {
        val bmp = pendingSource.getAndSet(null) ?: return
        if (sourceTextureId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            sourceTextureId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId)
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
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
            val pixels = ByteBuffer
                .allocateDirect(newLut.samples.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(newLut.samples)
                    position(0)
                }
            GLES30.glTexSubImage3D(
                GLES30.GL_TEXTURE_3D,
                0, 0, 0, 0,
                newLut.size, newLut.size, newLut.size,
                GLES20.GL_RGB,
                GLES30.GL_FLOAT,
                pixels,
            )
        }
        activeLut.set(newLut)
    }

    fun release() {
        program?.release()
        program = null
        if (sourceTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(sourceTextureId), 0)
            sourceTextureId = 0
        }
        if (lutTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
            lutTextureSize = 0
        }
    }

    /**
     * Wrapper that disambiguates "no pending update" (a `null` slot in
     * [pendingLut]) from "user explicitly requested identity / bypass"
     * (a `LutUpdate(null)` slot). Without the wrapper a single
     * `AtomicReference<Lut3D?>` collapses the two cases into the same
     * value, which would re-upload the identity bypass on every frame.
     */
    private data class LutUpdate(val lut: Lut3D?)

    companion object {
        private const val TAG = "PNS.GLES"

        /**
         * Vertically-flipped variant of [LutShaderProgram.Source.FULL_SCREEN_QUAD]
         * used when the source texture is fed by [GLUtils.texImage2D] (i.e. an
         * Android [Bitmap]). Android bitmaps store row 0 at the top of the
         * image, while OpenGL textures store row 0 at the bottom (`v = 0`),
         * so a 1:1 UV mapping renders the bitmap upside-down. Swapping the
         * V coordinate cancels the inversion. The Camera2 pipeline (a future
         * `samplerExternalOES` source) will use [LutShaderProgram.Source.FULL_SCREEN_QUAD]
         * directly because the Camera2 surface texture transform matrix
         * already handles orientation.
         */
        val BITMAP_VFLIP_QUAD: FloatArray = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )
    }
}
