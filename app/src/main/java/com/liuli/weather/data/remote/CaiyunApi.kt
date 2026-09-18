package com.liuli.weather.data.remote

import com.liuli.weather.data.model.LocationInfo
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CaiyunApi {

    /**
     * 合并接口：一次请求带齐 realtime + hourly + daily + alert。
     * 免费档 token 有较严的调用频率限制，优先使用本接口（4 请求 -> 1 请求）。
     */
    @GET("v2.6/{token}/{lng},{lat}/weather.json")
    suspend fun weather(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("dailysteps") dailySteps: Int = 15,
        @Query("hourlysteps") hourlySteps: Int = 48,
        @Query("alert") alert: Boolean = true,
        @Query("lang") lang: String = "zh_CN"
    ): CaiyunWeatherResponse

    @GET("v2.6/{token}/{lng},{lat}/realtime.json")
    suspend fun realtime(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("lang") lang: String = "zh_CN"
    ): CaiyunRealtimeResponse

    @GET("v2.6/{token}/{lng},{lat}/hourly.json")
    suspend fun hourly(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("hourlysteps") steps: Int = 48,
        @Query("lang") lang: String = "zh_CN"
    ): CaiyunHourlyResponse

    @GET("v2.6/{token}/{lng},{lat}/daily.json")
    suspend fun daily(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("dailysteps") steps: Int = 15,
        @Query("lang") lang: String = "zh_CN"
    ): CaiyunDailyResponse

    @GET("v2.6/{token}/{lng},{lat}/alert.json")
    suspend fun alert(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("lang") lang: String = "zh_CN"
    ): CaiyunAlertResponse
}

/** weather.json 合并响应：各字段与单独接口的 result 内层结构一致。 */
data class CaiyunWeatherResponse(
    val status: String? = null,
    val description: String? = null,
    val result: CombinedResult? = null
) {
    data class CombinedResult(
        val realtime: CaiyunRealtimeResponse.Realtime? = null,
        val hourly: CaiyunHourlyResponse.Hourly? = null,
        val daily: CaiyunDailyResponse.Daily? = null,
        val alert: CaiyunAlertResponse.Alert? = null
    )
}

object RetrofitClient {

    private const val BASE_URL = "https://api.caiyunapp.com/"
    private const val ACCU_BASE_URL = "https://dataservice.accuweather.com/"
    private const val OW_BASE_URL = "https://api.openweathermap.org/"
    private const val QW_BASE_URL = "https://devapi.qweather.com/"

    val api: CaiyunApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(CaiyunApi::class.java)
    }

    val accuApi: AccuApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl(ACCU_BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(AccuApi::class.java)
    }

    val owApi: OpenWeatherApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl(OW_BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    val qwApi: QWeatherApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl(QW_BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(QWeatherApi::class.java)
    }

    /** 彩云接口要求 token 为 Path 的一部分，坐标格式化成 4 位小数。 */
    fun formatLng(loc: LocationInfo): String = String.format(java.util.Locale.US, "%.4f", loc.lng)
    fun formatLat(loc: LocationInfo): String = String.format(java.util.Locale.US, "%.4f", loc.lat)
}
