package com.liuli.weather.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * 彩云天气 API v2.6 响应模型（Gson）。
 * 接口文档：https://docs.caiyunapp.com/weather-api/
 */

class ApiException(message: String) : RuntimeException(message)

// ---------------------------------------------------------------- realtime

data class CaiyunRealtimeResponse(
    val status: String? = null,
    val description: String? = null,
    @SerializedName("server_time") val serverTime: Long? = null,
    val result: RealtimeResult? = null
) {
    data class RealtimeResult(val status: String? = null, val realtime: Realtime? = null)

    data class Realtime(
        val status: String? = null,
        val temperature: Double? = null,
        @SerializedName("apparent_temperature") val apparentTemperature: Double? = null,
        val humidity: Double? = null,
        val skycon: String? = null,
        @SerializedName("skycon_name") val skyconName: String? = null,
        val wind: Wind? = null,
        val pressure: Double? = null,
        val cloudrate: Double? = null,
        val precipitation: Precipitation? = null,
        @SerializedName("air_quality") val airQuality: AirQuality? = null,
        @SerializedName("life_index") val lifeIndex: RealtimeLifeIndex? = null
    )

    data class Wind(
        val speed: Double? = null,
        val direction: String? = null,
        @SerializedName("direction_deg") val directionDeg: Double? = null
    )

    data class Precipitation(val local: LocalPrecip? = null, val nearest: NearestPrecip? = null)

    data class LocalPrecip(
        val status: String? = null,
        val intensity: Double? = null,
        val datetime: String? = null
    )

    data class NearestPrecip(
        val status: String? = null,
        val distance: Double? = null,
        val intensity: Double? = null,
        val datetime: String? = null
    )

    /**
     * v2.6 中 aqi / description 是 {"chn":..,"usa":..} 形式的对象，
     * 兼容部分部署直接返回数值/字符串的情况。
     */
    data class AirQuality(
        val aqi: JsonElement? = null,
        val pm25: Double? = null,
        val pm10: Double? = null,
        val o3: Double? = null,
        val so2: Double? = null,
        val no2: Double? = null,
        val co: Double? = null,
        val description: JsonElement? = null
    ) {
        fun aqiChn(): Double? = numberAt(aqi, "chn")
        fun aqiUsa(): Double? = numberAt(aqi, "usa")
        fun descChn(): String? = stringAt(description, "chn")

        companion object {
            private fun numberAt(e: JsonElement?, key: String?): Double? = when {
                e == null -> null
                e.isJsonPrimitive -> e.asDouble
                e.isJsonObject -> e.asJsonObject.get(key)?.takeIf { it.isJsonPrimitive }?.asDouble
                else -> null
            }

            private fun stringAt(e: JsonElement?, key: String?): String? = when {
                e == null -> null
                e.isJsonPrimitive -> e.asString
                e.isJsonObject -> e.asJsonObject.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
        }
    }

    data class RealtimeLifeIndex(val ultraviolet: UvIndex? = null, val comfort: Comfort? = null)

    data class UvIndex(val index: String? = null, val desc: String? = null)

    data class Comfort(val index: String? = null, val desc: String? = null)
}
