package com.stylecam.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny helpers for building the dynamic parts of the UI in code. */
object UiKit {
    const val AMBER = 0xFFFFC93C.toInt()

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    /** Pill-shaped chip; selected chips are filled amber. */
    fun chip(ctx: Context, text: String, selected: Boolean, textSp: Float = 12f): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = textSp
        tv.gravity = Gravity.CENTER
        tv.letterSpacing = 0.08f
        tv.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(ctx, 8)
        tv.layoutParams = lp
        styleChip(tv, selected)
        return tv
    }

    fun styleChip(tv: TextView, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(tv.context, 20).toFloat()
        if (selected) {
            bg.setColor(AMBER)
            tv.setTextColor(Color.parseColor("#1A1A1A"))
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            bg.setColor(Color.parseColor("#66000000"))
            bg.setStroke(dp(tv.context, 1), Color.parseColor("#44FFFFFF"))
            tv.setTextColor(Color.WHITE)
            tv.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        tv.background = bg
    }
}
