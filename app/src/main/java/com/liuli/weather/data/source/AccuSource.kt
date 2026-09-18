package com.liuli.weather.data.source

import com.liuli.weather.data.model.CurrentWeather
import com.liuli.weather.data.model.DailyWeather
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.remote.AccuCurrent
import com.liuli.weather.data.remote.AccuDailyResponse
import com.liuli.weather.data.remote.AccuHourly
import com.liuli.weather.data.remote.ApiException
import com.liuli.weather.data.remote.RetrofitClient
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * AccuWeather 数据源。
 * 免费档：currentconditions + 12 小时预报 + 5 日预报，每日 50 次调用；
 * 位置 Key 由坐标经 geoposition/search 解析并缓存到 LocationInfo.accuKey。
 * 领域映射：AccuWeather 图标码 -> skycon 体系，复用全套图标与背景。
 */
class AccuSource(private val settings: SettingsStore) : WeatherSource {

    override val id = SettingsStore.SOURCE_ACCU

    override suspend fun getWeather(loc: LocationInfo): Weather {
        val token = settings.accuToken ?: throw ApiException("未设置 AccuWeather API Key")
        val locationKey = loc.accuKey ?: resolveLocationKey(loc, token)

        val api = RetrofitClient.accuApi
        val current = api.current(token, locationKey).firstOrNull()
            ?: throw ApiException("AccuWeather 无当前天气数据")
        val hourly = api.hourly12(token, locationKey)
        val daily = api.daily5(token, locationKey)

        return mapWeather(loc, current, hourly, daily)
    }

    /** 坐标 -> AccuWeather locationKey，成功后缓存到城市，避免重复消耗配额。 */
    private suspend fun resolveLocationKey(loc: LocationInfo, token: String): String {
        val q = String.format(
            java.util.Locale.US, "%.4f,%.4f", loc.lat, loc.lng
        )
        val resp = RetrofitClient.accuApi.geoposition(token, q)
        val key = resp.key?.takeIf { it.isNotBlank() }
            ?: throw ApiException("AccuWeather 位置解析失败")
        settings.updateLocationAccuKey(loc, key)
        return key
    }

    // ---------------------------------------------------------------- mapping

    private fun mapWeather(
        loc: LocationInfo,
        c: AccuCurrent,
        hourly: List<AccuHourly>,
        daily: AccuDailyResponse
    ): Weather {
        val currentNight = c.localObservationDateTime
            ?.let { TimeUtils.parseIsoMillis(it) }
            ?.let { isNightHour(it) }
            ?: false
        val currentSkycon = skyconForIcon(c.weatherIcon, currentNight)

        val current = CurrentWeather(
            temperature = c.temperature?.metric?.value ?: 0.0,
            apparentTemperature = c.realFeelTemperature?.metric?.value
                ?: c.temperature?.metric?.value ?: 0.0,
            humidity = (c.relativeHumidity ?: 0.0) / 100.0,
            skycon = currentSkycon,
            skyconName = c.weatherText?.takeIf { it.isNotBlank() }
                ?: WeatherCodeMapper.textFor(currentSkycon),
            windSpeed = c.wind?.speed?.metric?.value ?: 0.0,
            windDirection = WeatherCodeMapper.windText(
                c.wind?.direction?.localized, c.wind?.direction?.degrees
            ),
            // AccuWeather 的气压本身就是 hPa
            pressure = c.pressure?.metric?.value ?: 0.0,
            cloudRate = (c.cloudCover ?: 0.0) / 100.0,
            uvIndex = c.uvIndex?.toString(),
            uvDesc = c.uvIndexText,
            comfortDesc = null,
            precipIntensity = c.precip1hr?.metric?.value,
            nearestPrecipDistance = null,
            aqi = null
        )

        val hourlyList = hourly.map { h ->
            val epochMillis = (h.epochDateTime ?: 0L) * 1000L
            HourlyWeather(
                time = epochMillis,
                temperature = h.temperature?.metric?.value ?: 0.0,
                skycon = skyconForHourIcon(h.weatherIcon, epochMillis),
                precipProbability = h.precipitationProbability
            )
        }

        val dailyList = daily.dailyForecasts.orEmpty().map { d ->
            val uv = d.airAndPollen?.firstOrNull { it.name.equals("UVIndex", true) }
            val prob = max(
                d.day?.precipitationProbability ?: 0,
                d.night?.precipitationProbability ?: 0
            )
            DailyWeather(
                date = TimeUtils.parseIsoMillis(d.date),
                skycon = skyconForIcon(d.day?.icon, night = false),
                tempMax = d.temperature?.maximum?.value ?: 0.0,
                tempMin = d.temperature?.minimum?.value ?: 0.0,
                precipProbability = if (prob > 0) prob else null,
                uvIndex = uv?.value?.roundToInt()?.toString(),
                uvDesc = uv?.category,
                sunrise = d.sun?.rise?.let { TimeUtils.clockLabel(TimeUtils.parseIsoMillis(it)) },
                sunset = d.sun?.set?.let { TimeUtils.clockLabel(TimeUtils.parseIsoMillis(it)) },
                windMax = max(
                    d.wind?.day?.speed?.metric?.value ?: 0.0,
                    d.wind?.night?.speed?.metric?.value ?: 0.0
                ).takeIf { it > 0.0 }
            )
        }

        return Weather(
            location = loc,
            fetchedAt = System.currentTimeMillis(),
            current = current,
            hourly = hourlyList,
            hourlyDescription = daily.headline?.text,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    // ------------------------------------------------- icon code -> skycon

    private fun isNightHour(millis: Long): Boolean {
        val hour = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).hour
        return hour < 6 || hour >= 19
    }

    /** AccuWeather 图标码（1-32 白天 / 33-38 夜间）-> skycon。 */
    private fun skyconForIcon(icon: Int?, night: Boolean = false): String = when (icon) {
        1, 2 -> if (night) "CLEAR_NIGHT" else "CLEAR_DAY"
        33, 34 -> "CLEAR_NIGHT"
        3, 4, 5 -> if (night) "PARTLY_CLOUDY_NIGHT" else "PARTLY_CLOUDY_DAY"
        35, 36, 37 -> "PARTLY_CLOUDY_NIGHT"
        6, 7, 8, 38 -> "CLOUDY"
        9 -> "FOG"
        10, 11, 29 -> "LIGHT_RAIN"
        12, 30 -> "THUNDER_SHOWER"
        13, 21, 31, 15, 16, 17, 20, 24, 25, 26 -> "LIGHT_SNOW"
        14, 22, 23, 32 -> "MODERATE_SNOW"
        18, 19, 27, 28 -> "LIGHT_RAIN"
        else -> if (night) "PARTLY_CLOUDY_NIGHT" else "PARTLY_CLOUDY_DAY"
    }

    /** 逐小时预报接口给的是白天图标，按本地时间做昼夜修正。 */
    private fun skyconForHourIcon(icon: Int?, epochMillis: Long): String =
        skyconForIcon(icon, isNightHour(epochMillis))
}
