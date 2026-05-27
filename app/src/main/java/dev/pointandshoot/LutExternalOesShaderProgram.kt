package dev.pointandshoot



import android.opengl.GLES20

import android.opengl.GLES30



/**

 * GLES 3.0 program: [GL_TEXTURE_EXTERNAL_OES] camera stream + optional [Lut3D]

 * (same bypass policy as [LutShaderProgram.BypassPolicy]).

 */

class LutExternalOesShaderProgram private constructor(

    val programId: Int,

    val attribPosition: Int,

    val attribTexCoord: Int,

    val uniformStMatrix: Int,

    val uniformViewW: Int,

    val uniformViewH: Int,

    val uniformTexW: Int,

    val uniformTexH: Int,

    val uniformAspectW: Int,

    val uniformAspectH: Int,

    val uniformCoverCrop: Int,

    val uniformBufKnown: Int,

    val uniformSourceTex: Int,

    val uniformLutTex: Int,

    val uniformLutSize: Int,

    val uniformLutEnabled: Int,

    val uniformReadoutWbRgb: Int,

    val uniformPeakingEnabled: Int,

    val uniformPeakingRgb: Int,

    val uniformPeakingSensitivity: Int,

) {



    object Source {

        const val VERTEX_ASSET_PATH: String = "shaders/lut_preview_external.vert.glsl"

        const val FRAGMENT_ASSET_PATH: String = "shaders/lut_preview_external.frag.glsl"



        const val ATTRIB_POSITION: String = "aPosition"

        const val ATTRIB_TEX_COORD: String = "aTexCoord"

        const val UNIFORM_ST_MATRIX: String = "uStMatrix"

        const val UNIFORM_VIEW_W: String = "uViewW"

        const val UNIFORM_VIEW_H: String = "uViewH"

        const val UNIFORM_TEX_W: String = "uTexW"

        const val UNIFORM_TEX_H: String = "uTexH"

        const val UNIFORM_ASPECT_W: String = "uAspectW"

        const val UNIFORM_ASPECT_H: String = "uAspectH"

        const val UNIFORM_COVER_CROP: String = "uCoverCrop"

        const val UNIFORM_BUF_KNOWN: String = "uBufKnown"

        const val UNIFORM_SOURCE_TEX: String = "uSourceTex"

        const val UNIFORM_LUT_TEX: String = "uLutTex"

        const val UNIFORM_LUT_SIZE: String = "uLutSize"

        const val UNIFORM_LUT_ENABLED: String = "uLutEnabled"

        const val UNIFORM_READOUT_WB_RGB: String = "uReadoutWbRgb"

        const val UNIFORM_PEAKING_ENABLED: String = "uPeakingEnabled"

        const val UNIFORM_PEAKING_RGB: String = "uPeakingRgb"

        const val UNIFORM_PEAKING_SENSITIVITY: String = "uPeakingSensitivity"



        const val REQUIRED_GLSL_VERSION_DIRECTIVE: String = "#version 300 es"



        fun requiredSymbols(): List<String> = listOf(

            ATTRIB_POSITION,

            ATTRIB_TEX_COORD,

            UNIFORM_ST_MATRIX,

            UNIFORM_VIEW_W,

            UNIFORM_VIEW_H,

            UNIFORM_TEX_W,

            UNIFORM_TEX_H,

            UNIFORM_ASPECT_W,

            UNIFORM_ASPECT_H,

            UNIFORM_COVER_CROP,

            UNIFORM_BUF_KNOWN,

            UNIFORM_SOURCE_TEX,

            UNIFORM_LUT_TEX,

            UNIFORM_LUT_SIZE,

            UNIFORM_LUT_ENABLED,

            UNIFORM_READOUT_WB_RGB,

            UNIFORM_PEAKING_ENABLED,

            UNIFORM_PEAKING_RGB,

            UNIFORM_PEAKING_SENSITIVITY,

        )

    }



    private val stMatrixScratch = FloatArray(16)



    fun bindPerFrameUniforms(

        stMatrix16: FloatArray,

        viewW: Int,

        viewH: Int,

        texW: Int,

        texH: Int,

        aspectW: Int,

        aspectH: Int,

        coverCrop: Boolean,

        /** When true, [stMatrix16] already includes center-fit/crop; vertex shader must not re-fit. */
        fitAppliedInMatrix: Boolean = false,

        lut: Lut3D?,

        readoutWbRgb: FloatArray,

        focusPeaking: FocusPeakingGlUniforms,

    ) {

        GLES20.glUseProgram(programId)

        System.arraycopy(stMatrix16, 0, stMatrixScratch, 0, 16)

        GLES20.glUniformMatrix4fv(uniformStMatrix, 1, false, stMatrixScratch, 0)

        GLES20.glUniform1f(uniformViewW, viewW.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformViewH, viewH.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformTexW, texW.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformTexH, texH.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformAspectW, aspectW.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformAspectH, aspectH.coerceAtLeast(1).toFloat())

        GLES20.glUniform1f(uniformCoverCrop, if (coverCrop) 1f else 0f)

        GLES20.glUniform1f(
            uniformBufKnown,
            if (!fitAppliedInMatrix && texW > 0 && texH > 0) 1f else 0f,
        )

        GLES20.glUniform1i(uniformSourceTex, 0)

        GLES20.glUniform1i(uniformLutTex, 1)

        GLES20.glUniform1f(uniformLutSize, (lut?.size ?: 33).toFloat())

        GLES20.glUniform1f(

            uniformLutEnabled,

            LutShaderProgram.BypassPolicy.lutEnabledUniform(lut),

        )

        val r = readoutWbRgb.getOrElse(0) { 1f }

        val g = readoutWbRgb.getOrElse(1) { 1f }

        val b = readoutWbRgb.getOrElse(2) { 1f }

        if (uniformReadoutWbRgb >= 0) {

            GLES20.glUniform3f(uniformReadoutWbRgb, r, g, b)

        }

        if (uniformPeakingEnabled >= 0) {

            GLES20.glUniform1f(uniformPeakingEnabled, if (focusPeaking.enabled) 1f else 0f)

        }

        if (uniformPeakingRgb >= 0) {

            GLES20.glUniform3f(uniformPeakingRgb, focusPeaking.r, focusPeaking.g, focusPeaking.b)

        }

        if (uniformPeakingSensitivity >= 0) {

            GLES20.glUniform1f(uniformPeakingSensitivity, focusPeaking.sensitivity)

        }

    }



    fun release() {

        if (programId != 0) GLES20.glDeleteProgram(programId)

    }



    companion object {

        private const val TAG = "PNS.GLES"



        fun create(vertSrc: String, fragSrc: String): LutExternalOesShaderProgram {

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

                error("$TAG: external-OES shader link failed: $log")

            }

            GLES20.glDeleteShader(vert)

            GLES20.glDeleteShader(frag)

            return LutExternalOesShaderProgram(

                programId = program,

                attribPosition = GLES20.glGetAttribLocation(program, Source.ATTRIB_POSITION),

                attribTexCoord = GLES20.glGetAttribLocation(program, Source.ATTRIB_TEX_COORD),

                uniformStMatrix = GLES20.glGetUniformLocation(program, Source.UNIFORM_ST_MATRIX),

                uniformViewW = GLES20.glGetUniformLocation(program, Source.UNIFORM_VIEW_W),

                uniformViewH = GLES20.glGetUniformLocation(program, Source.UNIFORM_VIEW_H),

                uniformTexW = GLES20.glGetUniformLocation(program, Source.UNIFORM_TEX_W),

                uniformTexH = GLES20.glGetUniformLocation(program, Source.UNIFORM_TEX_H),

                uniformAspectW = GLES20.glGetUniformLocation(program, Source.UNIFORM_ASPECT_W),

                uniformAspectH = GLES20.glGetUniformLocation(program, Source.UNIFORM_ASPECT_H),

                uniformCoverCrop = GLES20.glGetUniformLocation(program, Source.UNIFORM_COVER_CROP),

                uniformBufKnown = GLES20.glGetUniformLocation(program, Source.UNIFORM_BUF_KNOWN),

                uniformSourceTex = GLES20.glGetUniformLocation(program, Source.UNIFORM_SOURCE_TEX),

                uniformLutTex = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_TEX),

                uniformLutSize = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_SIZE),

                uniformLutEnabled = GLES20.glGetUniformLocation(program, Source.UNIFORM_LUT_ENABLED),

                uniformReadoutWbRgb = GLES20.glGetUniformLocation(program, Source.UNIFORM_READOUT_WB_RGB),

                uniformPeakingEnabled = GLES20.glGetUniformLocation(program, Source.UNIFORM_PEAKING_ENABLED),

                uniformPeakingRgb = GLES20.glGetUniformLocation(program, Source.UNIFORM_PEAKING_RGB),

                uniformPeakingSensitivity = GLES20.glGetUniformLocation(program, Source.UNIFORM_PEAKING_SENSITIVITY),

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

    }

}

