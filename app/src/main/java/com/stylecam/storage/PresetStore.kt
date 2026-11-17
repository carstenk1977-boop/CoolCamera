package com.stylecam.storage

import android.content.Context
import com.stylecam.models.CustomPreset
import com.stylecam.models.FilterParams
import com.stylecam.models.FilterStyle
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Custom user presets, stored locally as JSON in SharedPreferences. */
class PresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("stylecam_presets", Context.MODE_PRIVATE)

    fun all(): List<CustomPreset> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomPreset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    style = FilterStyle.fromId(o.getString("style")),
                    params = FilterParams.fromJson(o.getJSONObject("params"))
                )
            }
        }.getOrDefault(emptyList())
    }

    fun forStyle(style: FilterStyle): List<CustomPreset> = all().filter { it.style == style }

    fun create(name: String, style: FilterStyle, params: FilterParams): CustomPreset {
        val preset = CustomPreset(UUID.randomUUID().toString(), name.trim(), style, params)
        save(all() + preset)
        return preset
    }

    fun rename(id: String, newName: String) =
        save(all().map { if (it.id == id) it.copy(name = newName.trim()) else it })

    /** Replace the stored parameters of a preset with the current ones. */
    fun overwrite(id: String, params: FilterParams) =
        save(all().map { if (it.id == id) it.copy(params = params) else it })

    fun delete(id: String) = save(all().filterNot { it.id == id })

    private fun save(list: List<CustomPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("style", p.style.id)
                put("params", p.params.toJson())
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "custom_presets"
    }
}
