package com.liuli.weather.data.remote

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------- hourly

data class CaiyunHourlyResponse(
    val status: String? = null,
    val description: String? = null,
    val result: HourlyResult? = null
) {
    data class HourlyResult(val hourly: Hourly? = null)

    data class Hourly(
        val status: String? = null,
        val description: String? = null,
        val temperature: List<StepValue>? = null,
        val skycon: List<StepSkycon>? = null,
        @SerializedName("precipitation_2h") val precipitation2h: List<StepPrecip>? = null,
        val wind: List<StepWind>? = null,
        val humidity: List<StepValue>? = null
    )

    data class StepValue(val datetime: String? = null, val value: Double? = null)

    data class StepPrecip(
        val datetime: String? = null,
        val value: Double? = null,
        val probability: Int? = null
    )

    data class StepSkycon(
        val datetime: String? = null,
        val value: String? = null,
        val key: String? = null,
        @SerializedName("key_name") val keyName: String? = null
    )

    data class StepWind(
        val datetime: String? = null,
        val speed: Double? = null,
        val direction: String? = null,
        @SerializedName("direction_deg") val directionDeg: Double? = null
    )
}

// ---------------------------------------------------------------- daily

data class CaiyunDailyResponse(
    val status: String? = null,
    val description: String? = null,
    val result: DailyResult? = null
) {
    data class DailyResult(val daily: Daily? = null)

    data class Daily(
        val status: String? = null,
        val temperature: List<DailyTemp>? = null,
        @SerializedName("skycon_08h_20h") val skycon08h20h: List<StepSkyconX>? = null,
        val skycon: List<StepSkyconX>? = null,
        val precipitation: List<DailyPrecip>? = null,
        val wind: List<DailyWind>? = null,
        val astronomical: List<Astro>? = null,
        @SerializedName("life_index") val lifeIndex: DailyLifeIndex? = null,
        val aqi: List<DailyAqi>? = null
    )

    data class DailyTemp(val date: String? = null, val max: Double? = null, val min: Double? = null, val avg: Double? = null)

    data class StepSkyconX(
        val date: String? = null,
        val value: String? = null,
        val key: String? = null,
        @SerializedName("key_name") val keyName: String? = null
    )

    data class DailyPrecip(
        val date: String? = null,
        val avg: Double? = null,
        val max: Double? = null,
        val min: Double? = null,
        @SerializedName("probability_08h_20h") val probability08h20h: Int? = null,
        @SerializedName("probability_20h_32h") val probability20h32h: Int? = null
    )

    /** v2.6 中每日风速是 {"speed":..,"direction":..} 对象。 */
    data class DailyWind(
        val date: String? = null,
        val max: WindSpeed? = null,
        val min: WindSpeed? = null,
        val avg: WindSpeed? = null
    )

    data class WindSpeed(val speed: Double? = null, val direction: String? = null)

    data class Astro(val date: String? = null, val sunrise: SunTime? = null, val sunset: SunTime? = null)

    data class SunTime(val time: String? = null)

    data class DailyLifeIndex(
        val ultraviolet: List<LifeIndexItem>? = null,
        val comfort: List<LifeIndexItem>? = null
    )

    data class LifeIndexItem(val date: String? = null, val index: String? = null, val desc: String? = null)

    data class DailyAqi(val date: String? = null, val avg: Double? = null, val min: Double? = null, val max: Double? = null)
}

// ---------------------------------------------------------------- alert

data class CaiyunAlertResponse(
    val status: String? = null,
    val description: String? = null,
    val result: AlertResult? = null
) {
    data class AlertResult(val alert: Alert? = null)

    data class Alert(val status: String? = null, val content: List<AlertItem>? = null)

    data class AlertItem(
        val title: String? = null,
        val description: String? = null,
        val source: String? = null,
        val status: String? = null,
        val pubtimestamp: Long? = null
    )
}
