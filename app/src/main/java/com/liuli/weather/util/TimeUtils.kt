package com.liuli.weather.util

import com.liuli.weather.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 应用当前语言（用于 DateTimeFormatter 的本地化输出）。 */
    private fun locale(): Locale = AppCtx.context().resources.configuration.locales[0]

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

    /** 逐小时/小部件的时刻标签（格式随语言：中文「15时」、英文「3 PM」）。 */
    fun hourLabel(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone)
            .format(DateTimeFormatter.ofPattern(AppCtx.str(R.string.time_hour_pattern), locale()))

    fun clockLabel(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** 今天 / 明天 / 周三…（星期名由系统按语言本地化）。 */
    fun dayLabel(dateMillis: Long): String {
        val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> AppCtx.str(R.string.time_today)
            today.plusDays(1) -> AppCtx.str(R.string.time_tomorrow)
            else -> date.format(DateTimeFormatter.ofPattern("EEE", locale()))
        }
    }
}
