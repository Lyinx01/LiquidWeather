package com.liuli.weather.data.repository

import android.content.Context
import com.google.gson.Gson
import com.liuli.weather.data.model.CurrentWeather
import com.liuli.weather.data.model.AqiInfo
import com.liuli.weather.data.model.DailyWeather
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.model.WeatherAlert
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.remote.ApiException
import com.liuli.weather.data.remote.CaiyunDailyResponse
import com.liuli.weather.data.remote.CaiyunRealtimeResponse
import com.liuli.weather.data.remote.RetrofitClient
import com.liuli.weather.util.TimeUtils
import java.io.File
import java.util.Locale
import kotlin.math.max

/**
 * 天气仓库：请求彩云天气 v2.6（realtime + hourly + daily + alert），
 * 映射为领域模型并写盘缓存。数据结构参考 breezy-weather 的 Caiyun 数据源。
 */
class WeatherRepository(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    suspend fun getWeather(loc: LocationInfo): Weather {
        val token = settings.token ?: throw ApiException("未设置 API Token")
        val lng = RetrofitClient.formatLng(loc)
        val lat = RetrofitClient.formatLat(loc)

        // 单请求合并接口，最大限度节省免费档的调用配额
        val resp = RetrofitClient.api.weather(token, lng, lat)
        requireOk(resp.status, resp.description)
        val result = resp.result ?: throw ApiException("接口返回数据为空")

        val alerts = result.alert?.content.orEmpty().map { a ->
            WeatherAlert(
                title = a.title ?: "气象预警",
                description = a.description ?: "",
                source = a.source,
                publishTime = (a.pubtimestamp ?: 0L) * 1000L
            )
        }

        val weather = mapWeather(loc, result.realtime, result.hourly, result.daily, alerts)
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

    private fun requireOk(status: String?, description: String?) {
        if (status == "error") {
            throw ApiException(description?.takeIf { it.isNotBlank() } ?: "彩云天气接口返回错误")
        }
    }

    // ---------------------------------------------------------------- mapping

    private fun mapWeather(
        loc: LocationInfo,
        realtime: CaiyunRealtimeResponse.Realtime?,
        hourly: com.liuli.weather.data.remote.CaiyunHourlyResponse.Hourly?,
        daily: CaiyunDailyResponse.Daily?,
        alerts: List<WeatherAlert>
    ): Weather {
        val r = realtime
        val h = hourly
        val d = daily

        val air = r?.airQuality
        val aqiInfo = air?.aqiChn()?.toInt()?.let {
            AqiInfo(
                aqi = it,
                description = air.descChn(),
                pm25 = air.pm25,
                pm10 = air.pm10,
                o3 = air.o3,
                no2 = air.no2,
                so2 = air.so2,
                co = air.co
            )
        }

        val current = CurrentWeather(
            temperature = r?.temperature ?: 0.0,
            apparentTemperature = r?.apparentTemperature ?: 0.0,
            humidity = r?.humidity ?: 0.0,
            skycon = r?.skycon ?: "CLEAR_DAY",
            skyconName = r?.skyconName?.takeIf { it.isNotBlank() }
                ?: com.liuli.weather.util.WeatherCodeMapper.textFor(r?.skycon),
            windSpeed = r?.wind?.speed ?: 0.0,
            windDirection = com.liuli.weather.util.WeatherCodeMapper.windText(
                r?.wind?.direction, r?.wind?.directionDeg
            ),
            pressure = r?.pressure ?: 0.0,
            cloudRate = r?.cloudrate ?: 0.0,
            uvIndex = r?.lifeIndex?.ultraviolet?.index,
            uvDesc = r?.lifeIndex?.ultraviolet?.desc,
            comfortDesc = r?.lifeIndex?.comfort?.desc,
            precipIntensity = r?.precipitation?.local?.intensity,
            nearestPrecipDistance = r?.precipitation?.nearest?.distance,
            aqi = aqiInfo
        )

        val temps = h?.temperature.orEmpty()
        val skyconByTime = h?.skycon.orEmpty().associateBy { it.datetime }
        val precipByTime = h?.precipitation2h.orEmpty().associateBy { it.datetime }
        val hourly = temps.mapIndexed { i, step ->
            val sky = skyconByTime[step.datetime] ?: h?.skycon?.getOrNull(i)
            HourlyWeather(
                time = TimeUtils.parseIsoMillis(step.datetime),
                temperature = step.value ?: 0.0,
                skycon = sky?.value ?: sky?.key ?: "CLEAR_DAY",
                precipProbability = precipByTime[step.datetime]?.probability
            )
        }

        val dailyTemps = d?.temperature.orEmpty()
        val daily = dailyTemps.mapIndexed { i, t ->
            val sky = d?.skycon08h20h?.getOrNull(i) ?: d?.skycon?.getOrNull(i)
            val precip = d?.precipitation?.getOrNull(i)
            val prob = max(precip?.probability08h20h ?: 0, precip?.probability20h32h ?: 0)
            DailyWeather(
                date = TimeUtils.parseDateMillis(t.date),
                skycon = sky?.value ?: sky?.key ?: "CLEAR_DAY",
                tempMax = t.max ?: 0.0,
                tempMin = t.min ?: 0.0,
                precipProbability = if (prob > 0) prob else null,
                uvIndex = d?.lifeIndex?.ultraviolet?.getOrNull(i)?.index,
                uvDesc = d?.lifeIndex?.ultraviolet?.getOrNull(i)?.desc,
                sunrise = d?.astronomical?.getOrNull(i)?.sunrise?.time,
                sunset = d?.astronomical?.getOrNull(i)?.sunset?.time,
                windMax = d?.wind?.getOrNull(i)?.max?.speed
            )
        }

        return Weather(
            location = loc,
            fetchedAt = System.currentTimeMillis(),
            current = current,
            hourly = hourly,
            hourlyDescription = h?.description?.takeIf { it.isNotBlank() },
            daily = daily,
            alerts = alerts
        )
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(loc: LocationInfo): File {
        val name = String.format(
            Locale.US, "wx_%.4f_%.4f.json", loc.lat, loc.lng
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
}
