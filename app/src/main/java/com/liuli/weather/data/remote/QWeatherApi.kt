package com.liuli.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 和风天气数据源接口。
 * 认证与 Host 因账号体系而异：
 * - 旧免费账号：devapi.qweather.com，query 参数 key=...
 * - 新控制台账号：账号专属 API Host（控制台"设置->API Host"查看），
 *   请求头 X-QW-Api-Key 认证。
 * Host 由调用方按用户配置动态拼接。
 */
interface QWeatherApi {

    @GET
    suspend fun now(
        @Url url: String,
        @Header("X-QW-Api-Key") headerKey: String?,
        @Query("key") queryKey: String?,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwNowResponse

    @GET
    suspend fun daily7(
        @Url url: String,
        @Header("X-QW-Api-Key") headerKey: String?,
        @Query("key") queryKey: String?,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwDailyResponse

    @GET
    suspend fun hourly24(
        @Url url: String,
        @Header("X-QW-Api-Key") headerKey: String?,
        @Query("key") queryKey: String?,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwHourlyResponse

    @GET
    suspend fun warning(
        @Url url: String,
        @Header("X-QW-Api-Key") headerKey: String?,
        @Query("key") queryKey: String?,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QwWarningResponse
}
