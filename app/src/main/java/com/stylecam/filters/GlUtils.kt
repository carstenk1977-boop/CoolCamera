package com.stylecam.filters

import android.content.Context
import android.opengl.GLES30
import android.util.Log

/** A linked GL program with a cached uniform-location lookup. */
class GlProgram(val id: Int) {
    private val locations = HashMap<String, Int>()

    /** Returns -1 if the uniform does not exist / was optimised out; glUniform* ignores -1. */
    fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun use() = GLES30.glUseProgram(id)

    fun release() = GLES30.glDeleteProgram(id)
}

/** A colour render target (RGBA8 texture + framebuffer). Stored GL-native (row 0 = bottom). */
class Fbo(val width: Int, val height: Int) {
    val texture: Int
    val framebuffer: Int

    init {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        texture = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GlUtils.setLinearClamp()

        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        framebuffer = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer incomplete (0x${Integer.toHexString(status)}) ${width}x$height")
        }
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

object GlUtils {
    private const val TAG = "GlUtils"

    /** Prepended to every shader. All shaders are GLSL ES 3.00. */
    const val HEADER =
        "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"

    fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun compile(type: Int, source: String, label: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            Log.e(TAG, "Shader compile failed ($label): $log")
            throw RuntimeException("Shader compile failed ($label): $log")
        }
        return shader
    }

    fun buildProgram(vertexSource: String, fragmentSource: String, label: String): GlProgram {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource, "$label.vert")
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource, "$label.frag")
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Program link failed ($label): $log")
        }
        return GlProgram(program)
    }

    /** Applies LINEAR filtering + CLAMP_TO_EDGE to the currently bound 2D texture. */
    fun setLinearClamp() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun newTexture(): Int {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        setLinearClamp()
        return t[0]
    }

    fun deleteTexture(tex: Int) {
        GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
    }
}
