package com.liuli.weather.util

import kotlin.math.roundToInt

/** 温度单位换算：领域模型统一存摄氏度，展示时按用户设置转换。 */
object UnitConverter {

    fun cToDisplay(celsius: Double, imperial: Boolean): Double =
        if (imperial) celsius * 9.0 / 5.0 + 32.0 else celsius

    fun displayInt(celsius: Double, imperial: Boolean): Int =
        cToDisplay(celsius, imperial).roundToInt()

    fun unitLabel(imperial: Boolean): String = if (imperial) "°F" else "°C"
}
