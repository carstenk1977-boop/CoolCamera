package com.stylecam.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.stylecam.filters.FilterProcessor
import com.stylecam.models.FilterParams

/**
 * The "filter adjustment" sheet: a style option chip row (palettes / modes), one slider per
 * control of the active [FilterProcessor], and RESET / RANDOMISE / SAVE PRESET buttons.
 */
class ControlPanel(
    private val ctx: Context,
    private val container: FrameLayout,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onParamChanged(key: String, value: Float)
        fun onOptionSelected(index: Int)
        fun onReset()
        fun onRandomise()
        fun onSavePreset()
    }

    private class Row(val key: String, val seek: SeekBar, val value: TextView)

    private val rows = ArrayList<Row>()
    private val optionChips = ArrayList<TextView>()
    private var suppress = false
    private var processor: FilterProcessor? = null

    val isShowing: Boolean get() = container.visibility == View.VISIBLE

    fun show() { container.visibility = View.VISIBLE }
    fun hide() { container.visibility = View.GONE }
    fun toggle() { if (isShowing) hide() else show() }

    /** Rebuild the sliders for [proc] and set them to [params]. */
    fun bind(proc: FilterProcessor, params: FilterParams) {
        processor = proc
        container.removeAllViews()
        rows.clear()
        optionChips.clear()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1B1F"))
                cornerRadius = UiKit.dp(ctx, 18).toFloat()
            }
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 10))
        }

        // Option chips (Railway palettes, IdleOn smooth / pixel)
        if (proc.options.isNotEmpty()) {
            val title = TextView(ctx).apply {
                text = proc.optionLabel
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 10f
                letterSpacing = 0.15f
            }
            card.addView(title)
            val scroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 8)) }
            proc.options.forEachIndexed { i, name ->
                val chip = UiKit.chip(ctx, name, false, 11f)
                chip.setOnClickListener { callbacks.onOptionSelected(i) }
                optionChips.add(chip)
                row.addView(chip)
            }
            scroll.addView(row)
            card.addView(scroll)
        }

        // Sliders
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        proc.controls.forEach { spec ->
            val line = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(ctx).apply {
                text = spec.label
                setTextColor(Color.WHITE)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, 128), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val seek = SeekBar(ctx).apply {
                max = 100
                progressTintList = ColorStateList.valueOf(UiKit.AMBER)
                thumbTintList = ColorStateList.valueOf(UiKit.AMBER)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(ctx).apply {
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 11f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, 32), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    value.text = progress.toString()
                    if (fromUser && !suppress) callbacks.onParamChanged(spec.key, progress / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
            line.addView(label); line.addView(seek); line.addView(value)
            list.addView(line)
            rows.add(Row(spec.key, seek, value))
        }
        val scroll = ScrollView(ctx)
        scroll.addView(list)
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 210)))

        // Buttons
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, UiKit.dp(ctx, 6), 0, 0)
        }
        fun btn(text: String, onClick: () -> Unit, accent: Boolean = false): Button = Button(ctx).apply {
            this.text = text
            textSize = 11f
            isAllCaps = true
            setTextColor(if (accent) UiKit.AMBER else Color.WHITE)
            background = null
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(ctx, 44), 1f)
        }
        buttons.addView(btn("Reset", { callbacks.onReset() }))
        buttons.addView(btn("Randomise", { callbacks.onRandomise() }))
        buttons.addView(btn("Save preset", { callbacks.onSavePreset() }, accent = true))
        card.addView(buttons)

        container.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refresh(params)
    }

    /** Move the sliders / chips to [params] without firing callbacks. */
    fun refresh(params: FilterParams) {
        suppress = true
        rows.forEach { r ->
            val p = (params[r.key] * 100f).toInt().coerceIn(0, 100)
            r.seek.progress = p
            r.value.text = p.toString()
        }
        suppress = false
        val sel = processor?.selectedOption(params) ?: 0
        optionChips.forEachIndexed { i, chip -> UiKit.styleChip(chip, i == sel) }
    }
}
