package com.liuli.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** 和风天气数据源接口（免费订阅 devapi.qweather.com，location 支持经度,纬度）。 */
interface QWeatherApi {

    @GET("v7/weather/now")
    suspend fun now(
        @Query("key") token: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwNowResponse

    @GET("v7/weather/7d")
    suspend fun daily7(
        @Query("key") token: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwDailyResponse

    @GET("v7/weather/24h")
    suspend fun hourly24(
        @Query("key") token: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwHourlyResponse

    @GET("v7/warning/now")
    suspend fun warning(
        @Query("key") token: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwWarningResponse
}
