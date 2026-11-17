package com.stylecam.models

import org.json.JSONObject

/** Keys of every adjustable parameter. All values are normalised to 0..1. */
object P {
    const val STRENGTH = "strength"
    const val SATURATION = "saturation"
    const val CONTRAST = "contrast"
    const val BRIGHTNESS = "brightness"
    const val SHADOW = "shadow"
    const val HIGHLIGHT = "highlight"
    const val LEVELS = "levels"
    const val EDGE = "edge"
    const val THICKNESS = "thickness"
    const val SMOOTHING = "smoothing"
    const val GRAIN = "grain"
    const val WARMTH = "warmth"
    const val VIGNETTE = "vignette"
    const val PIXEL = "pixel"
    const val GLOW = "glow"
    const val CHUNK = "chunk"
    const val GRADE = "grade"

    /** Parameter key -> GLSL uniform name (see shaders/common.glsl). */
    val UNIFORMS: Map<String, String> = linkedMapOf(
        STRENGTH to "uStrength", SATURATION to "uSaturation", CONTRAST to "uContrast",
        BRIGHTNESS to "uBrightness", SHADOW to "uShadow", HIGHLIGHT to "uHighlight",
        LEVELS to "uLevels", EDGE to "uEdge", THICKNESS to "uThickness",
        SMOOTHING to "uSmoothing", GRAIN to "uGrain", WARMTH to "uWarmth",
        VIGNETTE to "uVignette", PIXEL to "uPixel", GLOW to "uGlow",
        CHUNK to "uChunk", GRADE to "uGrade"
    )

    /** Parameters where 0.5 means "unchanged"; randomisation keeps these in a tame range. */
    val NEUTRAL_KEYS: Set<String> = setOf(SATURATION, CONTRAST, BRIGHTNESS, WARMTH)
}

/**
 * Immutable parameter set for one filter. Missing keys read as 0 so a shader never
 * receives garbage. [palette] selects a colour palette (Railway) and is otherwise ignored.
 */
data class FilterParams(
    val values: Map<String, Float> = emptyMap(),
    val palette: Int = 0
) {
    operator fun get(key: String): Float = values[key] ?: 0f

    fun with(key: String, value: Float): FilterParams =
        copy(values = values + (key to value.coerceIn(0f, 1f)))

    fun withAll(vararg kv: Pair<String, Float>): FilterParams =
        copy(values = values + kv.associate { it.first to it.second.coerceIn(0f, 1f) })

    fun toJson(): JSONObject = JSONObject().apply {
        put("palette", palette)
        put("values", JSONObject().also { o -> values.forEach { (k, v) -> o.put(k, v.toDouble()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): FilterParams {
            val v = o.optJSONObject("values")
            val map = HashMap<String, Float>()
            v?.keys()?.forEach { k -> map[k] = v.optDouble(k, 0.0).toFloat() }
            return FilterParams(map, o.optInt("palette", 0))
        }
    }
}
