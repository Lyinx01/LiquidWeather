package com.liuli.weather.data.remote

import com.google.gson.annotations.SerializedName

/**
 * AccuWeather DTO（dataservice.accuweather.com）。
 * 字段均为 PascalCase，统一用 @SerializedName 映射。
 */

data class AccuLocationResponse(
    @SerializedName("Key") val key: String? = null,
    @SerializedName("LocalizedName") val localizedName: String? = null,
    @SerializedName("Country") val country: AccuNamePart? = null,
    @SerializedName("AdministrativeArea") val administrativeArea: AccuNamePart? = null,
    @SerializedName("GeoPosition") val geoPosition: AccuGeoPosition? = null
) {
    data class AccuNamePart(
        @SerializedName("ID") val id: String? = null,
        @SerializedName("LocalizedName") val localizedName: String? = null
    )

    data class AccuGeoPosition(
        @SerializedName("Latitude") val latitude: Double? = null,
        @SerializedName("Longitude") val longitude: Double? = null
    )
}

data class AccuCurrent(
    @SerializedName("LocalObservationDateTime") val localObservationDateTime: String? = null,
    @SerializedName("EpochTime") val epochTime: Long? = null,
    @SerializedName("WeatherText") val weatherText: String? = null,
    @SerializedName("WeatherIcon") val weatherIcon: Int? = null,
    @SerializedName("Temperature") val temperature: AccuMetricValue? = null,
    @SerializedName("RealFeelTemperature") val realFeelTemperature: AccuMetricValue? = null,
    @SerializedName("RelativeHumidity") val relativeHumidity: Double? = null,
    @SerializedName("Wind") val wind: AccuWind? = null,
    @SerializedName("Pressure") val pressure: AccuMetricValue? = null,
    @SerializedName("UVIndex") val uvIndex: Int? = null,
    @SerializedName("UVIndexText") val uvIndexText: String? = null,
    @SerializedName("CloudCover") val cloudCover: Double? = null,
    @SerializedName("Precip1hr") val precip1hr: AccuMetricValue? = null
)

data class AccuMetricValue(@SerializedName("Metric") val metric: AccuMetric? = null)

data class AccuMetric(
    @SerializedName("Value") val value: Double? = null,
    @SerializedName("Unit") val unit: String? = null
)

data class AccuWind(
    @SerializedName("Direction") val direction: AccuWindDirection? = null,
    @SerializedName("Speed") val speed: AccuMetricValue? = null
)

data class AccuWindDirection(
    @SerializedName("Degrees") val degrees: Double? = null,
    @SerializedName("Localized") val localized: String? = null
)

data class AccuHourly(
    @SerializedName("DateTime") val dateTime: String? = null,
    @SerializedName("EpochDateTime") val epochDateTime: Long? = null,
    @SerializedName("WeatherIcon") val weatherIcon: Int? = null,
    @SerializedName("IconPhrase") val iconPhrase: String? = null,
    @SerializedName("Temperature") val temperature: AccuMetricValue? = null,
    @SerializedName("PrecipitationProbability") val precipitationProbability: Int? = null
)

data class AccuDailyResponse(
    @SerializedName("Headline") val headline: AccuHeadline? = null,
    @SerializedName("DailyForecasts") val dailyForecasts: List<AccuDaily>? = null
) {
    data class AccuHeadline(
        @SerializedName("Text") val text: String? = null,
        @SerializedName("Category") val category: String? = null
    )

    data class AccuDaily(
        @SerializedName("Date") val date: String? = null,
        @SerializedName("EpochDate") val epochDate: Long? = null,
        @SerializedName("Temperature") val temperature: AccuMinMax? = null,
        @SerializedName("Day") val day: AccuDayNight? = null,
        @SerializedName("Night") val night: AccuDayNight? = null,
        @SerializedName("Sun") val sun: AccuSun? = null,
        @SerializedName("Wind") val wind: AccuDailyWind? = null,
        @SerializedName("AirAndPollen") val airAndPollen: List<AccuAirPollen>? = null
    )

    data class AccuMinMax(
        @SerializedName("Minimum") val minimum: AccuMetric? = null,
        @SerializedName("Maximum") val maximum: AccuMetric? = null
    )

    data class AccuDayNight(
        @SerializedName("Icon") val icon: Int? = null,
        @SerializedName("IconPhrase") val iconPhrase: String? = null,
        @SerializedName("PrecipitationProbability") val precipitationProbability: Int? = null
    )

    data class AccuSun(
        @SerializedName("Rise") val rise: String? = null,
        @SerializedName("Set") val set: String? = null
    )

    data class AccuDailyWind(val day: AccuWind? = null, val night: AccuWind? = null)

    data class AccuAirPollen(
        @SerializedName("Name") val name: String? = null,
        @SerializedName("Value") val value: Double? = null,
        @SerializedName("Category") val category: String? = null
    )
}
