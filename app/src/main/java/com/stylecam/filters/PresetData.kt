// GENERATED from presets.json by genpresets.py - the same table drives the offline shader test harness.
package com.stylecam.filters

import com.stylecam.models.FilterParams
import com.stylecam.models.P
import com.stylecam.models.Preset

internal object PresetData {

    val RAILWAY_DEFAULTS: FilterParams = FilterParams(mapOf(P.STRENGTH to 0.9f, P.SATURATION to 0.5f, P.CONTRAST to 0.5f, P.BRIGHTNESS to 0.55f, P.SHADOW to 0.5f, P.HIGHLIGHT to 0.5f, P.LEVELS to 0.45f, P.EDGE to 0.45f, P.THICKNESS to 0.35f, P.SMOOTHING to 0.55f, P.GRAIN to 0.45f, P.WARMTH to 0.5f, P.VIGNETTE to 0.35f))
    val RAILWAY: List<Preset> = listOf(
        Preset("Classic", RAILWAY_DEFAULTS.copy(palette = 0)),
        Preset("Coast", RAILWAY_DEFAULTS.withAll(P.WARMTH to 0.45f, P.SATURATION to 0.55f, P.LEVELS to 0.5f, P.GRAIN to 0.4f, P.EDGE to 0.4f).copy(palette = 1)),
        Preset("Countryside", RAILWAY_DEFAULTS.withAll(P.SMOOTHING to 0.6f, P.WARMTH to 0.55f, P.LEVELS to 0.5f).copy(palette = 2)),
        Preset("Industrial", RAILWAY_DEFAULTS.withAll(P.CONTRAST to 0.6f, P.SHADOW to 0.65f, P.EDGE to 0.55f, P.GRAIN to 0.55f, P.SATURATION to 0.4f, P.VIGNETTE to 0.5f).copy(palette = 3)),
        Preset("Sunset", RAILWAY_DEFAULTS.withAll(P.WARMTH to 0.85f, P.SATURATION to 0.6f, P.HIGHLIGHT to 0.6f, P.CONTRAST to 0.55f, P.VIGNETTE to 0.45f, P.GRAIN to 0.5f).copy(palette = 0))
    )

    val CEL_DEFAULTS: FilterParams = FilterParams(mapOf(P.STRENGTH to 0.95f, P.SATURATION to 0.6f, P.CONTRAST to 0.55f, P.BRIGHTNESS to 0.5f, P.SHADOW to 0.5f, P.HIGHLIGHT to 0.4f, P.LEVELS to 0.5f, P.EDGE to 0.7f, P.THICKNESS to 0.4f, P.SMOOTHING to 0.4f, P.GRAIN to 0.0f, P.WARMTH to 0.5f, P.VIGNETTE to 0.15f))
    val CEL: List<Preset> = listOf(
        Preset("Soft", CEL_DEFAULTS.withAll(P.LEVELS to 0.7f, P.EDGE to 0.35f, P.THICKNESS to 0.3f, P.SMOOTHING to 0.6f, P.SATURATION to 0.5f, P.STRENGTH to 0.85f).copy(palette = 0)),
        Preset("Anime-ish", CEL_DEFAULTS.withAll(P.LEVELS to 0.4f, P.EDGE to 0.6f, P.THICKNESS to 0.3f, P.SATURATION to 0.7f, P.SMOOTHING to 0.5f, P.HIGHLIGHT to 0.6f, P.SHADOW to 0.35f, P.WARMTH to 0.55f).copy(palette = 0)),
        Preset("Bold", CEL_DEFAULTS.withAll(P.LEVELS to 0.25f, P.EDGE to 0.9f, P.THICKNESS to 0.6f, P.SATURATION to 0.7f, P.CONTRAST to 0.6f, P.SMOOTHING to 0.3f).copy(palette = 0)),
        Preset("Comic", CEL_DEFAULTS.withAll(P.LEVELS to 0.15f, P.EDGE to 1.0f, P.THICKNESS to 0.55f, P.SATURATION to 0.7f, P.CONTRAST to 0.62f, P.SMOOTHING to 0.25f, P.GRAIN to 0.6f).copy(palette = 0))
    )

    val GRAPHIC_DEFAULTS: FilterParams = FilterParams(mapOf(P.STRENGTH to 0.95f, P.SATURATION to 0.6f, P.CONTRAST to 0.58f, P.BRIGHTNESS to 0.55f, P.SHADOW to 0.5f, P.HIGHLIGHT to 0.6f, P.LEVELS to 0.3f, P.EDGE to 0.7f, P.THICKNESS to 0.35f, P.SMOOTHING to 0.75f, P.GRAIN to 0.2f, P.WARMTH to 0.5f, P.VIGNETTE to 0.3f))
    val GRAPHIC: List<Preset> = listOf(
        Preset("Natural", GRAPHIC_DEFAULTS.withAll(P.STRENGTH to 0.75f, P.SATURATION to 0.5f, P.CONTRAST to 0.52f, P.SHADOW to 0.4f, P.EDGE to 0.45f, P.VIGNETTE to 0.2f, P.SMOOTHING to 0.6f, P.LEVELS to 0.45f).copy(palette = 0)),
        Preset("Vibrant", GRAPHIC_DEFAULTS.withAll(P.SATURATION to 0.78f, P.CONTRAST to 0.6f, P.EDGE to 0.6f, P.WARMTH to 0.52f).copy(palette = 0)),
        Preset("Dramatic", GRAPHIC_DEFAULTS.withAll(P.CONTRAST to 0.68f, P.SHADOW to 0.65f, P.HIGHLIGHT to 0.75f, P.EDGE to 0.8f, P.VIGNETTE to 0.5f, P.SATURATION to 0.62f, P.LEVELS to 0.25f).copy(palette = 0)),
        Preset("Night", GRAPHIC_DEFAULTS.withAll(P.BRIGHTNESS to 0.45f, P.CONTRAST to 0.66f, P.SHADOW to 0.6f, P.WARMTH to 0.28f, P.SATURATION to 0.68f, P.HIGHLIGHT to 0.8f, P.VIGNETTE to 0.55f, P.EDGE to 0.7f, P.LEVELS to 0.3f).copy(palette = 0))
    )

    val IDLEON_DEFAULTS: FilterParams = FilterParams(mapOf(P.STRENGTH to 0.9f, P.SATURATION to 0.72f, P.CONTRAST to 0.55f, P.BRIGHTNESS to 0.52f, P.SHADOW to 0.5f, P.HIGHLIGHT to 0.6f, P.LEVELS to 0.45f, P.EDGE to 0.65f, P.THICKNESS to 0.35f, P.SMOOTHING to 0.5f, P.GRAIN to 0.0f, P.WARMTH to 0.5f, P.VIGNETTE to 0.15f, P.PIXEL to 0.0f, P.GLOW to 0.45f, P.CHUNK to 0.45f, P.GRADE to 0.5f))
    val IDLEON: List<Preset> = listOf(
        Preset("Smooth", IDLEON_DEFAULTS.copy(palette = 0)),
        Preset("Fantasy", IDLEON_DEFAULTS.withAll(P.GRADE to 1.0f, P.SATURATION to 0.78f, P.GLOW to 0.7f, P.WARMTH to 0.42f, P.SHADOW to 0.55f).copy(palette = 0)),
        Preset("Vivid", IDLEON_DEFAULTS.withAll(P.SATURATION to 0.98f, P.CONTRAST to 0.65f, P.GLOW to 0.25f, P.GRADE to 0.15f, P.EDGE to 0.6f).copy(palette = 0)),
        Preset("Pixel", IDLEON_DEFAULTS.withAll(P.PIXEL to 0.5f, P.CHUNK to 0.55f, P.LEVELS to 0.35f, P.SATURATION to 0.8f, P.EDGE to 0.6f, P.SMOOTHING to 0.35f).copy(palette = 0)),
        Preset("Dungeon", IDLEON_DEFAULTS.withAll(P.BRIGHTNESS to 0.3f, P.CONTRAST to 0.7f, P.GRADE to 1.0f, P.WARMTH to 0.25f, P.SATURATION to 0.45f, P.VIGNETTE to 0.7f, P.GLOW to 0.15f, P.SHADOW to 0.8f, P.LEVELS to 0.4f).copy(palette = 0)),
        Preset("Golden", IDLEON_DEFAULTS.withAll(P.WARMTH to 0.9f, P.GRADE to 0.6f, P.GLOW to 0.9f, P.HIGHLIGHT to 0.8f, P.BRIGHTNESS to 0.58f, P.SATURATION to 0.85f).copy(palette = 0))
    )

    /** Railway poster ink palettes: 8 colours each, 0xRRGGBB. */
    val RAILWAY_PALETTES: List<IntArray> = listOf(
        intArrayOf(0xEDDFB3, 0xCC9933, 0xE6C761, 0xA33D2B, 0x265740, 0x297380, 0x456E99, 0x2B1F1A),
        intArrayOf(0xEFE2BE, 0x33999F, 0x73ADD1, 0x1F3866, 0xE07347, 0xF2CC66, 0x407359, 0x2A2E33),
        intArrayOf(0xE9E3BF, 0x6B9447, 0x244D2E, 0xC79E40, 0x8CB3C7, 0x994733, 0x614429, 0x241D16),
        intArrayOf(0x262628, 0x617385, 0x9E9E99, 0x8C4529, 0xBF9438, 0xE0D1AD, 0x2E473D, 0x853A2B),
        intArrayOf(0x121A38, 0x213361, 0x1F616B, 0xF2C759, 0xD97333, 0xE0D1A6, 0x662633, 0x0F0D12)
    )
}
