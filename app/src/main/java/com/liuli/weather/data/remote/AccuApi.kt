package com.liuli.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** AccuWeather 数据源接口（免费档：geoposition 定位 + 12 小时预报 + 5 日预报）。 */
interface AccuApi {

    @GET("locations/v1/cities/geoposition/search")
    suspend fun geoposition(
        @Query("apikey") token: String,
        @Query("q") q: String,
        @Query("language") lang: String = "zh-cn"
    ): AccuLocationResponse

    @GET("currentconditions/v1/{locationKey}")
    suspend fun current(
        @Query("apikey") token: String,
        @Path("locationKey") locationKey: String,
        @Query("details") details: Boolean = true,
        @Query("language") lang: String = "zh-cn"
    ): List<AccuCurrent>

    @GET("forecasts/v1/hourly/12hour/{locationKey}")
    suspend fun hourly12(
        @Query("apikey") token: String,
        @Path("locationKey") locationKey: String,
        @Query("details") details: Boolean = true,
        @Query("language") lang: String = "zh-cn"
    ): List<AccuHourly>

    @GET("forecasts/v1/daily/5day/{locationKey}")
    suspend fun daily5(
        @Query("apikey") token: String,
        @Path("locationKey") locationKey: String,
        @Query("details") details: Boolean = true,
        @Query("language") lang: String = "zh-cn"
    ): AccuDailyResponse
}
