package com.stylecam.models

/** A built-in preset: just a name for a processing configuration. */
data class Preset(val name: String, val params: FilterParams)

/** A user-saved preset, stored locally (see storage/PresetStore). */
data class CustomPreset(
    val id: String,
    val name: String,
    val style: FilterStyle,
    val params: FilterParams
)

/** Live-processing resolution tiers. AUTO adapts between LOW..HIGH from measured GPU time. */
enum class Quality { LOW, MEDIUM, HIGH, AUTO }
