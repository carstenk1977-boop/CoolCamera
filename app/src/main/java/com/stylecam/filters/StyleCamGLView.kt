package com.stylecam.filters

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * GLSurfaceView that hosts [FilterRenderer] (OpenGL ES 3.0) and turns touches into
 * tap-to-focus and pinch-to-zoom callbacks.
 */
class StyleCamGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    interface GestureCallbacks {
        fun onTap(x: Float, y: Float)
        fun onPinch(scaleFactor: Float)
    }

    val filterRenderer = FilterRenderer(context.applicationContext)
    var gestures: GestureCallbacks? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                gestures?.onPinch(detector.scaleFactor)
                return true
            }
        })

    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                gestures?.onTap(e.x, e.y)
                return true
            }
        })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(filterRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY   // draw only when a new camera frame / setting arrives
        filterRenderer.requestRender = { requestRender() }
        filterRenderer.glExecutor = { r -> queueEvent(r) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) tapDetector.onTouchEvent(event)
        return true
    }
}
