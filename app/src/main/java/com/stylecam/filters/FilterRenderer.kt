package com.stylecam.filters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.stylecam.models.FilterParams
import com.stylecam.models.Quality
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Owns the whole GPU pipeline. Per frame (all at the *processing* resolution, which is
 * independent of the displayed size, so the on-screen camera area never shrinks):
 *
 *   raw RGBA camera buffer (or imported bitmap)
 *     pass 0  camera.frag    rotate / mirror / aspect-crop        -> base FBO
 *     pass 1  smooth.frag    Kuwahara edge-preserving smoothing   -> smooth FBO
 *     pass 2  <style>.frag   style specific processing            -> filtered FBO
 *     pass 3  composite.frag original / filtered / split          -> screen (bilinear upscale)
 *
 * The exact same passes run offscreen at full resolution for photos (uPhoto = 1).
 * Threading: [submitFrame] is called from the CameraX analysis thread; everything else that
 * touches GL runs on the GL thread. Shared state is guarded by [lock].
 */
class FilterRenderer(private val context: Context) : GLSurfaceView.Renderer {

    interface StatsListener {
        fun onStats(fps: Int, gpuMs: Float, width: Int, height: Int, levelName: String)
    }

    // Wired up by StyleCamGLView
    var statsListener: StatsListener? = null
    var requestRender: () -> Unit = {}
    var glExecutor: (Runnable) -> Unit = { it.run() }

    @Volatile
    var maxTextureSize = 2048
        private set

    private class Snapshot(
        val proc: FilterProcessor,
        val params: FilterParams,
        val mode: Int,
        val split: Float,
        val quality: Quality,
        val isImage: Boolean,
        val rot: Int,
        val mirror: Boolean
    )

    private val lock = Any()

    // ---- state shared with other threads (guarded by lock) ----
    private var processor: FilterProcessor = OriginalFilter
    private var params = FilterParams()
    private var holdOriginal = false
    private var splitEnabled = false
    private var splitPos = 0.5f
    private var quality = Quality.AUTO
    private var frameBuf: ByteBuffer? = null
    private var frameW = 0
    private var frameH = 0
    private var frameRowLen = 0
    private var frameRot = 0
    private var frameMirror = false
    private var frameDirty = false
    private var imageBitmap: Bitmap? = null
    private var imageDirty = false

    // ---- GL-thread-only state ----
    private var viewW = 1
    private var viewH = 1
    private var camProgram: GlProgram? = null
    private var compProgram: GlProgram? = null
    private val styleCache = HashMap<String, GlProgram>()
    private val failedShaders = HashSet<String>()
    private var vertexSource = ""
    private var commonSource = ""
    private var vbo = 0
    private var rawTex = 0
    private var rawW = 0
    private var rawH = 0
    private var liveBase: Fbo? = null
    private var liveSmooth: Fbo? = null
    private var liveFiltered: Fbo? = null

    // stats / AUTO quality
    private var autoLevel = 1
    private var slowCount = 0
    private var fastCount = 0
    private var frameCounter = 0L
    private var statFrames = 0
    private var statStart = 0L
    private var lastGpuMs = 0f
    private var lastW = 0
    private var lastH = 0

    // -------------------------------------------------------------------------------------
    // API used from the UI / camera threads
    // -------------------------------------------------------------------------------------

    fun setFilter(proc: FilterProcessor, p: FilterParams) {
        synchronized(lock) { processor = proc; params = p }
        requestRender()
    }

    fun setHoldOriginal(hold: Boolean) {
        synchronized(lock) { holdOriginal = hold }
        requestRender()
    }

    fun setSplit(enabled: Boolean, pos: Float) {
        synchronized(lock) { splitEnabled = enabled; splitPos = pos }
        requestRender()
    }

    fun setQuality(q: Quality) {
        synchronized(lock) { quality = q }
        requestRender()
    }

    /** Show a still image (gallery import) instead of the camera; null returns to the camera. */
    fun setImage(bitmap: Bitmap?) {
        synchronized(lock) {
            imageBitmap = bitmap
            imageDirty = bitmap != null
        }
        requestRender()
    }

    /**
     * Called on the CameraX analysis thread with an RGBA_8888 frame. The pixels are copied into
     * a reusable direct buffer so the ImageProxy can be closed immediately; with
     * STRATEGY_KEEP_ONLY_LATEST a slow GPU therefore never builds up latency.
     */
    fun submitFrame(image: ImageProxy, mirror: Boolean) {
        val plane = image.planes[0]
        val src = plane.buffer
        synchronized(lock) {
            if (imageBitmap != null) return
            src.rewind()
            val need = src.remaining()
            val buf: ByteBuffer = frameBuf?.takeIf { it.capacity() >= need }
                ?: ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder()).also { frameBuf = it }
            buf.clear()
            buf.put(src)
            buf.position(0)
            frameW = image.width
            frameH = image.height
            frameRowLen = plane.rowStride / 4
            frameRot = image.imageInfo.rotationDegrees
            frameMirror = mirror
            frameDirty = true
        }
        requestRender()
    }

    /**
     * High-quality still processing (shutter / import export). Runs the same passes as the live
     * view at the bitmap's resolution with uPhoto = 1. [outAspect] > 0 crops the result to that
     * aspect ratio (centre crop), <= 0 keeps the full frame. The callback runs on the GL thread.
     */
    fun processBitmap(
        src: Bitmap,
        proc: FilterProcessor,
        p: FilterParams,
        outAspect: Float,
        callback: (Bitmap?) -> Unit
    ) {
        glExecutor(Runnable {
            val result = try {
                renderBitmapNow(src, proc, p, outAspect)
            } catch (t: Throwable) {
                Log.e(TAG, "Photo render failed", t)
                null
            }
            callback(result)
        })
    }

    // -------------------------------------------------------------------------------------
    // GLSurfaceView.Renderer
    // -------------------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // The GL context is new: every GL object from a previous context is gone.
        styleCache.clear()
        failedShaders.clear()
        liveBase = null; liveSmooth = null; liveFiltered = null
        rawW = 0; rawH = 0

        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        maxTextureSize = v[0]

        vertexSource = GlUtils.HEADER + GlUtils.readAsset(context, "shaders/vertex.glsl")
        commonSource = GlUtils.readAsset(context, "shaders/common.glsl")
        camProgram = GlUtils.buildProgram(
            vertexSource, GlUtils.HEADER + GlUtils.readAsset(context, "shaders/camera.frag"), "camera"
        )
        compProgram = GlUtils.buildProgram(
            vertexSource, GlUtils.HEADER + GlUtils.readAsset(context, "shaders/composite.frag"), "composite"
        )

        // Full-screen quad (triangle strip)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(quad).position(0)
        val b = IntArray(1)
        GLES30.glGenBuffers(1, b, 0)
        vbo = b[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(0)

        rawTex = GlUtils.newTexture()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        // Re-upload whatever source we had before the context was lost
        synchronized(lock) {
            if (frameBuf != null) frameDirty = true
            if (imageBitmap != null) imageDirty = true
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = max(1, width)
        viewH = max(1, height)
        releaseLiveFbos()
    }

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()

        // 1. Snapshot shared state and upload any new source pixels.
        val s = synchronized(lock) {
            val bmp = imageBitmap
            val isImage = bmp != null
            if (bmp != null) {
                if (imageDirty) { uploadBitmap(bmp); imageDirty = false }
            } else if (frameDirty) {
                uploadFrame(); frameDirty = false
            }
            Snapshot(
                proc = processor, params = params,
                mode = if (holdOriginal) MODE_ORIGINAL else if (splitEnabled) MODE_SPLIT else MODE_FILTERED,
                split = splitPos, quality = quality, isImage = isImage,
                rot = if (isImage) 0 else frameRot, mirror = if (isImage) false else frameMirror
            )
        }

        if (rawW == 0 || rawH == 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, viewW, viewH)
            GLES30.glClearColor(0.04f, 0.04f, 0.05f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }

        // 2. Geometry. The camera fills the view; an imported photo is letter-boxed (never cropped).
        val upright90 = s.rot == 90 || s.rot == 270
        val imgAspect = if (upright90) rawH.toFloat() / rawW else rawW.toFloat() / rawH
        val viewAspect = viewW.toFloat() / viewH
        val bufAspect = if (s.isImage) imgAspect else viewAspect
        val level = when (s.quality) {
            Quality.LOW -> 0
            Quality.MEDIUM -> 1
            Quality.HIGH -> 2
            Quality.AUTO -> autoLevel
        }
        val capW = if (s.isImage) rawW else viewW
        val capH = if (s.isImage) rawH else viewH
        val (pw, ph) = processingSize(LEVEL_LONG_SIDE[level], bufAspect, capW, capH)
        lastW = pw; lastH = ph

        val base = ensureLive(pw, ph)

        // 3. Passes
        val needFilter = s.proc.fragmentAsset != null && s.mode != MODE_ORIGINAL
        val result = runPipeline(
            s.proc, s.params, rawTex, s.rot, s.mirror, bufAspect, imgAspect,
            photo = false, base = base, smooth = liveSmooth!!, out = liveFiltered!!, needFilter = needFilter
        )
        composite(base.texture, result.texture, s.mode, s.split, if (s.isImage) imgAspect else 0f)

        // 4. Stats + AUTO quality
        frameCounter++
        statFrames++
        if (frameCounter % 10L == 0L) {
            GLES30.glFinish()   // measures real GPU time for this frame (only every 10th frame)
            lastGpuMs = (System.nanoTime() - t0) / 1_000_000f
            if (s.quality == Quality.AUTO) adaptAutoLevel(lastGpuMs)
        }
        val now = SystemClock.elapsedRealtime()
        if (statStart == 0L) statStart = now
        if (now - statStart >= 1000) {
            val fps = (statFrames * 1000L / (now - statStart)).toInt()
            val name = if (s.quality == Quality.AUTO) "AUTO/" + LEVEL_NAMES[autoLevel] else s.quality.name
            statsListener?.onStats(fps, lastGpuMs, lastW, lastH, name)
            statFrames = 0
            statStart = now
        }
    }

    // -------------------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------------------

    private fun runPipeline(
        proc: FilterProcessor, p: FilterParams, raw: Int, rot: Int, mirror: Boolean,
        viewAspect: Float, imgAspect: Float, photo: Boolean,
        base: Fbo, smooth: Fbo, out: Fbo, needFilter: Boolean
    ): Fbo {
        val cam = camProgram!!

        // pass 0: source -> upright, mirrored, cropped base
        base.bind()
        cam.use()
        bindTexture(0, raw)
        GLES30.glUniform1i(cam.loc("uRaw"), 0)
        GLES30.glUniform1i(cam.loc("uRot"), rot)
        GLES30.glUniform1f(cam.loc("uMirror"), if (mirror) 1f else 0f)
        GLES30.glUniform1f(cam.loc("uViewAspect"), viewAspect)
        GLES30.glUniform1f(cam.loc("uImgAspect"), imgAspect)
        drawQuad()

        val asset = proc.fragmentAsset
        if (!needFilter || asset == null) return base
        val style = styleProgram(asset) ?: return base
        val smoothProg = styleProgram("smooth.frag") ?: return base

        // pass 1: Kuwahara smoothing
        smooth.bind()
        smoothProg.use()
        bindTexture(0, base.texture)
        setCommonUniforms(smoothProg, base.width, base.height, photo)
        GLES30.glUniform1i(smoothProg.loc("uRadius"), proc.smoothingRadius(p, photo))
        drawQuad()

        // pass 2: style
        out.bind()
        style.use()
        bindTexture(0, base.texture)
        bindTexture(1, smooth.texture)
        setCommonUniforms(style, base.width, base.height, photo)
        for ((key, uniform) in com.stylecam.models.P.UNIFORMS) {
            GLES30.glUniform1f(style.loc(uniform), p[key])
        }
        GLES30.glUniform1i(style.loc("uPalette"), p.palette)
        proc.bindExtras(style, p)
        drawQuad()
        return out
    }

    private fun setCommonUniforms(prog: GlProgram, w: Int, h: Int, photo: Boolean) {
        GLES30.glUniform1i(prog.loc("uBase"), 0)
        GLES30.glUniform1i(prog.loc("uSmooth"), 1)
        GLES30.glUniform2f(prog.loc("uTexel"), 1f / w, 1f / h)
        // Kernel scale: keeps the "painted" look identical whatever the processing resolution.
        GLES30.glUniform1f(prog.loc("uStep"), (min(w, h) / 540f).coerceIn(0.75f, 8f))
        GLES30.glUniform1f(prog.loc("uPhoto"), if (photo) 1f else 0f)
        GLES30.glUniform1f(prog.loc("uSeed"), (frameCounter % 997L) * 0.0137f)
    }

    /** Draw to the screen. [letterboxAspect] > 0 fits that aspect inside the view (photo import). */
    private fun composite(orig: Int, filtered: Int, mode: Int, split: Float, letterboxAspect: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        GLES30.glClearColor(0.04f, 0.04f, 0.05f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        var x = 0; var y = 0; var w = viewW; var h = viewH
        if (letterboxAspect > 0f) {
            val viewAspect = viewW.toFloat() / viewH
            if (letterboxAspect > viewAspect) {
                h = (viewW / letterboxAspect).roundToInt()
                y = (viewH - h) / 2
            } else {
                w = (viewH * letterboxAspect).roundToInt()
                x = (viewW - w) / 2
            }
        }
        GLES30.glViewport(x, y, w, h)

        val p = compProgram!!
        p.use()
        bindTexture(0, orig)
        bindTexture(1, filtered)
        GLES30.glUniform1i(p.loc("uOrig"), 0)
        GLES30.glUniform1i(p.loc("uFilt"), 1)
        GLES30.glUniform1i(p.loc("uMode"), mode)
        GLES30.glUniform1f(p.loc("uSplit"), split)
        GLES30.glUniform1f(p.loc("uViewW"), viewW.toFloat())
        drawQuad()
    }

    private fun renderBitmapNow(src: Bitmap, proc: FilterProcessor, p: FilterParams, outAspect: Float): Bitmap? {
        val imgAspect = src.width.toFloat() / src.height
        val viewAspect = if (outAspect > 0f) outAspect else imgAspect
        val ow: Int
        val oh: Int
        if (imgAspect > viewAspect) {
            oh = src.height; ow = max(2, (oh * viewAspect).roundToInt())
        } else {
            ow = src.width; oh = max(2, (ow / viewAspect).roundToInt())
        }

        val tex = GlUtils.newTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, src, 0)
        val base = Fbo(ow, oh)
        val smooth = Fbo(ow, oh)
        val filtered = Fbo(ow, oh)
        try {
            val needFilter = proc.fragmentAsset != null
            val result = runPipeline(
                proc, p, tex, 0, false, viewAspect, imgAspect,
                photo = true, base = base, smooth = smooth, out = filtered, needFilter = needFilter
            )
            val buf = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder())
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, result.framebuffer)
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4)
            GLES30.glReadPixels(0, 0, ow, oh, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            val raw = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            buf.position(0)
            raw.copyPixelsFromBuffer(buf)
            // glReadPixels returns the bottom row first
            val flip = Matrix().apply { preScale(1f, -1f) }
            val out = Bitmap.createBitmap(raw, 0, 0, ow, oh, flip, false)
            if (out !== raw) raw.recycle()
            return out
        } finally {
            base.release(); smooth.release(); filtered.release()
            GlUtils.deleteTexture(tex)
        }
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private fun uploadFrame() {
        val buf = frameBuf ?: return
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rawTex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, frameRowLen)
        buf.position(0)
        if (rawW != frameW || rawH != frameH) {
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, frameW, frameH, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
            )
            rawW = frameW; rawH = frameH
        } else {
            GLES30.glTexSubImage2D(
                GLES30.GL_TEXTURE_2D, 0, 0, 0, frameW, frameH,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
            )
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)
    }

    private fun uploadBitmap(bmp: Bitmap) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rawTex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        rawW = bmp.width
        rawH = bmp.height
    }

    private fun styleProgram(asset: String): GlProgram? {
        styleCache[asset]?.let { return it }
        if (asset in failedShaders) return null
        return try {
            val frag = GlUtils.HEADER + commonSource + GlUtils.readAsset(context, "shaders/$asset")
            GlUtils.buildProgram(vertexSource, frag, asset).also { styleCache[asset] = it }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not build $asset - falling back to unfiltered", t)
            failedShaders.add(asset)
            null
        }
    }

    private fun ensureLive(w: Int, h: Int): Fbo {
        val b = liveBase
        if (b == null || b.width != w || b.height != h) {
            releaseLiveFbos()
            liveBase = Fbo(w, h)
            liveSmooth = Fbo(w, h)
            liveFiltered = Fbo(w, h)
        }
        return liveBase!!
    }

    private fun releaseLiveFbos() {
        liveBase?.release(); liveSmooth?.release(); liveFiltered?.release()
        liveBase = null; liveSmooth = null; liveFiltered = null
    }

    /** Processing buffer size: [longSide] px on the long edge at [aspect], never above the cap. */
    private fun processingSize(longSide: Int, aspect: Float, capW: Int, capH: Int): Pair<Int, Int> {
        var w: Float
        var h: Float
        if (aspect >= 1f) { w = longSide.toFloat(); h = w / aspect } else { h = longSide.toFloat(); w = h * aspect }
        val scale = min(1f, min(capW / w, capH / h))
        w *= scale; h *= scale
        return max(16, w.roundToInt()) to max(16, h.roundToInt())
    }

    /** AUTO: step the processing resolution down quickly, and back up only after sustained headroom. */
    private fun adaptAutoLevel(gpuMs: Float) {
        if (gpuMs > 30f) { slowCount++; fastCount = 0 } else if (gpuMs < 14f) { fastCount++; slowCount = 0 } else { slowCount = 0; fastCount = 0 }
        if (slowCount >= 3 && autoLevel > 0) { autoLevel--; slowCount = 0 }
        if (fastCount >= 12 && autoLevel < 2) { autoLevel++; fastCount = 0 }
    }

    private fun bindTexture(unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    }

    private fun drawQuad() {
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    companion object {
        private const val TAG = "FilterRenderer"
        private const val MODE_FILTERED = 0
        private const val MODE_ORIGINAL = 1
        private const val MODE_SPLIT = 2
        private val LEVEL_LONG_SIDE = intArrayOf(640, 960, 1280)
        private val LEVEL_NAMES = arrayOf("LOW", "MEDIUM", "HIGH")
    }
}
