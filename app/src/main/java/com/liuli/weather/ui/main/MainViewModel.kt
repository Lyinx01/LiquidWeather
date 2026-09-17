package com.liuli.weather.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.data.repository.WeatherRepository
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore(application)
    private val repo = WeatherRepository(application, settings)

    private val _state = MutableLiveData<MainUiState>()
    val state: LiveData<MainUiState> = _state

    private val _locations = MutableLiveData<List<LocationInfo>>()
    val locations: LiveData<List<LocationInfo>> = _locations

    val currentLocation: LocationInfo?
        get() = settings.currentLocation()

    fun hasLocations(): Boolean = settings.locations().isNotEmpty()

    fun hasToken(): Boolean = settings.token != null

    init {
        _locations.value = settings.locations()
        val current = settings.currentLocation()
        if (current == null) {
            _state.value = MainUiState.Idle
        } else {
            load(forceRefresh = false)
        }
    }

    fun refresh() = load(forceRefresh = true)

    /** 冷启动 / 回到前台时，缓存超过 30 分钟则刷新。 */
    fun refreshIfStale() {
        if (settings.currentLocation() == null) return
        val stale = System.currentTimeMillis() - settings.lastSuccessAt > STALE_MS
        if (stale) load(forceRefresh = true)
    }

    fun setCurrentLocation(loc: LocationInfo) {
        settings.addOrSelectLocation(loc)
        _locations.value = settings.locations()
        load(forceRefresh = true)
    }

    fun selectLocation(index: Int) {
        if (settings.selectLocation(index)) {
            _locations.value = settings.locations()
            load(forceRefresh = true)
        }
    }

    fun removeLocation(index: Int) {
        if (settings.removeLocation(index)) {
            _locations.value = settings.locations()
            load(forceRefresh = false)
        }
    }

    /** 定位被拒绝且没有任何城市时，退回默认城市。 */
    fun ensureDefaultLocation() {
        if (settings.locations().isEmpty()) {
            settings.addOrSelectLocation(LocationInfo("北京", 39.9042, 116.4074))
            _locations.value = settings.locations()
            load(forceRefresh = false)
        }
    }

    private fun load(forceRefresh: Boolean) {
        val loc = settings.currentLocation() ?: run {
            _state.value = MainUiState.Idle
            return
        }
        viewModelScope.launch {
            val cached = repo.cachedWeather(loc)
            if (cached != null) {
                _state.value = MainUiState.Success(cached, fromCache = true)
                val fresh = System.currentTimeMillis() - cached.fetchedAt < STALE_MS
                if (!forceRefresh && fresh) return@launch
            } else {
                _state.value = MainUiState.Loading
            }
            try {
                _state.value = MainUiState.Success(repo.getWeather(loc), fromCache = false)
            } catch (e: Exception) {
                _state.value = MainUiState.Error(humanMessage(e), cached, settings.token == null)
            }
        }
    }

    private fun humanMessage(e: Exception): String = when {
        e is com.liuli.weather.data.remote.ApiException -> e.message ?: "接口返回错误"
        e is UnknownHostException -> "网络连接失败，请检查网络"
        e is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> "Token 无效或已过期，请在设置中检查"
            429 -> "请求过于频繁，请稍后再试"
            else -> "服务器错误（${e.code()}）"
        }
        else -> e.message ?: "加载失败"
    }

    companion object {
        private const val STALE_MS = 30 * 60 * 1000L
    }
}
