package com.stylecam.models

/** The five entries of the style carousel. [id] is what is persisted. */
enum class FilterStyle(val id: String, val label: String) {
    ORIGINAL("original", "ORIGINAL"),
    RAILWAY("railway", "RAILWAY"),
    CEL("cel", "CEL"),
    GRAPHIC("graphic", "GRAPHIC"),
    IDLEON("idleon", "IDLEON");

    companion object {
        fun fromId(id: String?): FilterStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}
