package com.stylecam.storage

import android.content.Context
import android.content.SharedPreferences
import com.stylecam.models.FilterStyle
import com.stylecam.models.Quality

/** Small typed wrapper around SharedPreferences for app settings. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stylecam_settings", Context.MODE_PRIVATE)

    var showFps: Boolean
        get() = prefs.getBoolean("show_fps", false)
        set(v) = prefs.edit().putBoolean("show_fps", v).apply()

    var quality: Quality
        get() = runCatching { Quality.valueOf(prefs.getString("quality", "AUTO")!!) }.getOrDefault(Quality.AUTO)
        set(v) = prefs.edit().putString("quality", v.name).apply()

    /** Also save the unfiltered photo next to the filtered one. */
    var saveOriginal: Boolean
        get() = prefs.getBoolean("save_original", false)
        set(v) = prefs.edit().putBoolean("save_original", v).apply()

    /** Saved front-camera photos match the (mirrored) viewfinder. */
    var mirrorFrontPhotos: Boolean
        get() = prefs.getBoolean("mirror_front", true)
        set(v) = prefs.edit().putBoolean("mirror_front", v).apply()

    /** Crop saved photos to exactly what the screen shows instead of the full 16:9 frame. */
    var cropToPreview: Boolean
        get() = prefs.getBoolean("crop_preview", false)
        set(v) = prefs.edit().putBoolean("crop_preview", v).apply()

    var lastStyle: FilterStyle
        get() = FilterStyle.fromId(prefs.getString("last_style", FilterStyle.RAILWAY.id))
        set(v) = prefs.edit().putString("last_style", v.id).apply()
}
