package dev.pointandshoot

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** Minimal YUV→RGB for HFR record finder (no LUT, no external-OES). */
class HfrYuvMonitorShaderProgram private constructor(
    val programId: Int,
    val attribPosition: Int,
    val attribTexCoord: Int,
    val uniformTexY: Int,
    val uniformTexU: Int,
    val uniformTexV: Int,
) {
    fun draw(
        quad: FloatBuffer,
        yTex: Int,
        uTex: Int,
        vTex: Int,
        viewX: Int,
        viewY: Int,
        viewW: Int,
        viewH: Int,
    ) {
        GLES20.glUseProgram(programId)
        GLES20.glViewport(
            viewX,
            viewY,
            viewW.coerceAtLeast(1),
            viewH.coerceAtLeast(1),
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yTex)
        GLES20.glUniform1i(uniformTexY, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uTex)
        GLES20.glUniform1i(uniformTexU, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, vTex)
        GLES20.glUniform1i(uniformTexV, 2)
        quad.position(0)
        GLES20.glEnableVertexAttribArray(attribPosition)
        GLES20.glVertexAttribPointer(attribPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glEnableVertexAttribArray(attribTexCoord)
        GLES20.glVertexAttribPointer(attribTexCoord, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(attribPosition)
        GLES20.glDisableVertexAttribArray(attribTexCoord)
    }

    fun release() {
        if (programId != 0) GLES20.glDeleteProgram(programId)
    }

    companion object {
        private const val TAG = "PNS.GLES"

        const val VERTEX_ASSET_PATH = "shaders/hfr_yuv_monitor.vert.glsl"
        const val FRAGMENT_ASSET_PATH = "shaders/hfr_yuv_monitor.frag.glsl"

        fun create(vertSrc: String, fragSrc: String): HfrYuvMonitorShaderProgram {
            val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc, "hfr-yuv-vert")
            val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc, "hfr-yuv-frag")
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
                error("$TAG: hfr-yuv shader link failed: $log")
            }
            GLES20.glDeleteShader(vert)
            GLES20.glDeleteShader(frag)
            return HfrYuvMonitorShaderProgram(
                programId = program,
                attribPosition = GLES20.glGetAttribLocation(program, "aPosition"),
                attribTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord"),
                uniformTexY = GLES20.glGetUniformLocation(program, "uTexY"),
                uniformTexU = GLES20.glGetUniformLocation(program, "uTexU"),
                uniformTexV = GLES20.glGetUniformLocation(program, "uTexV"),
            )
        }

        fun uploadPlaneR8(texId: Int, width: Int, height: Int, data: ByteArray) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val buf = ByteBuffer.wrap(data)
            GLES30.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES30.GL_R8,
                width,
                height,
                0,
                GLES30.GL_RED,
                GLES20.GL_UNSIGNED_BYTE,
                buf,
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
