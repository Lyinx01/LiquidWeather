package com.liuli.weather.data.remote

import com.google.gson.annotations.SerializedName

/** OpenWeather DTO（api.openweathermap.org，免费档 2.5 接口）。 */

data class OwCurrentResponse(
    val weather: List<OwWeather>? = null,
    val main: OwMain? = null,
    val wind: OwWind? = null,
    val clouds: OwClouds? = null,
    val sys: OwSys? = null,
    val name: String? = null,
    val dt: Long? = null,
    val rain: OwRain? = null
)

data class OwWeather(
    val id: Int? = null,
    val main: String? = null,
    val description: String? = null,
    val icon: String? = null
)

data class OwMain(
    val temp: Double? = null,
    @SerializedName("feels_like") val feelsLike: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null
)

data class OwWind(val speed: Double? = null, val deg: Double? = null)

data class OwClouds(val all: Double? = null)

data class OwSys(val sunrise: Long? = null, val sunset: Long? = null)

data class OwRain(@SerializedName("1h") val h1: Double? = null)

data class OwForecastResponse(
    val list: List<OwForecastItem>? = null,
    val city: OwCity? = null
)

data class OwForecastItem(
    val dt: Long? = null,
    val main: OwMain? = null,
    val weather: List<OwWeather>? = null,
    val wind: OwWind? = null,
    val clouds: OwClouds? = null,
    @SerializedName("pop") val pop: Double? = null,
    @SerializedName("dt_txt") val dtTxt: String? = null
)

data class OwCity(val sunrise: Long? = null, val sunset: Long? = null)
