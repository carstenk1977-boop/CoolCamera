package com.stylecam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * ORIGINAL | FILTERED comparison handle drawn over the GL view. The actual image split is done
 * in composite.frag; this view only draws the divider and reports the dragged position (0..1).
 */
class SplitOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var position = 0.5f
        private set
    var onPositionChanged: ((Float) -> Unit)? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = UiKit.dp(context, 2).toFloat() }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(8f, 0f, 2f, 0x88000000.toInt()) }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#222222"); strokeWidth = UiKit.dp(context, 2).toFloat(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = UiKit.dp(context, 11).toFloat(); typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f; setShadowLayer(6f, 0f, 1f, Color.BLACK)
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun reset() { position = 0.5f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val x = position * width
        canvas.drawLine(x, 0f, x, height.toFloat(), line)
        val cy = height * 0.45f
        val r = UiKit.dp(context, 20).toFloat()
        canvas.drawCircle(x, cy, r, knob)
        val a = UiKit.dp(context, 6).toFloat()
        canvas.drawLines(floatArrayOf(x - a - 2, cy, x - 2, cy - a, x - a - 2, cy, x - 2, cy + a), arrow)
        canvas.drawLines(floatArrayOf(x + a + 2, cy, x + 2, cy - a, x + a + 2, cy, x + 2, cy + a), arrow)

        val top = UiKit.dp(context, 110).toFloat()
        label.textAlign = Paint.Align.LEFT
        if (x > UiKit.dp(context, 110)) canvas.drawText("ORIGINAL", UiKit.dp(context, 14).toFloat(), top, label)
        label.textAlign = Paint.Align.RIGHT
        if (width - x > UiKit.dp(context, 110)) canvas.drawText("FILTERED", width - UiKit.dp(context, 14).toFloat(), top, label)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                position = (event.x / width).coerceIn(0.02f, 0.98f)
                onPositionChanged?.invoke(position)
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
