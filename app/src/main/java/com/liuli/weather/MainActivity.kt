package com.liuli.weather

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.liquidglass.LiquidGlassView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.liuli.weather.data.city.City
import com.liuli.weather.data.location.LocationRepository
import com.liuli.weather.data.model.AqiInfo
import com.liuli.weather.data.model.DetailItem
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.model.Weather
import com.liuli.weather.databinding.ActivityMainBinding
import com.liuli.weather.ui.city.CityPickerSheet
import com.liuli.weather.ui.main.AlertAdapter
import com.liuli.weather.ui.main.DailyAdapter
import com.liuli.weather.ui.main.DetailsAdapter
import com.liuli.weather.ui.main.HourlyAdapter
import com.liuli.weather.ui.main.MainUiState
import com.liuli.weather.ui.main.MainViewModel
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.UnitConverter
import com.liuli.weather.util.WeatherCodeMapper
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), CityPickerSheet.Callback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    /** 读单位等展示配置用（SharedPreferences 轻量实例）。 */
    private val settings by lazy { com.liuli.weather.data.prefs.SettingsStore(this) }

    private val hourlyAdapter = HourlyAdapter()
    private val dailyAdapter = DailyAdapter()
    private val detailsAdapter = DetailsAdapter()
    private val alertAdapter = AlertAdapter()

    // 悬浮玻璃的动态采样只在滚动/数据更新期间开启，静止后自动降频关闭
    private val floatingGlasses: List<LiquidGlassView> by lazy {
        listOf(binding.glassTopbar, binding.glassBtnLocation, binding.glassBtnSettings)
    }
    private val glassIdleHandler = Handler(Looper.getMainLooper())
    private val glassIdleRunnable = Runnable { setFloatingGlassDynamic(false) }
    private var floatingGlassDynamic = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            requestGpsLocation()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            viewModel.ensureDefaultLocation()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置里可能改了数据源或 Key，保存后强制刷新
            viewModel.refresh()
        } else {
            viewModel.refreshIfStale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupGlass()
        setupLists()
        setupTopBar()
        observe()

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        if (!viewModel.hasLocations()) {
            if (LocationRepository.hasPermission(this)) {
                requestGpsLocation()
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        } else {
            viewModel.refreshIfStale()
        }
    }

    // ---------------------------------------------------------------- setup

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topLp = binding.glassTopbar.layoutParams as FrameLayout.LayoutParams
            topLp.topMargin = sys.top + dp(10)
            binding.glassTopbar.layoutParams = topLp

            val locationLp = binding.glassBtnLocation.layoutParams as FrameLayout.LayoutParams
            locationLp.bottomMargin = sys.bottom + dp(16)
            binding.glassBtnLocation.layoutParams = locationLp

            val settingsLp = binding.glassBtnSettings.layoutParams as FrameLayout.LayoutParams
            settingsLp.bottomMargin = sys.bottom + dp(16)
            binding.glassBtnSettings.layoutParams = settingsLp

            binding.scroll.updatePadding(
                top = sys.top + dp(118),
                left = sys.left + dp(16),
                right = sys.right + dp(16),
                bottom = sys.bottom + dp(104)
            )
            insets
        }
    }

    /**
     * 玻璃折射分两类：
     * - 悬浮控件折射滚到下方的实时内容（iOS 导航栏效果）：采样源为含滚动内容的
     *   根布局 mainRoot；动态采样只在滚动期间及数据刷新后短暂开启，静止即降频关闭，
     *   避免每帧全屏重采的持续开销。
     * - 内容卡片磨砂静态天空背景（bg_container）：库缓存背景位图，滚动仅做位移映射。
     */
    private fun setupGlass() {
        listOf(
            binding.glassTopbar,
            binding.glassBtnLocation,
            binding.glassBtnSettings
        ).forEach { glass: LiquidGlassView ->
            glass.backdropSource = binding.mainRoot
        }
        listOf(
            binding.glassHourly,
            binding.glassDaily,
            binding.glassDetails,
            binding.glassAqi
        ).forEach { glass: LiquidGlassView ->
            glass.backdropSource = binding.bgContainer
        }

        binding.scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            onGlassActivity()
        }
    }

    /** 有滚动/内容变化：立即开启动态采样，并重置静止降频计时。 */
    private fun onGlassActivity() {
        setFloatingGlassDynamic(true)
        glassIdleHandler.removeCallbacks(glassIdleRunnable)
        glassIdleHandler.postDelayed(glassIdleRunnable, GLASS_IDLE_DELAY_MS)
    }

    private fun setFloatingGlassDynamic(enabled: Boolean) {
        if (floatingGlassDynamic == enabled) return
        floatingGlassDynamic = enabled
        floatingGlasses.forEach { it.enableDynamicBackground = enabled }
        if (enabled) {
            floatingGlasses.forEach { it.invalidate() }
        }
    }

    private fun setupLists() {
        binding.rvHourly.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHourly.adapter = hourlyAdapter

        binding.rvDaily.layoutManager = LinearLayoutManager(this)
        binding.rvDaily.adapter = dailyAdapter
        binding.rvDaily.isNestedScrollingEnabled = false

        binding.rvDetails.layoutManager = GridLayoutManager(this, 2)
        binding.rvDetails.adapter = detailsAdapter
        binding.rvDetails.isNestedScrollingEnabled = false

        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvAlerts.adapter = alertAdapter
        binding.rvAlerts.isNestedScrollingEnabled = false
        binding.rvAlerts.visibility = View.GONE

        // 预警玻璃卡片同样只采样静态天空背景
        alertAdapter.backdropSource = binding.bgContainer
    }

    private fun setupTopBar() {
        binding.btnLocations.setOnClickListener {
            CityPickerSheet.show(supportFragmentManager)
        }
        // 长按底部按钮：直接 GPS 定位添加/切换城市
        binding.btnLocations.setOnLongClickListener {
            requestGpsLocation()
            true
        }
        binding.btnSettings.setOnClickListener { openSettings() }
        binding.tvTitle.setOnClickListener {
            CityPickerSheet.show(supportFragmentManager)
        }

        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassTopbar, binding.tvTitle
        )
        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassBtnLocation, binding.btnLocations
        )
        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassBtnSettings, binding.btnSettings
        )
    }

    // ---------------------------------------------------------------- observe

    private fun observe() {
        viewModel.state.observe(this) { st ->
            binding.swipeRefresh.isRefreshing = st is MainUiState.Loading
            when (st) {
                is MainUiState.Idle -> showIdle()
                is MainUiState.Loading -> Unit
                is MainUiState.Success -> render(st.weather)
                is MainUiState.Error -> {
                    st.cached?.let { render(it) }
                    showError(st)
                }
            }
        }
    }

    private fun showIdle() {
        setCardsVisible(false)
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = getString(R.string.idle_hint)
    }

    private fun render(w: Weather) {
        val imperial = settings?.imperialUnits == true
        binding.bgSky.setBackgroundResource(WeatherCodeMapper.backgroundFor(w.current.skycon))
        binding.tvTitle.text = w.location.name
        binding.tvTemp.text = "${UnitConverter.displayInt(w.current.temperature, imperial)}°"
        binding.tvCondition.text = w.current.skyconName
        val today = w.daily.firstOrNull()
        binding.tvTempRange.text = today?.let {
            getString(
                R.string.temp_range,
                UnitConverter.displayInt(it.tempMax, imperial),
                UnitConverter.displayInt(it.tempMin, imperial)
            )
        } ?: ""
        binding.tvUpdated.text = getString(R.string.updated_at, TimeUtils.clockLabel(w.fetchedAt))
        binding.tvError.visibility = View.GONE

        setCardsVisible(true)

        hourlyAdapter.submitList(w.hourly)
        dailyAdapter.submitList(w.daily)
        hourlyAdapter.imperialUnits = imperial
        dailyAdapter.imperialUnits = imperial

        detailsAdapter.submitList(buildDetails(w))
        renderAqi(w.current.aqi)

        alertAdapter.submitList(w.alerts)
        binding.rvAlerts.visibility =
            if (w.alerts.isEmpty()) View.GONE else View.VISIBLE

        // 数据更新后让悬浮玻璃的折射短暂动态刷新一次
        onGlassActivity()
    }

    private fun setCardsVisible(visible: Boolean) {
        listOf(
            binding.glassHourly,
            binding.glassDaily,
            binding.glassDetails
        ).forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (!visible) {
            binding.glassAqi.visibility = View.GONE
            binding.spaceAqi.visibility = View.GONE
            binding.rvAlerts.visibility = View.GONE
        }
    }

    private fun buildDetails(w: Weather): List<DetailItem> {
        val c = w.current
        val imperial = settings?.imperialUnits == true
        val d0 = w.daily.firstOrNull()
        val items = mutableListOf(
            DetailItem(R.drawable.ic_d_temp, getString(R.string.detail_feels_like),
                "${UnitConverter.displayInt(c.apparentTemperature, imperial)}°"),
            DetailItem(R.drawable.ic_d_humidity, getString(R.string.detail_humidity),
                "${(c.humidity * 100).roundToInt()}%"),
            DetailItem(R.drawable.ic_d_wind, getString(R.string.detail_wind),
                "${c.windDirection} ${c.windSpeed.roundToInt()}km/h"),
            DetailItem(R.drawable.ic_d_pressure, getString(R.string.detail_pressure),
                "${c.pressure.roundToInt()} hPa"),
            DetailItem(R.drawable.ic_d_cloud, getString(R.string.detail_cloud),
                "${(c.cloudRate * 100).roundToInt()}%"),
            DetailItem(R.drawable.ic_d_uv, getString(R.string.detail_uv),
                c.uvDesc ?: "--"),
            DetailItem(R.drawable.ic_d_comfort, getString(R.string.detail_comfort),
                c.comfortDesc ?: "--"),
            DetailItem(R.drawable.ic_d_precip, getString(R.string.detail_precip),
                c.precipIntensity?.let { String.format(Locale.US, "%.2f mm/h", it) } ?: "--")
        )
        // 免费版彩云接口不含天文数据（astronomical 为空数组），此时隐藏日出/日落格
        if (d0?.sunrise != null || d0?.sunset != null) {
            items.add(DetailItem(R.drawable.ic_d_sunrise, getString(R.string.detail_sunrise),
                d0?.sunrise ?: "--"))
            items.add(DetailItem(R.drawable.ic_d_sunset, getString(R.string.detail_sunset),
                d0?.sunset ?: "--"))
        }
        return items
    }

    private fun renderAqi(aqi: AqiInfo?) {
        // 彩云对海外坐标不提供空气质量数据（返回全 0 + "缺数据"），此时隐藏卡片
        if (aqi == null || aqi.aqi <= 0) {
            binding.glassAqi.visibility = View.GONE
            binding.spaceAqi.visibility = View.GONE
            return
        }
        binding.glassAqi.visibility = View.VISIBLE
        binding.spaceAqi.visibility = View.VISIBLE
        binding.aqiValue.text = "${aqi.aqi}"
        val (level, color) = WeatherCodeMapper.aqiLevel(aqi.aqi)
        binding.aqiValue.setTextColor(color)
        binding.aqiDesc.text = aqi.description?.takeIf { it.isNotBlank() } ?: level
        binding.aqiPollutants.text = listOf(
            aqi.pm25?.let { "PM2.5 ${formatNum(it)}" },
            aqi.pm10?.let { "PM10 ${formatNum(it)}" },
            aqi.o3?.let { "O₃ ${formatNum(it)}" },
            aqi.no2?.let { "NO₂ ${formatNum(it)}" },
            aqi.so2?.let { "SO₂ ${formatNum(it)}" },
            aqi.co?.let { "CO ${formatNum(it)}" }
        ).filterNotNull().joinToString("   ")
    }

    private fun formatNum(v: Double): String = String.format(Locale.US, "%.0f", v)

    private fun showError(st: MainUiState.Error) {
        if (st.cached == null) {
            setCardsVisible(false)
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = st.message
        }
        if (st.noToken) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.need_token_title)
                .setMessage(R.string.need_token_msg)
                .setPositiveButton(R.string.action_go_settings) { _, _ -> openSettings() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            Snackbar.make(binding.mainRoot, st.message, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry) { viewModel.refresh() }
                .show()
        }
    }

    // ---------------------------------------------------------------- actions

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun requestGpsLocation() {
        if (!LocationRepository.hasPermission(this)) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
            return
        }
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            val loc = LocationRepository.getCurrentLocation(this@MainActivity)
            binding.swipeRefresh.isRefreshing = false
            if (loc == null) {
                Toast.makeText(this@MainActivity, R.string.locate_failed, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setCurrentLocation(loc)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.city_updated, loc.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ------------------------------------------------- CityPickerSheet.Callback

    override fun onSelectSaved(index: Int) {
        viewModel.selectLocation(index)
    }

    override fun onPickCity(city: City) {
        viewModel.setCurrentLocation(LocationInfo(city.name, city.lat, city.lng))
    }

    override fun onGpsRequested() {
        requestGpsLocation()
    }

    override fun onDeleteSaved(index: Int) {
        viewModel.removeLocation(index)
        Toast.makeText(this, R.string.city_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        glassIdleHandler.removeCallbacks(glassIdleRunnable)
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /** 滚动停止多久后关闭悬浮玻璃的动态采样（降频节能）。 */
        private const val GLASS_IDLE_DELAY_MS = 250L
    }
}
