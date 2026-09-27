package com.liuli.weather.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.liuli.weather.R
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

    fun hasToken(): Boolean = settings.hasTokenForSource(settings.effectiveSource())

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

    /** 定位被拒绝且没有任何城市时，退回默认城市（北京）。 */
    fun ensureDefaultLocation() {
        if (settings.locations().isEmpty()) {
            settings.addOrSelectLocation(LocationInfo(getApplication<Application>().getString(R.string.city_default), 39.9042, 116.4074))
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
            }
            // 手动刷新时发出 Loading 驱动刷新图标旋转（观察端只转图标、不清界面）；
            // 有缓存的路径此前从不发 Loading，导致手动刷新看不到旋转动画
            if (forceRefresh || cached == null) {
                _state.value = MainUiState.Loading
            }
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val fresh = repo.getWeather(loc)
                if (forceRefresh) {
                    // 网络过快时补足最短展示时长，保证旋转至少接近一整圈
                    val remain = MIN_SPIN_MS - (android.os.SystemClock.elapsedRealtime() - started)
                    if (remain > 0) kotlinx.coroutines.delay(remain)
                }
                _state.value = MainUiState.Success(fresh, fromCache = false)
            } catch (e: Exception) {
                val noToken = !settings.hasTokenForSource(settings.effectiveSource())
                _state.value = MainUiState.Error(humanMessage(e), cached, noToken)
            }
        }
    }

    private fun humanMessage(e: Exception): String {
        val res = getApplication<Application>().resources
        return when {
            e is com.liuli.weather.data.remote.ApiException ->
                e.message ?: res.getString(R.string.err_load_failed)
            e is UnknownHostException -> res.getString(R.string.err_network)
            e is retrofit2.HttpException -> when (e.code()) {
                401, 403 -> res.getString(R.string.err_invalid_key)
                429 -> res.getString(R.string.err_rate_limit)
                503 -> res.getString(R.string.err_quota)
                else -> res.getString(R.string.err_server, e.code())
            }
            else -> e.message ?: res.getString(R.string.err_load_failed)
        }
    }

    companion object {
        private const val STALE_MS = 30 * 60 * 1000L

        /** 手动刷新时旋转指示的最短展示时长（图标转一整圈约 900ms）。 */
        private const val MIN_SPIN_MS = 800L
    }
}
