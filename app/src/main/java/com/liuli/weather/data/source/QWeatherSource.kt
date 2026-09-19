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
import com.liuli.weather.R
import com.liuli.weather.util.ApiLang
import com.liuli.weather.util.AppCtx
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
        val token = settings.qwToken ?: throw ApiException(AppCtx.str(R.string.err_no_key_qw))
        val location = String.format(Locale.US, "%.4f,%.4f", loc.lng, loc.lat)
        val lang = ApiLang.qweather()

        // 新控制台账号使用专属 API Host 且用请求头认证；旧账号走默认 devapi + key 参数。
        // 双认证同时带上（header + key 参数），服务端按自身体系取其一，简化用户配置。
        val base = RetrofitClient.normalizeQwHost(settings.effectiveQwHost())
        val isLegacyHost = base.contains("devapi.qweather.com")
        val api = RetrofitClient.qwApi
        val headerKey = if (isLegacyHost) null else token
        val queryKey = if (isLegacyHost) token else null

        val now = api.now("${base}v7/weather/now", headerKey, queryKey, location, lang)
            .also { checkCode(it.code, "now") }
        val daily = api.daily7("${base}v7/weather/7d", headerKey, queryKey, location, lang)
            .also { checkCode(it.code, "7d") }
        val hourly = api.hourly24("${base}v7/weather/24h", headerKey, queryKey, location, lang)
            .also { checkCode(it.code, "24h") }
        val warning = try {
            api.warning("${base}v7/warning/now", headerKey, queryKey, location, lang)
                .also { checkCode(it.code, "warning") }
        } catch (e: Exception) {
            null // 预警失败不阻塞主流程
        }

        return mapWeather(loc, now.now, daily.daily.orEmpty(), hourly.hourly.orEmpty(), warning)
    }

    /** 业务码校验：错误提示本地化，接口名用简短英文标识便于排查。 */
    private fun checkCode(code: String?, what: String) {
        if (code != "200") {
            val hint = when (code) {
                "401", "403" -> AppCtx.str(R.string.err_qw_key_invalid)
                "402" -> AppCtx.str(R.string.err_qw_quota)
                "404" -> AppCtx.str(R.string.err_qw_region)
                else -> ""
            }
            throw ApiException(
                if (hint.isEmpty()) AppCtx.str(R.string.err_qw_empty)
                else "$what: $hint"
            )
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
                title = w.title ?: w.typeName ?: AppCtx.str(R.string.wx_unknown),
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
