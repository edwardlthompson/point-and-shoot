package dev.pointandshoot

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30

/**
 * GLES 3.0 program for the [Lut3D] apply path used by live preview and the
 * video encode surface (per BUILD_PLAN §7 "Apply path").
 *
 * The program is split into three layers so the host side stays unit-
 * testable on the JVM without an EGL context:
 *
 *   1. [Source]: pure-data accessors for the shader source strings (read
 *      from `app/src/main/assets/shaders/`) and the public uniform / vertex-
 *      attribute names. Pure functions only - no GLES, no Android imports.
 *      [LutShaderProgramSourceTest] validates the on-disk asset against this
 *      contract so a future shader edit cannot silently break the binding.
 *   2. [BypassPolicy]: pure-data decider for whether a given [Lut3D] should
 *      drive `uLutEnabled = 0.0` (identity LUT, skip the sampler3D fetch).
 *      Centralizes the policy so the GLES program, the CPU apply path, and
 *      the host-side performance budget all agree on what counts as "off".
 *   3. The class itself: a thin GLES wrapper that compiles the program,
 *      uploads the LUT as a `GL_RGB16F` 3D texture (with `LINEAR` filtering
 *      so trilinear interpolation runs in hardware), and exposes [draw]. The
 *      Android-only entry point lives at [createFromAssets].
 *
 * The contract this program promises to the live-preview composer:
 *   * `aPosition` is a `vec4` NDC quad covering [-1, 1]^2.
 *   * `aTexCoord` is the matching `vec2` UV quad in [0, 1]^2.
 *   * `uSourceTex` is the camera/preview color source (sRGB-encoded).
 *   * `uLutTex` is the active LUT (linear-light, normalized [0, 1]).
 *   * `uLutSize` matches `Lut3D.size` exactly.
 *   * `uLutEnabled` is `0.0` for identity bypass, `1.0` otherwise.
 *
 * If the program fails to compile or link, [createFromAssets] throws an
 * [IllegalStateException] with the GLES infolog so failures surface in
 * `PNS.GLES` logcat instead of vanishing into a black preview.
 */
class LutShaderProgram private constructor(
    val programId: Int,
    val attribPosition: Int,
    val attribTexCoord: Int,
    val uniformSourceTex: Int,
    val uniformLutTex: Int,
    val uniformLutSize: Int,
    val uniformLutEnabled: Int,
) {

    /** Pure-data view of the shader contract; safe to use from JUnit. */
    object Source {

        const val VERTEX_ASSET_PATH: String = "shaders/lut_apply.vert.glsl"
        const val FRAGMENT_ASSET_PATH: String = "shaders/lut_apply.frag.glsl"

        const val ATTRIB_POSITION: String = "aPosition"
        const val ATTRIB_TEX_COORD: String = "aTexCoord"
        const val UNIFORM_SOURCE_TEX: String = "uSourceTex"
        const val UNIFORM_LUT_TEX: String = "uLutTex"
        const val UNIFORM_LUT_SIZE: String = "uLutSize"
        const val UNIFORM_LUT_ENABLED: String = "uLutEnabled"

        /** GLES 3.0 ES is required for `sampler3D` in fragment shaders. */
        const val REQUIRED_GLSL_VERSION_DIRECTIVE: String = "#version 300 es"

        /**
         * Full-screen quad as 4 vertices (xy + uv) in CCW winding. The host
         * issues one `GL_TRIANGLE_STRIP` of length 4 against this layout.
         * Float[16]: { x, y, u, v } * 4.
         */
        val FULL_SCREEN_QUAD: FloatArray = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        /**
         * Return every uniform / attribute name the Kotlin side binds. Used
         * by [LutShaderProgramSourceTest] to confirm the on-disk shader text
         * actually declares each of them - if a shader edit drops a uniform,
         * the test fails before the GLES driver ever sees the program.
         */
        fun requiredSymbols(): List<String> = listOf(
            ATTRIB_POSITION,
            ATTRIB_TEX_COORD,
            UNIFORM_SOURCE_TEX,
            UNIFORM_LUT_TEX,
            UNIFORM_LUT_SIZE,
            UNIFORM_LUT_ENABLED,
        )
    }

    /**
     * Decides whether a given [Lut3D] should bypass the sampler3D fetch.
     * Identity LUTs (within [Lut3D.DEFAULT_IDENTITY_TOLERANCE]) bypass to
     * keep the per-frame cost at "one sourceTex sample" when the user has
     * `LutCatalog.None` selected.
     */
    object BypassPolicy {

        /**
         * @return `0f` to skip the LUT (use the source texel verbatim),
         *   `1f` to apply the LUT.
         */
        fun lutEnabledUniform(lut: Lut3D?): Float =
            if (lut == null || lut.isIdentity()) 0f else 1f

        /**
         * @return `true` when the host should not even bother uploading the
         *   3D texture for this LUT (identity case). Used by the preview
         *   composer to short-circuit allocation.
         */
        fun shouldSkipUpload(lut: Lut3D?): Boolean =
            lut == null || lut.isIdentity()
    }

    /**
     * Bind this program and draw a full-screen quad. The caller is
     * responsible for setting up the source texture (unit 0) and LUT
     * texture (unit 1) before calling.
     *
     * The GLES wiring is intentionally minimal here - the preview composer
     * owns the framebuffer + viewport + vertex buffer setup; this method
     * exists so the composer can issue the draw without re-deriving the
     * uniform values.
     */
    fun bindUniforms(lut: Lut3D?) {
        GLES20.glUseProgram(programId)
        GLES20.glUniform1i(uniformSourceTex, 0)
        GLES20.glUniform1i(uniformLutTex, 1)
        GLES20.glUniform1f(uniformLutSize, (lut?.size ?: 33).toFloat())
        GLES20.glUniform1f(uniformLutEnabled, BypassPolicy.lutEnabledUniform(lut))
    }

    fun release() {
        if (programId != 0) GLES20.glDeleteProgram(programId)
    }

    companion object {
        private const val TAG = "PNS.GLES"

        /**
         * Compile + link the LUT-apply program from the bundled assets and
         * return a ready-to-use [LutShaderProgram]. Must be called on a
         * thread with a current EGL context.
         *
         * Throws [IllegalStateException] (with the GLES infolog) on compile
         * or link failure so the preview pipeline fails loudly rather than
         * silently rendering black.
         */
        fun createFromAssets(context: Context): LutShaderProgram {
            val vertSrc = context.assets.open(Source.VERTEX_ASSET_PATH)
                .bufferedReader().use { it.readText() }
            val fragSrc = context.assets.open(Source.FRAGMENT_ASSET_PATH)
                .bufferedReader().use { it.readText() }
            return create(vertSrc, fragSrc)
        }

        /**
         * Lower-level overload used by [createFromAssets] (and intended for
         * a future Robolectric / instrumented test). Pass the raw shader
         * source strings; the GLES driver does the rest.
         */
        fun create(vertSrc: String, fragSrc: String): LutShaderProgram {
            val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc, "vertex")
            val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc, "fragment")
            val program = GLES20.glCreateProgram()
            check(program != 0) { "glCreateProgram returned 0" }
            GLES20.glAttachShader(program, vert)
            GLES20.glAttachShader(program, frag)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                error("$TAG: shader link failed: $log")
            }
            GLES20.glDeleteShader(vert)
            GLES20.glDeleteShader(frag)
            return LutShaderProgram(
                programId = program,
                attribPosition = GLES20.glGetAttribLocation(program, Source.ATTRIB_POSITION),
                attribTexCoord = GLES20.glGetAttribLocation(program, Source.ATTRIB_TEX_COORD),
                uniformSourceTex = GLES20.glGetUniformLocation(program, Source.UNIFORM_SOURCE_TEX),
                uniformLutTex = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_TEX),
                uniformLutSize = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_SIZE),
                uniformLutEnabled = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_ENABLED),
            )
        }

        private fun compileShader(type: Int, src: String, label: String): Int {
            val id = GLES20.glCreateShader(type)
            check(id != 0) { "glCreateShader($label) returned 0" }
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(id)
                GLES20.glDeleteShader(id)
                error("$TAG: $label shader compile failed: $log")
            }
            return id
        }

        /**
         * Upload a [Lut3D] as a `GL_RGB16F` 3D texture, returning the
         * texture ID. Must be called on a GLES 3.0+ context. The caller
         * owns the resulting texture (call [GLES20.glDeleteTextures] when
         * done). Identity LUTs short-circuit via [BypassPolicy.shouldSkipUpload]
         * before this is invoked.
         */
        fun uploadLutTexture(lut: Lut3D): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val tex = ids[0]
            check(tex != 0) { "glGenTextures returned 0" }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE)
            val pixels = java.nio.ByteBuffer
                .allocateDirect(lut.samples.size * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
            pixels.put(lut.samples).position(0)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_3D,
                0,
                GLES30.GL_RGB16F,
                lut.size, lut.size, lut.size,
                0,
                GLES20.GL_RGB,
                GLES30.GL_FLOAT,
                pixels,
            )
            return tex
        }
    }
}
