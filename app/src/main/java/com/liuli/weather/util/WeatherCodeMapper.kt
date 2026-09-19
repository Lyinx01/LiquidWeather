package com.liuli.weather.util

import com.liuli.weather.R

/**
 * 天气代码 -> 文案 / 图标 / 背景资源 的映射。
 * 文案全部走字符串资源，随应用语言切换。
 */
object WeatherCodeMapper {

    /** 统一天气代码 -> 本地化文案。 */
    fun textFor(skycon: String?): String = AppCtx.str(resFor(skycon))

    private fun resFor(skycon: String?): Int = when (skycon) {
        "CLEAR_DAY" -> R.string.wx_clear_day
        "CLEAR_NIGHT" -> R.string.wx_clear_night
        "PARTLY_CLOUDY_DAY" -> R.string.wx_partly_cloudy_day
        "PARTLY_CLOUDY_NIGHT" -> R.string.wx_partly_cloudy_night
        "CLOUDY" -> R.string.wx_cloudy
        "LIGHT_HAZE" -> R.string.wx_light_haze
        "MODERATE_HAZE" -> R.string.wx_moderate_haze
        "HEAVY_HAZE" -> R.string.wx_heavy_haze
        "LIGHT_RAIN" -> R.string.wx_light_rain
        "MODERATE_RAIN" -> R.string.wx_moderate_rain
        "HEAVY_RAIN" -> R.string.wx_heavy_rain
        "STORM_RAIN" -> R.string.wx_storm_rain
        "FOG" -> R.string.wx_fog
        "LIGHT_SNOW" -> R.string.wx_light_snow
        "MODERATE_SNOW" -> R.string.wx_moderate_snow
        "HEAVY_SNOW" -> R.string.wx_heavy_snow
        "STORM_SNOW" -> R.string.wx_storm_snow
        "DUST" -> R.string.wx_dust
        "SAND" -> R.string.wx_sand
        "WINDY" -> R.string.wx_windy
        "THUNDER_SHOWER" -> R.string.wx_thunder
        else -> R.string.wx_unknown
    }

    fun iconFor(skycon: String?): Int = when (skycon) {
        "CLEAR_DAY" -> R.drawable.ic_w_sunny
        "CLEAR_NIGHT" -> R.drawable.ic_w_night
        "PARTLY_CLOUDY_DAY" -> R.drawable.ic_w_partly_day
        "PARTLY_CLOUDY_NIGHT" -> R.drawable.ic_w_partly_night
        "CLOUDY" -> R.drawable.ic_w_cloudy
        "LIGHT_RAIN", "MODERATE_RAIN" -> R.drawable.ic_w_rain
        "HEAVY_RAIN", "STORM_RAIN" -> R.drawable.ic_w_heavyrain
        "THUNDER_SHOWER" -> R.drawable.ic_w_thunder
        "LIGHT_SNOW", "MODERATE_SNOW", "HEAVY_SNOW", "STORM_SNOW" -> R.drawable.ic_w_snow
        "FOG" -> R.drawable.ic_w_fog
        "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE" -> R.drawable.ic_w_haze
        "DUST", "SAND" -> R.drawable.ic_w_dust
        "WINDY" -> R.drawable.ic_w_wind
        else -> R.drawable.ic_w_cloudy
    }

    fun isNight(skycon: String?): Boolean = skycon?.endsWith("_NIGHT") == true

    private val WIND_RES = intArrayOf(
        R.string.wind_n, R.string.wind_ne, R.string.wind_e, R.string.wind_se,
        R.string.wind_s, R.string.wind_sw, R.string.wind_w, R.string.wind_nw
    )

    /** 风向本地化：API 部分地区返回度数字符串，统一转成八方位；无法解析时原样返回。 */
    fun windText(direction: String?, deg: Double?): String {
        val d = direction?.trim().orEmpty()
        val degValue = deg ?: d.toDoubleOrNull()
            ?: return if (d.isEmpty()) "--" else d
        val normalized = ((degValue % 360) + 360) % 360
        val idx = ((normalized + 22.5) / 45).toInt() % 8
        return AppCtx.str(WIND_RES[idx])
    }

    /** 整屏天空背景（需要给 Liquid Glass 提供折射细节）。 */
    fun backgroundFor(skycon: String?): Int = when {
        skycon == "CLEAR_DAY" -> R.drawable.bg_sky_sunny
        skycon == "CLEAR_NIGHT" || skycon == "PARTLY_CLOUDY_NIGHT" -> R.drawable.bg_sky_night
        skycon == null -> R.drawable.bg_sky_cloudy
        skycon.contains("RAIN") || skycon == "THUNDER_SHOWER" -> R.drawable.bg_sky_rain
        skycon.contains("SNOW") -> R.drawable.bg_sky_snow
        skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> R.drawable.bg_sky_fog
        else -> R.drawable.bg_sky_cloudy
    }

    /** AQI 数值对应的本地化描述与颜色。 */
    fun aqiLevel(aqi: Int): Pair<String, Int> = when {
        aqi <= 50 -> AppCtx.str(R.string.aqi_good) to 0xFF7ED957.toInt()
        aqi <= 100 -> AppCtx.str(R.string.aqi_moderate) to 0xFFFFD54F.toInt()
        aqi <= 150 -> AppCtx.str(R.string.aqi_light) to 0xFFFFA726.toInt()
        aqi <= 200 -> AppCtx.str(R.string.aqi_medium) to 0xFFEF5350.toInt()
        aqi <= 300 -> AppCtx.str(R.string.aqi_heavy) to 0xFFAB47BC.toInt()
        else -> AppCtx.str(R.string.aqi_severe) to 0xFF8D6E63.toInt()
    }
}
