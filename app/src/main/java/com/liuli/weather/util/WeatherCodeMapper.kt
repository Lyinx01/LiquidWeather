package com.liuli.weather.util

import com.liuli.weather.R

/**
 * 彩云天气 skycon 代码 -> 中文文案 / 图标 / 背景资源 的映射。
 * skycon 全集：CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT,
 * CLOUDY, LIGHT_HAZE, MODERATE_HAZE, HEAVY_HAZE, LIGHT_RAIN, MODERATE_RAIN,
 * HEAVY_RAIN, STORM_RAIN, FOG, LIGHT_SNOW, MODERATE_SNOW, HEAVY_SNOW,
 * STORM_SNOW, DUST, SAND, WINDY, THUNDER_SHOWER
 */
object WeatherCodeMapper {

    fun textFor(skycon: String?): String = when (skycon) {
        "CLEAR_DAY" -> "晴"
        "CLEAR_NIGHT" -> "晴夜"
        "PARTLY_CLOUDY_DAY" -> "白天多云"
        "PARTLY_CLOUDY_NIGHT" -> "夜间多云"
        "CLOUDY" -> "阴"
        "LIGHT_HAZE" -> "轻度雾霾"
        "MODERATE_HAZE" -> "中度雾霾"
        "HEAVY_HAZE" -> "重度雾霾"
        "LIGHT_RAIN" -> "小雨"
        "MODERATE_RAIN" -> "中雨"
        "HEAVY_RAIN" -> "大雨"
        "STORM_RAIN" -> "暴雨"
        "FOG" -> "雾"
        "LIGHT_SNOW" -> "小雪"
        "MODERATE_SNOW" -> "中雪"
        "HEAVY_SNOW" -> "大雪"
        "STORM_SNOW" -> "暴雪"
        "DUST" -> "浮尘"
        "SAND" -> "沙尘暴"
        "WINDY" -> "大风"
        "THUNDER_SHOWER" -> "雷阵雨"
        else -> "未知"
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

    private val WIND_DIRS = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

    /** 风向文本化：API 部分地区直接返回度数字符串，统一转成八方位中文。 */
    fun windText(direction: String?, deg: Double?): String {
        val d = direction?.trim().orEmpty()
        val degValue = deg ?: d.toDoubleOrNull()
            ?: return if (d.isEmpty()) "--" else d
        val normalized = ((degValue % 360) + 360) % 360
        val idx = ((normalized + 22.5) / 45).toInt() % 8
        return WIND_DIRS[idx]
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

    /** AQI 数值对应的描述与颜色。 */
    fun aqiLevel(aqi: Int): Pair<String, Int> = when {
        aqi <= 50 -> "优" to 0xFF7ED957.toInt()
        aqi <= 100 -> "良" to 0xFFFFD54F.toInt()
        aqi <= 150 -> "轻度污染" to 0xFFFFA726.toInt()
        aqi <= 200 -> "中度污染" to 0xFFEF5350.toInt()
        aqi <= 300 -> "重度污染" to 0xFFAB47BC.toInt()
        else -> "严重污染" to 0xFF8D6E63.toInt()
    }
}
