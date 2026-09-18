package com.liuli.weather.data.repository

import android.content.Context
import com.google.gson.Gson
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.source.AccuSource
import com.liuli.weather.data.source.CaiyunSource
import com.liuli.weather.data.source.OpenWeatherSource
import com.liuli.weather.data.source.QWeatherSource
import com.liuli.weather.data.source.WeatherSource
import java.io.File
import java.util.Locale

/**
 * 天气仓库门面：按设置里的数据源分发到具体实现，并负责按源隔离的文件缓存。
 * 缓存键包含数据源 id，切换数据源后自动读取对应缓存或重新拉取。
 */
class WeatherRepository(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val caiyunSource = CaiyunSource(settings)
    private val accuSource = AccuSource(settings)
    private val openWeatherSource = OpenWeatherSource(settings)
    private val qWeatherSource = QWeatherSource(settings)

    init {
        cleanupLegacyCache()
    }

    suspend fun getWeather(loc: LocationInfo): Weather {
        val weather = sourceFor().getWeather(loc)
        saveCache(loc, weather)
        settings.lastSuccessAt = System.currentTimeMillis()
        return weather
    }

    fun cachedWeather(loc: LocationInfo): Weather? {
        val file = cacheFile(loc)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(Charsets.UTF_8), Weather::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun sourceFor(): WeatherSource = when (settings.effectiveSource()) {
        SettingsStore.SOURCE_ACCU -> accuSource
        SettingsStore.SOURCE_OPENWEATHER -> openWeatherSource
        SettingsStore.SOURCE_QWEATHER -> qWeatherSource
        else -> caiyunSource
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(loc: LocationInfo): File {
        val name = String.format(
            Locale.US, "wx_%s_%.4f_%.4f.json", settings.effectiveSource(), loc.lat, loc.lng
        )
        return File(appContext.filesDir, name)
    }

    private fun saveCache(loc: LocationInfo, weather: Weather) {
        try {
            cacheFile(loc).writeText(gson.toJson(weather), Charsets.UTF_8)
        } catch (e: Exception) {
            // 缓存失败不影响展示
        }
    }

    /** 删除多数据源改造前不带源前缀的旧缓存文件。 */
    private fun cleanupLegacyCache() {
        val prefixes = listOf(
            SettingsStore.SOURCE_CAIYUN,
            SettingsStore.SOURCE_ACCU,
            SettingsStore.SOURCE_OPENWEATHER,
            SettingsStore.SOURCE_QWEATHER
        )
        val files = appContext.filesDir.listFiles { _, name ->
            name.startsWith("wx_") && prefixes.none { name.startsWith("wx_$it") }
        } ?: return
        files.forEach { it.delete() }
    }
}
