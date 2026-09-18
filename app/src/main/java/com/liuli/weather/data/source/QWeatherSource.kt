package com.liuli.weather.data.source

import com.liuli.weather.data.model.CurrentWeather
import com.liuli.weather.data.model.DailyWeather
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.model.WeatherAlert
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.remote.ApiException
import com.liuli.weather.data.remote.QwWarningResponse
import com.liuli.weather.data.remote.RetrofitClient
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 和风天气数据源（免费订阅 devapi.qweather.com）。
 * location 直接支持"经度,纬度"，无需位置解析；
 * 含实时 + 7 天 + 24 小时 + 预警，图标码映射到 skycon 体系。
 */
class QWeatherSource(private val settings: SettingsStore) : WeatherSource {

    override val id = SettingsStore.SOURCE_QWEATHER

    override suspend fun getWeather(loc: LocationInfo): Weather {
        val token = settings.qwToken ?: throw ApiException("未设置和风天气 API Key")
        val location = String.format(Locale.US, "%.4f,%.4f", loc.lng, loc.lat)
        val api = RetrofitClient.qwApi

        val now = api.now(token, location).also { checkCode(it.code, "实时天气") }
        val daily = api.daily7(token, location).also { checkCode(it.code, "每日预报") }
        val hourly = api.hourly24(token, location).also { checkCode(it.code, "逐小时预报") }
        val warning = try {
            api.warning(token, location).also { checkCode(it.code, "气象预警") }
        } catch (e: Exception) {
            null // 预警失败不阻塞主流程
        }

        return mapWeather(loc, now.now, daily.daily.orEmpty(), hourly.hourly.orEmpty(), warning)
    }

    private fun checkCode(code: String?, what: String) {
        if (code != "200") {
            val hint = when (code) {
                "401", "403" -> "（Key 无效或未开通该接口）"
                "402" -> "（免费档调用次数已用完）"
                "404" -> "（查询地区超出订阅范围）"
                else -> ""
            }
            throw ApiException("和风天气$what 获取失败$hint")
        }
    }

    // ---------------------------------------------------------------- mapping

    /** 和风字段均为字符串，安全取数。 */
    private fun String?.d(): Double? = this?.toDoubleOrNull()

    private fun mapWeather(
        loc: LocationInfo,
        now: com.liuli.weather.data.remote.QwNow?,
        daily: List<com.liuli.weather.data.remote.QwDaily>,
        hourly: List<com.liuli.weather.data.remote.QwHourly>,
        warning: QwWarningResponse?
    ): Weather {
        val nowSkycon = iconToSkycon(now?.icon)
        val current = CurrentWeather(
            temperature = now?.temp.d() ?: 0.0,
            apparentTemperature = now?.feelsLike.d() ?: 0.0,
            humidity = (now?.humidity.d() ?: 0.0) / 100.0,
            skycon = nowSkycon,
            skyconName = now?.text?.takeIf { it.isNotBlank() }
                ?: WeatherCodeMapper.textFor(nowSkycon),
            windSpeed = now?.windSpeed.d() ?: 0.0,
            windDirection = WeatherCodeMapper.windText(now?.windDir, now?.wind360.d()),
            pressure = now?.pressure.d() ?: 0.0,
            cloudRate = (now?.cloud.d() ?: 0.0) / 100.0,
            uvIndex = null,
            uvDesc = null,
            comfortDesc = null,
            precipIntensity = null,
            nearestPrecipDistance = null,
            aqi = null
        )

        val hourlyList = hourly.map { h ->
            HourlyWeather(
                time = TimeUtils.parseIsoMillis(h.fxTime),
                temperature = h.temp.d() ?: 0.0,
                skycon = iconToSkycon(h.icon),
                precipProbability = h.pop.d()?.roundToInt()
            )
        }

        val dailyList = daily.map { d ->
            DailyWeather(
                date = TimeUtils.parseDateMillis(d.fxDate),
                skycon = iconToSkycon(d.iconDay),
                tempMax = d.tempMax.d() ?: 0.0,
                tempMin = d.tempMin.d() ?: 0.0,
                precipProbability = d.precipProb.d()?.roundToInt()?.takeIf { it > 0 },
                uvIndex = d.uvIndex,
                uvDesc = null,
                sunrise = d.sunrise,
                sunset = d.sunset,
                windMax = d.windSpeedDay.d()?.takeIf { it > 0.0 }
            )
        }

        val alerts = warning?.warning.orEmpty().map { w ->
            WeatherAlert(
                title = w.title ?: w.typeName ?: "气象预警",
                description = w.text ?: "",
                source = w.source,
                publishTime = TimeUtils.parseIsoMillis(w.pubTime)
            )
        }

        return Weather(
            location = loc,
            fetchedAt = System.currentTimeMillis(),
            current = current,
            hourly = hourlyList,
            hourlyDescription = null,
            daily = dailyList,
            alerts = alerts
        )
    }

    // ------------------------------------------------- 和风图标码 -> skycon

    private fun iconToSkycon(icon: String?): String = when (icon?.toIntOrNull()) {
        100 -> "CLEAR_DAY"
        150 -> "CLEAR_NIGHT"
        101, 102, 103 -> "PARTLY_CLOUDY_DAY"
        151, 152, 153 -> "PARTLY_CLOUDY_NIGHT"
        104, 154 -> "CLOUDY"
        300, 301, 309, 314, 350, 399 -> "LIGHT_RAIN"
        305 -> "LIGHT_RAIN"
        306, 315 -> "MODERATE_RAIN"
        302, 303, 304, 351 -> "THUNDER_SHOWER"
        307, 308, 310, 311, 312, 316, 317, 318 -> "HEAVY_RAIN"
        313 -> "LIGHT_SNOW"
        400, 401, 404, 405, 406, 407, 408, 499 -> "LIGHT_SNOW"
        402, 403, 409, 410 -> "MODERATE_SNOW"
        456, 457 -> "LIGHT_SNOW"
        500, 501, 512, 513, 514 -> "FOG"
        502, 515, 516, 517 -> "LIGHT_HAZE"
        503, 504, 505, 506, 507 -> "DUST"
        518 -> "WINDY"
        else -> "PARTLY_CLOUDY_DAY"
    }
}
