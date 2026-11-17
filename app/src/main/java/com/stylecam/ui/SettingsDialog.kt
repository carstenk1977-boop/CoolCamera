package com.stylecam.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.stylecam.models.Quality
import com.stylecam.storage.SettingsStore

object SettingsDialog {
    fun show(ctx: Context, settings: SettingsStore, onChanged: () -> Unit) {
        val pad = UiKit.dp(ctx, 20)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun header(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 11f
            letterSpacing = 0.12f
            setPadding(0, UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 4))
        }

        fun switch(text: String, value: Boolean, set: (Boolean) -> Unit) = SwitchCompat(ctx).apply {
            this.text = text
            isChecked = value
            setOnCheckedChangeListener { _, checked -> set(checked); onChanged() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 6))
        }

        root.addView(header("PROCESSING RESOLUTION (LIVE VIEW)"))
        val group = RadioGroup(ctx)
        val labels = mapOf(
            Quality.LOW to "Low (fastest, coolest)",
            Quality.MEDIUM to "Medium",
            Quality.HIGH to "High (best quality)",
            Quality.AUTO to "Auto (adapts to frame rate)"
        )
        Quality.entries.forEach { q ->
            val rb = RadioButton(ctx).apply { text = labels[q]; id = q.ordinal + 1000 }
            group.addView(rb)
            if (q == settings.quality) group.check(rb.id)
        }
        group.setOnCheckedChangeListener { _, id ->
            Quality.entries.firstOrNull { it.ordinal + 1000 == id }?.let { settings.quality = it; onChanged() }
        }
        root.addView(group)

        root.addView(header("PHOTOS"))
        root.addView(switch("Also save the original photo", settings.saveOriginal) { settings.saveOriginal = it })
        root.addView(switch("Mirror front-camera photos", settings.mirrorFrontPhotos) { settings.mirrorFrontPhotos = it })
        root.addView(switch("Crop photos to what the screen shows", settings.cropToPreview) { settings.cropToPreview = it })

        root.addView(header("DEBUG"))
        root.addView(switch("Show FPS / processing info", settings.showFps) { settings.showFps = it })

        AlertDialog.Builder(ctx)
            .setTitle("Settings")
            .setView(root)
            .setPositiveButton("Done", null)
            .show()
    }
}
