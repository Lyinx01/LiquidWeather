package com.liuli.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.liuli.weather.MainActivity
import com.liuli.weather.R
import com.liuli.weather.data.model.Weather
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.repository.WeatherRepository
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.UnitConverter
import com.liuli.weather.util.WeatherCodeMapper
import java.util.concurrent.TimeUnit

/**
 * 桌面天气小部件（iOS 风格）。
 * 数据刷新交给 [WeatherWidgetWorker]（WorkManager 周期任务，最短 15 分钟），
 * 点击部件本体打开应用。
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 先用缓存立即渲染，避免空白
        appWidgetIds.forEach { id ->
            renderFromCache(context, appWidgetManager, id)
        }
        WeatherWidgetWorker.enqueue(context)
        // 触发一次立即刷新（有网时尽快更新）
        WeatherWidgetWorker.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH) {
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.liuli.weather.action.WIDGET_REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id -> renderFromCache(context, manager, id) }
        }

        /** 用本地缓存渲染；无缓存时显示引导文案。 */
        fun renderFromCache(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val settings = SettingsStore(context)
            val loc = settings.currentLocation()
            val views = RemoteViews(context.packageName, R.layout.widget_weather_wide)

            if (loc == null) {
                views.setTextViewText(R.id.widget_city, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_condition, context.getString(R.string.widget_no_data))
                views.setTextViewText(R.id.widget_high, "")
                views.setTextViewText(R.id.widget_low, "")
                views.setTextViewText(R.id.widget_temp, "--°")
                views.setViewVisibility(R.id.widget_location_arrow, android.view.View.GONE)
            } else {
                val repo = WeatherRepository(context, settings)
                val weather = repo.cachedWeather(loc)
                if (weather == null) {
                    views.setTextViewText(R.id.widget_city, loc.name)
                    views.setTextViewText(R.id.widget_condition, context.getString(R.string.widget_no_data))
                    views.setTextViewText(R.id.widget_high, "")
                    views.setTextViewText(R.id.widget_low, "")
                    views.setTextViewText(R.id.widget_temp, "--°")
                    views.setViewVisibility(
                        R.id.widget_location_arrow,
                        if (loc.isGps) android.view.View.VISIBLE else android.view.View.GONE
                    )
                } else {
                    bindWeather(context, views, weather, settings.imperialUnits)
                }
            }

            // 点击打开应用
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(widgetId, views)
        }

        fun bindWeather(context: Context, views: RemoteViews, weather: Weather, imperial: Boolean) {
            val c = weather.current
            // 背景随天气切换（现代渐变玻璃）
            views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(c.skycon))
            views.setTextViewText(R.id.widget_city, weather.location.name)
            views.setViewVisibility(
                R.id.widget_location_arrow,
                if (weather.location.isGps) android.view.View.VISIBLE else android.view.View.GONE
            )
            views.setTextViewText(R.id.widget_condition, c.skyconName)
            views.setTextViewText(
                R.id.widget_temp,
                "${UnitConverter.displayInt(c.temperature, imperial)}°"
            )
            views.setImageViewResource(R.id.widget_icon, WeatherCodeMapper.iconFor(c.skycon))

            // 最高/最低（iOS 布局：位于天气状况下方）
            val today = weather.daily.firstOrNull()
            views.setTextViewText(
                R.id.widget_high,
                if (today != null) "${UnitConverter.displayInt(today.tempMax, imperial)}°" else "--°"
            )
            views.setTextViewText(
                R.id.widget_low,
                if (today != null) "${UnitConverter.displayInt(today.tempMin, imperial)}°" else "--°"
            )

            // 逐小时预览：未来 6 个时段，其中与日落/日出同小时的位置替换为时刻标记
            val now = System.currentTimeMillis()
            val upcoming = weather.hourly.filter { it.time >= now - 30 * 60 * 1000L }.take(6)
            val hourIds = intArrayOf(
                R.id.hour_1, R.id.hour_2, R.id.hour_3, R.id.hour_4, R.id.hour_5, R.id.hour_6
            )
            val timeIds = intArrayOf(
                R.id.hour_1_time, R.id.hour_2_time, R.id.hour_3_time,
                R.id.hour_4_time, R.id.hour_5_time, R.id.hour_6_time
            )
            val iconIds = intArrayOf(
                R.id.hour_1_icon, R.id.hour_2_icon, R.id.hour_3_icon,
                R.id.hour_4_icon, R.id.hour_5_icon, R.id.hour_6_icon
            )
            val tempIds = intArrayOf(
                R.id.hour_1_temp, R.id.hour_2_temp, R.id.hour_3_temp,
                R.id.hour_4_temp, R.id.hour_5_temp, R.id.hour_6_temp
            )
            hourIds.forEachIndexed { i, containerId ->
                val hour = upcoming.getOrNull(i)
                if (hour == null) {
                    views.setViewVisibility(containerId, android.view.View.INVISIBLE)
                } else {
                    views.setViewVisibility(containerId, android.view.View.VISIBLE)
                    // iOS 设计：日出/日落所在时段显示具体时刻与对应图标
                    val event = sunriseSunsetFor(hour.time, today)
                    views.setTextViewText(
                        timeIds[i],
                        event?.first ?: TimeUtils.hourLabel(hour.time)
                    )
                    views.setImageViewResource(
                        iconIds[i],
                        event?.second ?: WeatherCodeMapper.iconFor(hour.skycon)
                    )
                    views.setTextViewText(
                        tempIds[i],
                        "${UnitConverter.displayInt(hour.temperature, imperial)}°"
                    )
                }
            }
        }

        /** 该小时是否命中今天的日出/日落（命中则返回时刻与图标）。 */
        private fun sunriseSunsetFor(
            hourMillis: Long,
            today: com.liuli.weather.data.model.DailyWeather?
        ): Pair<String, Int>? {
            if (today == null) return null
            val hourLabel = TimeUtils.hourLabel(hourMillis)
            return when {
                today.sunrise?.substringBefore(":")?.let { "${it.toIntOrNull()}时" } == hourLabel ->
                    (today.sunrise ?: "") to R.drawable.ic_d_sunrise
                today.sunset?.substringBefore(":")?.let { "${it.toIntOrNull()}时" } == hourLabel ->
                    (today.sunset ?: "") to R.drawable.ic_d_sunset
                else -> null
            }
        }

        /** 天气 -> 小部件渐变背景。 */
        private fun backgroundFor(skycon: String?): Int = when {
            skycon == null -> R.drawable.widget_bg_cloudy
            skycon == "CLEAR_DAY" -> R.drawable.widget_bg_sunny
            skycon == "CLEAR_NIGHT" || skycon == "PARTLY_CLOUDY_NIGHT" -> R.drawable.widget_bg_night
            skycon.contains("RAIN") || skycon == "THUNDER_SHOWER" -> R.drawable.widget_bg_rain
            skycon.contains("SNOW") -> R.drawable.widget_bg_snow
            skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" ->
                R.drawable.widget_bg_fog
            else -> R.drawable.widget_bg_cloudy
        }
    }
}

/** 周期刷新天气数据（WorkManager 最短周期 15 分钟）。 */
class WeatherWidgetWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val loc = settings.currentLocation() ?: return Result.success()
        return try {
            val repo = WeatherRepository(applicationContext, settings)
            repo.getWeather(loc)
            WeatherWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // 失败保留旧缓存展示，下个周期重试
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "weather_widget_periodic"
        private const val ONESHOT_NAME = "weather_widget_now"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun refreshNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
