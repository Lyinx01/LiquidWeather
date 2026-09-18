package com.liuli.weather.data.source

import com.liuli.weather.data.model.CurrentWeather
import com.liuli.weather.data.model.DailyWeather
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.remote.ApiException
import com.liuli.weather.data.remote.OwCurrentResponse
import com.liuli.weather.data.remote.OwForecastResponse
import com.liuli.weather.data.remote.OwWeather
import com.liuli.weather.data.remote.RetrofitClient
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * OpenWeather 数据源（免费档 2.5：current + 5 天/3 小时预报）。
 * 无每日聚合接口，5 天日预报由 3 小时步长按本地日期聚合得到；
 * 免费档无空气质量与预警，对应卡片自动隐藏。
 */
class OpenWeatherSource(private val settings: SettingsStore) : WeatherSource {

    override val id = SettingsStore.SOURCE_OPENWEATHER

    override suspend fun getWeather(loc: LocationInfo): Weather {
        val token = settings.owToken ?: throw ApiException("未设置 OpenWeather API Key")
        val lat = String.format(Locale.US, "%.4f", loc.lat)
        val lng = String.format(Locale.US, "%.4f", loc.lng)
        val api = RetrofitClient.owApi
        val current = api.current(token, lat, lng)
        val forecast = api.forecast(token, lat, lng)
        return mapWeather(loc, current, forecast)
    }

    // ---------------------------------------------------------------- mapping

    private fun mapWeather(
        loc: LocationInfo,
        current: OwCurrentResponse,
        forecast: OwForecastResponse
    ): Weather {
        val cw = current.weather?.firstOrNull()
        val night = cw?.icon?.endsWith("n") == true
        val currentSkycon = skyconForId(cw?.id, night)

        val now = System.currentTimeMillis()
        val currentDaily = CurrentWeather(
            temperature = current.main?.temp ?: 0.0,
            apparentTemperature = current.main?.feelsLike ?: 0.0,
            humidity = (current.main?.humidity ?: 0.0) / 100.0,
            skycon = currentSkycon,
            skyconName = cw?.description?.takeIf { it.isNotBlank() }
                ?: WeatherCodeMapper.textFor(currentSkycon),
            // OpenWeather 风速单位 m/s，统一转 km/h
            windSpeed = (current.wind?.speed ?: 0.0) * 3.6,
            windDirection = WeatherCodeMapper.windText(null, current.wind?.deg),
            pressure = current.main?.pressure ?: 0.0,
            cloudRate = (current.clouds?.all ?: 0.0) / 100.0,
            uvIndex = null,
            uvDesc = null,
            comfortDesc = null,
            precipIntensity = current.rain?.h1,
            nearestPrecipDistance = null,
            aqi = null
        )

        val zone = ZoneId.systemDefault()
        val items = forecast.list.orEmpty()

        val hourlyList = items.map { item ->
            val w = item.weather?.firstOrNull()
            val itemNight = w?.icon?.endsWith("n") == true
            HourlyWeather(
                time = (item.dt ?: 0L) * 1000L,
                temperature = item.main?.temp ?: 0.0,
                skycon = skyconForId(w?.id, itemNight),
                precipProbability = item.pop?.let { (it * 100).roundToInt() }
            )
        }

        // 3 小时步长按本地日期聚合成每日预报
        val grouped = items.groupBy {
            Instant.ofEpochSecond(it.dt ?: 0L).atZone(zone).toLocalDate()
        }.toSortedMap()

        val citySunrise = forecast.city?.sunrise
        val citySunset = forecast.city?.sunset
        val today = LocalDate.now(zone)

        val dailyList = grouped.entries.take(5).map { (date, group) ->
            val temps = group.mapNotNull { it.main?.temp }
            val skycons = group.mapNotNull { g ->
                g.weather?.firstOrNull()?.let { w -> skyconForId(w.id, w.icon?.endsWith("n") == true) }
            }
            val maxWind = group.maxOfOrNull { (it.wind?.speed ?: 0.0) } ?: 0.0
            DailyWeather(
                date = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                skycon = skycons.maxByOrNull { skyconPriority(it) } ?: "CLEAR_DAY",
                tempMax = temps.maxOrNull() ?: 0.0,
                tempMin = temps.minOrNull() ?: 0.0,
                precipProbability = group.maxOfOrNull { ((it.pop ?: 0.0) * 100).roundToInt() }
                    ?.takeIf { it > 0 },
                uvIndex = null,
                uvDesc = null,
                sunrise = if (date == today) citySunrise?.let {
                    TimeUtils.clockLabel(it * 1000L)
                } else null,
                sunset = if (date == today) citySunset?.let {
                    TimeUtils.clockLabel(it * 1000L)
                } else null,
                windMax = (maxWind * 3.6).takeIf { maxWind > 0.0 }
            )
        }

        return Weather(
            location = loc,
            fetchedAt = now,
            current = currentDaily,
            hourly = hourlyList,
            hourlyDescription = null,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    // ------------------------------------------------- condition id -> skycon

    /** OpenWeather 天气码（id 分组）-> skycon，night 仅影响晴/少云。 */
    private fun skyconForId(id: Int?, night: Boolean): String = when (id) {
        in 200..232 -> "THUNDER_SHOWER"
        in 300..321 -> "LIGHT_RAIN"
        500 -> "LIGHT_RAIN"
        501 -> "MODERATE_RAIN"
        in 502..504 -> "HEAVY_RAIN"
        511 -> "LIGHT_SNOW"
        520, 521, 531 -> "LIGHT_RAIN"
        522 -> "MODERATE_RAIN"
        600 -> "LIGHT_SNOW"
        601 -> "MODERATE_SNOW"
        602 -> "MODERATE_SNOW"
        in 611..616, 620 -> "LIGHT_SNOW"
        621, 622 -> "MODERATE_SNOW"
        701, 741 -> "FOG"
        721 -> "LIGHT_HAZE"
        in 731..762 -> "DUST"
        771, 781 -> "WINDY"
        800 -> if (night) "CLEAR_NIGHT" else "CLEAR_DAY"
        in 801..802 -> if (night) "PARTLY_CLOUDY_NIGHT" else "PARTLY_CLOUDY_DAY"
        in 803..804 -> "CLOUDY"
        else -> if (night) "PARTLY_CLOUDY_NIGHT" else "PARTLY_CLOUDY_DAY"
    }

    /** 聚合每日天气时用严重程度打分，优先展示降水。 */
    private fun skyconPriority(skycon: String): Int = when (skycon) {
        "THUNDER_SHOWER" -> 7
        "HEAVY_RAIN" -> 6
        "MODERATE_RAIN" -> 5
        "MODERATE_SNOW" -> 5
        "LIGHT_RAIN" -> 4
        "LIGHT_SNOW" -> 4
        "FOG", "LIGHT_HAZE", "DUST", "WINDY" -> 3
        "CLOUDY" -> 2
        else -> 0
    }
}
