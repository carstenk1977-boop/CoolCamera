package com.stylecam.filters

import android.opengl.GLES30
import com.stylecam.models.FilterParams
import com.stylecam.models.FilterStyle
import com.stylecam.models.P
import com.stylecam.models.Preset
import kotlin.math.roundToInt
import kotlin.random.Random

/** One slider in the adjustment panel. */
data class ControlSpec(val key: String, val label: String)

/**
 * Common abstraction every artistic style implements. The renderer owns the GL plumbing
 * (camera pass, Kuwahara smoothing pass, compositing); a processor only supplies:
 *   - which fragment shader performs the style pass,
 *   - its sliders and presets,
 *   - how wide the smoothing kernel should be,
 *   - any extra uniforms (e.g. palette colours).
 * To add a new style: write assets/shaders/<name>.frag, subclass [BaseProcessor], register it
 * in [FilterRegistry] and add a [FilterStyle] entry.
 */
interface FilterProcessor {
    val style: FilterStyle
    val title: String

    /** Asset file name of the style pass, or null for a pass-through (ORIGINAL). */
    val fragmentAsset: String?

    val controls: List<ControlSpec>
    val presets: List<Preset>
    val defaultParams: FilterParams

    /** Optional chip row in the adjust panel (Railway palettes, IdleOn sub-modes). */
    val optionLabel: String
    val options: List<String>
    fun selectedOption(params: FilterParams): Int
    fun applyOption(params: FilterParams, index: Int): FilterParams

    /** Kuwahara kernel radius in samples for the current parameters. */
    fun smoothingRadius(params: FilterParams, photo: Boolean): Int

    /** Bind uniforms that are specific to this style. The program is already in use. */
    fun bindExtras(program: GlProgram, params: FilterParams)

    /** A sensible variation of this style (never uniformly random parameters). */
    fun randomise(rng: Random): FilterParams
}

abstract class BaseProcessor : FilterProcessor {
    override val defaultParams: FilterParams get() = presets.first().params
    override val optionLabel: String get() = ""
    override val options: List<String> get() = emptyList()
    override fun selectedOption(params: FilterParams): Int = 0
    override fun applyOption(params: FilterParams, index: Int): FilterParams = params
    override fun bindExtras(program: GlProgram, params: FilterParams) {}

    protected open fun effectiveSmoothing(p: FilterParams): Float = p[P.SMOOTHING]

    override fun smoothingRadius(params: FilterParams, photo: Boolean): Int {
        val s = effectiveSmoothing(params).coerceIn(0f, 1f)
        return if (photo) (2f + 4f * s).roundToInt() else (1f + 2f * s).roundToInt()
    }

    protected open fun postRandomise(p: FilterParams, rng: Random): FilterParams = p

    /**
     * Start from a random BUILT-IN preset (so the combination is coherent), then jitter every
     * slider a little. Strength stays high, neutral-centred sliders stay in a tame range, and
     * the pixel amount is never randomised (it defines Smooth vs Pixel mode).
     */
    override fun randomise(rng: Random): FilterParams {
        var out = presets[rng.nextInt(presets.size)].params
        for (c in controls) {
            val base = out[c.key]
            val v = when {
                c.key == P.PIXEL -> base
                c.key == P.STRENGTH -> (base + (rng.nextFloat() - 0.5f) * 0.2f).coerceIn(0.7f, 1f)
                c.key in P.NEUTRAL_KEYS -> (base + (rng.nextFloat() - 0.5f) * 0.18f).coerceIn(0.3f, 0.72f)
                else -> (base + (rng.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            }
            out = out.with(c.key, v)
        }
        return postRandomise(out, rng)
    }
}

/** No processing at all - shows the camera as is. */
object OriginalFilter : BaseProcessor() {
    override val style = FilterStyle.ORIGINAL
    override val title = "ORIGINAL"
    override val fragmentAsset: String? = null
    override val controls: List<ControlSpec> = emptyList()
    override val presets: List<Preset> = listOf(Preset("Original", FilterParams()))
}

/** STYLE 1 - 1930s British railway poster. */
object RailwayFilter : BaseProcessor() {
    override val style = FilterStyle.RAILWAY
    override val title = "RAILWAY"
    override val fragmentAsset = "railway.frag"
    override val presets = PresetData.RAILWAY
    override val controls = listOf(
        ControlSpec(P.STRENGTH, "Effect Strength"),
        ControlSpec(P.LEVELS, "Colour Levels"),
        ControlSpec(P.SATURATION, "Saturation"),
        ControlSpec(P.CONTRAST, "Contrast"),
        ControlSpec(P.BRIGHTNESS, "Brightness"),
        ControlSpec(P.SHADOW, "Shadow Depth"),
        ControlSpec(P.HIGHLIGHT, "Highlight Strength"),
        ControlSpec(P.EDGE, "Edge Strength"),
        ControlSpec(P.THICKNESS, "Edge Thickness"),
        ControlSpec(P.SMOOTHING, "Smoothing"),
        ControlSpec(P.GRAIN, "Paper / Print Grain"),
        ControlSpec(P.WARMTH, "Warmth"),
        ControlSpec(P.VIGNETTE, "Vignette")
    )

    private val paletteNames = listOf("Railway Classic", "Seaside", "Countryside", "Industrial", "Night Travel")
    private val palettes: List<FloatArray> = PresetData.RAILWAY_PALETTES.map { pal ->
        FloatArray(24).also { out ->
            for (i in 0 until 8) {
                val c = pal[i]
                out[i * 3] = ((c shr 16) and 0xFF) / 255f
                out[i * 3 + 1] = ((c shr 8) and 0xFF) / 255f
                out[i * 3 + 2] = (c and 0xFF) / 255f
            }
        }
    }

    override val optionLabel = "PALETTE"
    override val options = paletteNames
    override fun selectedOption(params: FilterParams) = params.palette.coerceIn(0, palettes.size - 1)
    override fun applyOption(params: FilterParams, index: Int) = params.copy(palette = index)

    override fun bindExtras(program: GlProgram, params: FilterParams) {
        val pal = palettes[selectedOption(params)]
        GLES30.glUniform3fv(program.loc("uPal"), 8, pal, 0)
        GLES30.glUniform1i(program.loc("uPalCount"), 8)
    }

    override fun postRandomise(p: FilterParams, rng: Random): FilterParams =
        p.copy(palette = rng.nextInt(palettes.size))
}

/** STYLE 2 - cel shading. */
object CelFilter : BaseProcessor() {
    override val style = FilterStyle.CEL
    override val title = "CEL"
    override val fragmentAsset = "cel.frag"
    override val presets = PresetData.CEL
    override val controls = listOf(
        ControlSpec(P.STRENGTH, "Effect Strength"),
        ControlSpec(P.LEVELS, "Colour Levels"),
        ControlSpec(P.SHADOW, "Shadow Steps"),
        ControlSpec(P.EDGE, "Outline Strength"),
        ControlSpec(P.THICKNESS, "Outline Width"),
        ControlSpec(P.SATURATION, "Saturation"),
        ControlSpec(P.SMOOTHING, "Smoothing"),
        ControlSpec(P.CONTRAST, "Contrast"),
        ControlSpec(P.BRIGHTNESS, "Brightness"),
        ControlSpec(P.HIGHLIGHT, "Highlight Strength"),
        ControlSpec(P.GRAIN, "Halftone Dots"),
        ControlSpec(P.WARMTH, "Warmth"),
        ControlSpec(P.VIGNETTE, "Vignette")
    )
}

/** STYLE 3 - graphic crime-game poster illustration (labelled GRAPHIC in the app). */
object GraphicFilter : BaseProcessor() {
    override val style = FilterStyle.GRAPHIC
    override val title = "GRAPHIC"
    override val fragmentAsset = "graphic.frag"
    override val presets = PresetData.GRAPHIC
    override val controls = listOf(
        ControlSpec(P.STRENGTH, "Illustration Strength"),
        ControlSpec(P.EDGE, "Outline Strength"),
        ControlSpec(P.THICKNESS, "Outline Width"),
        ControlSpec(P.SHADOW, "Shadow Depth"),
        ControlSpec(P.SATURATION, "Colour Intensity"),
        ControlSpec(P.CONTRAST, "Contrast"),
        ControlSpec(P.SMOOTHING, "Surface Smoothing"),
        ControlSpec(P.LEVELS, "Tonal Levels"),
        ControlSpec(P.HIGHLIGHT, "Highlight Strength"),
        ControlSpec(P.BRIGHTNESS, "Brightness"),
        ControlSpec(P.GRAIN, "Grain"),
        ControlSpec(P.WARMTH, "Warmth"),
        ControlSpec(P.VIGNETTE, "Vignette")
    )
}

/** STYLE 4 - IdleOn-inspired cartoon RPG (SMOOTH and PIXEL sub-modes). */
object IdleOnFilter : BaseProcessor() {
    override val style = FilterStyle.IDLEON
    override val title = "IDLEON"
    override val fragmentAsset = "idleon.frag"
    override val presets = PresetData.IDLEON
    override val controls = listOf(
        ControlSpec(P.STRENGTH, "Cartoon Strength"),
        ControlSpec(P.SATURATION, "Colour Boost"),
        ControlSpec(P.CHUNK, "Chunkiness"),
        ControlSpec(P.EDGE, "Edge Strength"),
        ControlSpec(P.LEVELS, "Shadow Levels"),
        ControlSpec(P.GRADE, "Fantasy Colour Grade"),
        ControlSpec(P.PIXEL, "Pixel Amount"),
        ControlSpec(P.GLOW, "Highlight Glow"),
        ControlSpec(P.CONTRAST, "Contrast"),
        ControlSpec(P.BRIGHTNESS, "Brightness"),
        ControlSpec(P.SMOOTHING, "Smoothing"),
        ControlSpec(P.WARMTH, "Warmth"),
        ControlSpec(P.VIGNETTE, "Vignette")
    )

    // Chunkiness widens the smoothing kernel too: bigger, flatter shapes.
    override fun effectiveSmoothing(p: FilterParams): Float =
        (p[P.SMOOTHING] * 0.6f + p[P.CHUNK] * 0.6f).coerceIn(0f, 1f)

    override val optionLabel = "MODE"
    override val options = listOf("SMOOTH", "PIXEL")
    override fun selectedOption(params: FilterParams) = if (params[P.PIXEL] > 0.03f) 1 else 0
    override fun applyOption(params: FilterParams, index: Int): FilterParams =
        if (index == 0) params.with(P.PIXEL, 0f)
        else if (params[P.PIXEL] > 0.03f) params else params.with(P.PIXEL, 0.5f)
}

object FilterRegistry {
    private val all: Map<FilterStyle, FilterProcessor> = listOf(
        OriginalFilter, RailwayFilter, CelFilter, GraphicFilter, IdleOnFilter
    ).associateBy { it.style }

    fun byStyle(style: FilterStyle): FilterProcessor = all.getValue(style)
    val styles: List<FilterStyle> = FilterStyle.entries
}
