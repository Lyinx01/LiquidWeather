package com.liuli.weather.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liuli.weather.data.model.LocationInfo

/** Token、已保存城市列表等本地配置存储。 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("liquid_weather", Context.MODE_PRIVATE)
    private val gson = Gson()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_TOKEN, value?.trim()).apply()

    var accuToken: String?
        get() = prefs.getString(KEY_ACCU_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_ACCU_TOKEN, value?.trim()).apply()

    var owToken: String?
        get() = prefs.getString(KEY_OW_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_OW_TOKEN, value?.trim()).apply()

    var qwToken: String?
        get() = prefs.getString(KEY_QW_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_QW_TOKEN, value?.trim()).apply()

    var source: String
        get() = prefs.getString(KEY_SOURCE, SOURCE_CAIYUN) ?: SOURCE_CAIYUN
        set(value) = prefs.edit().putString(KEY_SOURCE, value).apply()

    fun effectiveSource(): String = source

    /** 当前数据源是否已配置 Key。 */
    fun hasTokenForSource(source: String): Boolean = when (source) {
        SOURCE_ACCU -> accuToken != null
        SOURCE_OPENWEATHER -> owToken != null
        SOURCE_QWEATHER -> qwToken != null
        else -> token != null
    }

    var lastSuccessAt: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()

    fun locations(): List<LocationInfo> {
        val json = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocationInfo>>() {}.type
            gson.fromJson<List<LocationInfo>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun currentIndex(): Int {
        val index = prefs.getInt(KEY_CURRENT, 0)
        return if (index in locations().indices) index else 0
    }

    fun currentLocation(): LocationInfo? {
        val list = locations()
        if (list.isEmpty()) return null
        return list[currentIndex().coerceIn(0, list.size - 1)]
    }

    fun saveLocations(list: List<LocationInfo>) {
        prefs.edit().putString(KEY_LOCATIONS, gson.toJson(list)).apply()
    }

    fun setCurrentIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT, index).apply()
    }

    /** 已存在则切换，否则插入到最前面并选中。返回是否发生了变化。 */
    fun addOrSelectLocation(loc: LocationInfo): Boolean {
        val list = locations().toMutableList()
        val existing = list.indexOfFirst { it.sameAs(loc) }
        return if (existing >= 0) {
            if (currentIndex() == existing) false
            else {
                setCurrentIndex(existing)
                true
            }
        } else {
            list.add(0, loc)
            saveLocations(list)
            setCurrentIndex(0)
            true
        }
    }

    /** 切换到第 index 个城市，返回是否成功。 */
    fun selectLocation(index: Int): Boolean {
        val list = locations()
        if (index !in list.indices || index == currentIndex()) return false
        setCurrentIndex(index)
        return true
    }

    /** 删除第 index 个城市（至少保留一个），返回是否成功。 */
    fun removeLocation(index: Int): Boolean {
        val list = locations().toMutableList()
        if (index !in list.indices || list.size <= 1) return false
        val removedIndex = index
        list.removeAt(removedIndex)
        saveLocations(list)
        val cur = currentIndex()
        setCurrentIndex(
            when {
                removedIndex < cur -> (cur - 1).coerceIn(0, list.size - 1)
                removedIndex == cur -> cur.coerceIn(0, list.size - 1)
                else -> cur
            }
        )
        return true
    }

    /** true = 英制（°F），false = 公制（°C）。 */
    var imperialUnits: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_IMPERIAL, value).apply()

    /** 把 AccuWeather 解析出的位置 Key 缓存到对应城市，避免重复消耗定位配额。 */
    fun updateLocationAccuKey(loc: LocationInfo, key: String): Boolean {
        val list = locations().toMutableList()
        val idx = list.indexOfFirst { it.sameAs(loc) }
        if (idx < 0 || list[idx].accuKey == key) return false
        list[idx] = list[idx].copy(accuKey = key)
        saveLocations(list)
        return true
    }

    companion object {
        const val SOURCE_CAIYUN = "caiyun"
        const val SOURCE_ACCU = "accu"
        const val SOURCE_OPENWEATHER = "openweather"
        const val SOURCE_QWEATHER = "qweather"

        private const val KEY_TOKEN = "caiyun_token"
        private const val KEY_ACCU_TOKEN = "accu_token"
        private const val KEY_OW_TOKEN = "ow_token"
        private const val KEY_QW_TOKEN = "qw_token"
        private const val KEY_SOURCE = "weather_source"
        private const val KEY_IMPERIAL = "imperial_units"
        private const val KEY_LOCATIONS = "saved_locations"
        private const val KEY_CURRENT = "current_index"
        private const val KEY_LAST_SUCCESS = "last_success_at"
    }
}
