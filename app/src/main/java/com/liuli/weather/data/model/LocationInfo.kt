package com.liuli.weather.data.model

/** 一个天气地点（GPS 定位或手动选择的城市）。 */
data class LocationInfo(
    val name: String,
    val lat: Double,
    val lng: Double,
    val isGps: Boolean = false
) {
    fun sameAs(other: LocationInfo): Boolean =
        Math.abs(lat - other.lat) < 0.02 && Math.abs(lng - other.lng) < 0.02
}
