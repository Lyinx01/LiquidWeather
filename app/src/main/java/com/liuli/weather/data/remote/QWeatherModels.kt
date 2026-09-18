package com.liuli.weather.data.remote

import com.google.gson.annotations.SerializedName

/** 和风天气 DTO（devapi.qweather.com，免费订阅 v7 接口）。字段官方为字符串。 */

data class QwNowResponse(
    val code: String? = null,
    val now: QwNow? = null
)

data class QwNow(
    @SerializedName("obsTime") val obsTime: String? = null,
    @SerializedName("temp") val temp: String? = null,
    @SerializedName("feelsLike") val feelsLike: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("wind360") val wind360: String? = null,
    @SerializedName("windDir") val windDir: String? = null,
    @SerializedName("windSpeed") val windSpeed: String? = null,
    @SerializedName("humidity") val humidity: String? = null,
    @SerializedName("pressure") val pressure: String? = null,
    @SerializedName("cloud") val cloud: String? = null
)

data class QwDailyResponse(
    val code: String? = null,
    val daily: List<QwDaily>? = null
)

data class QwDaily(
    @SerializedName("fxDate") val fxDate: String? = null,
    @SerializedName("tempMax") val tempMax: String? = null,
    @SerializedName("tempMin") val tempMin: String? = null,
    @SerializedName("iconDay") val iconDay: String? = null,
    @SerializedName("textDay") val textDay: String? = null,
    @SerializedName("precipProb") val precipProb: String? = null,
    @SerializedName("uvIndex") val uvIndex: String? = null,
    @SerializedName("sunrise") val sunrise: String? = null,
    @SerializedName("sunset") val sunset: String? = null,
    @SerializedName("windSpeedDay") val windSpeedDay: String? = null
)

data class QwHourlyResponse(
    val code: String? = null,
    val hourly: List<QwHourly>? = null
)

data class QwHourly(
    @SerializedName("fxTime") val fxTime: String? = null,
    @SerializedName("temp") val temp: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("pop") val pop: String? = null,
    @SerializedName("windSpeed") val windSpeed: String? = null,
    @SerializedName("humidity") val humidity: String? = null
)

data class QwWarningResponse(
    val code: String? = null,
    val warning: List<QwWarning>? = null
)

data class QwWarning(
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("typeName") val typeName: String? = null,
    @SerializedName("pubTime") val pubTime: String? = null,
    @SerializedName("source") val source: String? = null
)
