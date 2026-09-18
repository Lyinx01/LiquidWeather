package com.liuli.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** OpenWeather 数据源接口（免费档：current + 5 天/3 小时预报）。 */
interface OpenWeatherApi {

    @GET("data/2.5/weather")
    suspend fun current(
        @Query("appid") token: String,
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("lang") lang: String = "zh_cn",
        @Query("units") units: String = "metric"
    ): OwCurrentResponse

    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("appid") token: String,
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("lang") lang: String = "zh_cn",
        @Query("units") units: String = "metric"
    ): OwForecastResponse
}
