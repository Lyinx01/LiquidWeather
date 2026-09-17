package com.liuli.weather.data.city

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** 内置城市（来自 assets/cities.json）。 */
data class City(
    val name: String,
    val province: String,
    val lat: Double,
    val lng: Double
)

object CityDataSource {

    private var cache: List<City>? = null

    fun load(context: Context): List<City> {
        cache?.let { return it }
        return try {
            context.assets.open("cities.json").use { input ->
                val reader = InputStreamReader(input, StandardCharsets.UTF_8)
                val type = object : TypeToken<List<City>>() {}.type
                Gson().fromJson<List<City>>(reader, type) ?: emptyList()
            }.also { cache = it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun search(context: Context, keyword: String): List<City> {
        val all = load(context)
        if (keyword.isBlank()) return all
        val lower = keyword.trim().lowercase()
        return all.filter {
            it.name.contains(lower, ignoreCase = true) ||
                it.province.contains(lower, ignoreCase = true) ||
                (it.name.lowercase().contains(lower))
        }
    }
}
