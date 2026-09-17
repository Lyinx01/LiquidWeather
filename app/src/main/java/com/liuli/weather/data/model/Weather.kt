package com.liuli.weather.data.model

/** 领域层天气模型（可直接被 Gson 序列化用于缓存，时间统一存 epoch 毫秒）。 */

data class Weather(
    val location: LocationInfo,
    val fetchedAt: Long,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val hourlyDescription: String?,
    val daily: List<DailyWeather>,
    val alerts: List<WeatherAlert>
)

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Double,
    val skycon: String,
    val skyconName: String,
    val windSpeed: Double,
    val windDirection: String,
    val pressure: Double,
    val cloudRate: Double,
    val uvIndex: String?,
    val uvDesc: String?,
    val comfortDesc: String?,
    val precipIntensity: Double?,
    val nearestPrecipDistance: Double?,
    val aqi: AqiInfo?
)

data class HourlyWeather(
    val time: Long,
    val temperature: Double,
    val skycon: String,
    val precipProbability: Int?
)

data class DailyWeather(
    val date: Long,
    val skycon: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipProbability: Int?,
    val uvIndex: String?,
    val uvDesc: String?,
    val sunrise: String?,
    val sunset: String?,
    val windMax: Double?
)

data class WeatherAlert(
    val title: String,
    val description: String,
    val source: String?,
    val publishTime: Long
)

data class AqiInfo(
    val aqi: Int,
    val description: String?,
    val pm25: Double?,
    val pm10: Double?,
    val o3: Double?,
    val no2: Double?,
    val so2: Double?,
    val co: Double?
)

/** 详细信息网格的条目（icon 为 drawable 资源 id）。 */
data class DetailItem(val icon: Int, val label: String, val value: String)
