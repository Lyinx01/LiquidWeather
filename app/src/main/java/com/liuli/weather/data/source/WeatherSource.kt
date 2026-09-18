package com.liuli.weather.data.source

import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather

/** 天气数据源抽象：彩云天气 / AccuWeather 等各自实现。 */
interface WeatherSource {
    val id: String

    /** 拉取并映射为领域模型；失败抛 ApiException/HttpException。 */
    suspend fun getWeather(loc: LocationInfo): Weather
}
