package com.liuli.weather.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** "2024-05-01T14:00+08:00" -> epoch millis，解析失败回退当前时间。 */
    fun parseIsoMillis(dt: String?): Long {
        if (dt.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            OffsetDateTime.parse(dt).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(dt).atZone(zone).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    /** "2024-05-01" 或 "2024-05-01T00:00+08:00" -> 当天 0 点的 epoch millis。 */
    fun parseDateMillis(date: String?): Long {
        if (date.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            // 兼容带时间部分的 ISO 串，只取前 10 位日期
            LocalDate.parse(date.take(10)).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun hourLabel(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("H时"))

    fun clockLabel(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** 今天 / 明天 / 周一… */
    fun dayLabel(dateMillis: Long): String {
        val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "周一"
                DayOfWeek.TUESDAY -> "周二"
                DayOfWeek.WEDNESDAY -> "周三"
                DayOfWeek.THURSDAY -> "周四"
                DayOfWeek.FRIDAY -> "周五"
                DayOfWeek.SATURDAY -> "周六"
                else -> "周日"
            }
        }
    }
}
