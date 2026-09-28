package com.liuli.weather

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.liquidglass.LiquidGlassToast
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
import com.liuli.weather.ui.common.GlassTextTone
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
        listOf(binding.glassTopbar, binding.glassBtnRefresh, binding.glassBtnSettings)
    }

    // 内容卡片：静止时开启动态采样以折射飘移的云层，滚动时关闭保证流畅
    private val cardGlasses: List<LiquidGlassView> by lazy {
        listOf(binding.glassHourly, binding.glassDaily, binding.glassDetails, binding.glassAqi)
    }
    private val glassIdleHandler = Handler(Looper.getMainLooper())
    private val glassIdleRunnable = Runnable { onScrollIdle() }
    private var scrolling = false

    /** 按住中的玻璃数量：>0 时抑制降频，保持动态采样让折射跟随缩放。 */
    private var pressedGlassCount = 0

    /** 刷新图标的旋转动画：spin = 匀速整圈循环；settle = 结束时补完当前一圈归位。 */
    private var spinAnimator: ObjectAnimator? = null
    private var settleAnimator: ObjectAnimator? = null

    // 打开设置时的主页过渡：整体模糊 + 压暗（iOS 应用启动时主屏幕的退隐效果）
    private lateinit var transitionScrim: View
    private var transitionAnimator: ValueAnimator? = null
    private var currentBlurPx = 0f

    // 液态玻璃错误提示条（替代 Snackbar）
    private var glassBar: com.liuli.weather.databinding.ViewGlassBarBinding? = null
    private var glassBarView: View? = null
    private val barHandler = Handler(Looper.getMainLooper())
    private var barDismissRunnable: Runnable? = null
    private var barEnterRunnable: Runnable? = null
    private var topInsetPx = 0
    private var bottomInsetPx = 0

    /** 卡片低频刷新：只做 invalidate，库在背景哈希变化时才真正重采，避免逐帧全量采样。 */
    private val cardRefreshRunnable = object : Runnable {
        override fun run() {
            cardGlasses.forEach { it.invalidate() }
            if (backgroundAnimating && !scrolling) {
                glassIdleHandler.postDelayed(this, CARD_REFRESH_INTERVAL_MS)
            }
        }
    }

    // 背景动画（云层飘移/星空闪烁/雨丝下落/光晕呼吸）
    private val backgroundAnimators = mutableListOf<android.animation.Animator>()
    private var backgroundAnimating = true

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            requestGpsLocation()
        } else {
            glassToast(getString(R.string.permission_denied), duration = LiquidGlassToast.LENGTH_LONG)
            viewModel.ensureDefaultLocation()
        }
    }

    /**
     * 玻璃 toast。直接采样主页内容（GPU 透镜管线，库已修成环）。
     * 文字颜色按「文字正后方那一层」的实际亮度自适应（显示前同步测量，首帧即正确）。
     */
    /**
     * 玻璃 toast。直接采样主页内容（GPU 透镜管线，库已修成环）。
     * 文字颜色按「toast 除文字外的实际渲染表面」亮度自适应（入场完成后测量，无闪变）。
     */
    private fun glassToast(
        text: CharSequence,
        withCheck: Boolean = false,
        duration: Int = LiquidGlassToast.LENGTH_SHORT
    ) {
        val toast = LiquidGlassToast.makeText(this, text, duration)
            .setIconResource(if (withCheck) R.drawable.ic_check else 0)
            .setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, dp(84))
        val contentHost = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        toast.glass.backdropSource = contentHost
        // GPU 透镜管线（经内容快照隔离），与主页卡片同档次
        toast.glass.apply {
            blurAmount = 0.6f
            refractionHeight = dp(16).toFloat()
            bevelWidth = dp(18).toFloat()
            edgeSoftness = dp(4).toFloat()
            saturation = 150f
        }
        GlassTextTone.adaptTextColorToSurface(toast)
        toast.show()    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置里可能改了数据源或 Key，保存后强制刷新；提示条在主页上呈现
            // （设置页保存后立即 finish，挂在它窗口里的提示会跟着销毁）
            glassToast(getString(R.string.token_saved), withCheck = true)
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

        setupTransitionScrim()
        // 设置页收起动画开始时回调，主页同步解除模糊（进程内跨 Activity 通知）
        SettingsActivity.revealMain = { revealMainFromTransition() }

        setupInsets()
        setupGlass()
        setupLists()
        setupTopBar()
        setupBackgroundAnimations()
        observe()

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
            topInsetPx = sys.top
            bottomInsetPx = sys.bottom
            val topLp = binding.glassTopbar.layoutParams as FrameLayout.LayoutParams
            topLp.topMargin = sys.top + dp(10)
            binding.glassTopbar.layoutParams = topLp

            val refreshLp = binding.glassBtnRefresh.layoutParams as FrameLayout.LayoutParams
            refreshLp.bottomMargin = sys.bottom + dp(16)
            binding.glassBtnRefresh.layoutParams = refreshLp

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
     * 玻璃折射分两类，采样策略按滚动状态切换：
     * - 悬浮控件（顶栏/底部按钮）折射滚到下方的实时内容（iOS 导航栏效果），
     *   采样源为含滚动内容的根布局 mainRoot；动态采样只在滚动期间开启。
     * - 内容卡片采样天空背景层 bgContainer；静止时开启动态采样以实时折射
     *   飘移的云层，滚动期间关闭（此时库用缓存位图做位移映射，避免逐帧重采）。
     */
    private fun setupGlass() {
        floatingGlasses.forEach { it.backdropSource = binding.mainRoot }
        cardGlasses.forEach { it.backdropSource = binding.bgContainer }
        // 光谱描边：色散观感的安全实现（内容零通道分离，见 GlassSpectralRim 注释）
        com.liuli.weather.ui.common.GlassSpectralRim.attach(
            *(floatingGlasses + cardGlasses).toTypedArray()
        )

        binding.scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            onScrollStarted()
        }
    }

    /** 滚动中：悬浮控件动态采样（实时折射内容），卡片关闭动态保流畅。 */
    private fun onScrollStarted() {
        scrolling = true
        glassIdleHandler.removeCallbacks(glassIdleRunnable)
        glassIdleHandler.postDelayed(glassIdleRunnable, GLASS_IDLE_DELAY_MS)
        applyGlassDynamicPolicy()
    }

    /** 滚动停止/数据刷新后：恢复静止策略（卡片动态折射飘移云层）。 */
    private fun onScrollIdle() {
        scrolling = false
        if (pressedGlassCount > 0) {
            // 按住期间保持动态采样（折射跟随缩放），松手后自然降频
            glassIdleHandler.postDelayed(glassIdleRunnable, GLASS_IDLE_DELAY_MS)
            return
        }
        applyGlassDynamicPolicy()
    }

    /** 悬浮玻璃按压状态变化：按住期间保持动态采样，折射实时跟随缩放动画。 */
    private fun onGlassPressed(down: Boolean) {
        if (down) {
            pressedGlassCount++
            scrolling = false
            glassIdleHandler.removeCallbacks(glassIdleRunnable)
            floatingGlasses.forEach {
                it.enableDynamicBackground = true
                it.invalidate()
            }
        } else {
            pressedGlassCount = (pressedGlassCount - 1).coerceAtLeast(0)
            if (pressedGlassCount == 0) {
                // 回弹动画期间仍保持动态采样，动画结束后恢复常规降频策略
                glassIdleHandler.removeCallbacks(glassIdleRunnable)
                glassIdleHandler.postDelayed(glassIdleRunnable, PRESS_RELEASE_KEEP_MS)
            }
        }
    }

    private fun applyGlassDynamicPolicy() {
        val animating = backgroundAnimating
        // 悬浮控件：仅滚动期间逐帧采样（实时折射滚过下方的内容）
        floatingGlasses.forEach { it.enableDynamicBackground = scrolling && animating }
        // 内容卡片：始终不做逐帧采样，改用低频 invalidate 跟随背景动画
        cardGlasses.forEach { it.enableDynamicBackground = false }
        (floatingGlasses + cardGlasses).forEach { it.invalidate() }

        glassIdleHandler.removeCallbacks(cardRefreshRunnable)
        if (animating && !scrolling) {
            glassIdleHandler.postDelayed(cardRefreshRunnable, CARD_REFRESH_INTERVAL_MS)
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

        // 预警玻璃卡片同样采样天空背景
        alertAdapter.backdropSource = binding.bgContainer
    }

    // ------------------------------------------------- background animations

    /** 云层缓慢飘移、星空/光晕呼吸、雨丝下落；暂停时全部冻结省电。 */
    private fun setupBackgroundAnimations() {
        val screenW = resources.displayMetrics.widthPixels.toFloat()

        // 动画图层走硬件层：alpha/位移由 GPU 合成，避免每帧软件重绘整屏
        listOf(
            binding.cloud1, binding.cloud2, binding.cloud3,
            binding.starsOverlay, binding.rainOverlay, binding.glowOverlay
        ).forEach { it.setLayerType(View.LAYER_TYPE_HARDWARE, null) }

        // 云层横向飘移（周期很长，营造缓慢流动感）
        backgroundAnimators += driftAnimator(binding.cloud1, -700f, screenW + 250f, 200_000L)
        backgroundAnimators += driftAnimator(binding.cloud2, screenW + 300f, -800f, 260_000L)
        backgroundAnimators += driftAnimator(binding.cloud3, -600f, screenW + 200f, 320_000L)

        // 星空闪烁（透明度呼吸）
        backgroundAnimators += ObjectAnimator.ofFloat(
            binding.starsOverlay, View.ALPHA, 0.45f, 0.95f
        ).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }

        // 太阳光晕呼吸
        backgroundAnimators += ObjectAnimator.ofFloat(
            binding.glowOverlay, View.ALPHA, 0.5f, 0.95f
        ).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }

        // 雨丝下落（纵向循环滚动）
        backgroundAnimators += ObjectAnimator.ofFloat(
            binding.rainOverlay, View.TRANSLATION_Y, -screenW * 0.5f, screenW * 0.5f
        ).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        backgroundAnimators.forEach { it.start() }
    }

    private fun driftAnimator(
        view: View,
        from: Float,
        to: Float,
        duration: Long
    ): ObjectAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, from, to).apply {
        this.duration = duration
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }

    /** 按天气切换动态背景层的可见性。 */
    private fun updateBackgroundLayers(skycon: String?) {
        val code = skycon ?: "CLEAR_DAY"
        val isNight = WeatherCodeMapper.isNight(code)
        val isRain = code.contains("RAIN") || code == "THUNDER_SHOWER"
        val isClear = code == "CLEAR_DAY"

        binding.starsOverlay.visibility = if (isNight) View.VISIBLE else View.GONE
        binding.rainOverlay.visibility = if (isRain) View.VISIBLE else View.GONE
        binding.glowOverlay.visibility = if (isClear) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        // 冻结背景动画并关闭所有动态采样，避免后台耗电
        backgroundAnimating = false
        backgroundAnimators.forEach { it.pause() }
        glassIdleHandler.removeCallbacks(cardRefreshRunnable)
        floatingGlasses.forEach { it.enableDynamicBackground = false }
        cardGlasses.forEach { it.enableDynamicBackground = false }
    }

    override fun onResume() {
        super.onResume()
        backgroundAnimating = true
        backgroundAnimators.forEach { it.resume() }
        applyGlassDynamicPolicy()
        // 兜底：设置页关闭后回到前台时解除过渡模糊（正常路径由 revealMain 回调触发）
        if (currentBlurPx > 0f) {
            revealMainFromTransition()
        }
    }

    private fun setupTopBar() {
        // 左下：刷新
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnSettings.setOnClickListener { openSettings() }
        // 顶部城市胶囊：城市管理
        binding.tvTitle.setOnClickListener {
            CityPickerSheet.show(supportFragmentManager)
        }

        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassTopbar, { onGlassPressed(it) }, binding.tvTitle
        )
        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassBtnRefresh, { onGlassPressed(it) }, binding.btnRefresh
        )
        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassBtnSettings, { onGlassPressed(it) }, binding.btnSettings
        )
    }

    /**
     * 刷新中：左下角按钮的图标匀速旋转。
     * 结束（成功或失败都一样）时不瞬间回正，而是按原角速度补完当前这一圈，
     * 末帧自然落在与静态一致的 0°，视觉上连贯收住。
     */
    private fun setRefreshing(refreshing: Boolean) {
        if (refreshing) {
            // 上一次的收尾动画若还在转，直接取消并从当前角度续转，避免跳变
            settleAnimator?.cancel()
            settleAnimator = null
            if (spinAnimator?.isRunning == true) return
            val from = binding.btnRefresh.rotation
            spinAnimator = ObjectAnimator.ofFloat(
                binding.btnRefresh, View.ROTATION, from, from + 360f
            ).apply {
                duration = SPIN_PERIOD_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }.also { it.start() }
        } else {
            val spin = spinAnimator
            if (spin?.isRunning != true) return
            val current = binding.btnRefresh.rotation % 360f
            spin.cancel()
            spinAnimator = null
            val remain = 360f - current
            if (remain < 2f || remain > 358f) {
                // 已在归位角度附近，直接静止
                binding.btnRefresh.rotation = 0f
                return
            }
            settleAnimator?.cancel()
            settleAnimator = ObjectAnimator.ofFloat(
                binding.btnRefresh, View.ROTATION, current, current + remain
            ).apply {
                // 与旋转同角速度，保持速度连续
                duration = (SPIN_PERIOD_MS * remain / 360f).toLong().coerceIn(60L, SPIN_PERIOD_MS)
                interpolator = LinearInterpolator()
                doOnEnd {
                    binding.btnRefresh.rotation = 0f
                    if (settleAnimator === it) settleAnimator = null
                }
            }.also { it.start() }
        }
    }

    // ---------------------------------------------------------------- observe

    private fun observe() {
        viewModel.state.observe(this) { st ->
            setRefreshing(st is MainUiState.Loading)
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
        updateBackgroundLayers(w.current.skycon)
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

        // 同步刷新桌面小部件（数据刚更新，直接读缓存渲染）
        com.liuli.weather.widget.WeatherWidgetProvider.updateAll(this)

        // 数据更新后让玻璃的折射立即刷新一次
        applyGlassDynamicPolicy()
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
            showGlassBar(st.message, getString(R.string.action_retry)) { viewModel.refresh() }
        }
    }

    // ------------------------------------------------- 液态玻璃错误提示条

    /**
     * 液态玻璃提示条：替代 Material Snackbar。
     * 视图**常驻复用**（首次显示时创建一次，之后只改内容与可见性）——
     * 每次动态 inflate/remove 在 Android 17 上会触发 removeFromArray NPE；
     * 从底部滑入，停留约 3 秒后自动滑出，点击动作按钮立即收起并执行回调。
     */
    private fun showGlassBar(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        val bar = ensureGlassBar()
        bar.tvBarMessage.text = message
        if (actionLabel != null && action != null) {
            bar.tvBarAction.visibility = View.VISIBLE
            bar.tvBarAction.text = actionLabel
            bar.tvBarAction.setOnClickListener {
                dismissGlassBar()
                action()
            }
        } else {
            bar.tvBarAction.visibility = View.GONE
        }
        bar.root.visibility = View.VISIBLE
        // 入场动画（iOS 浮窗弹簧：从屏幕顶部边缘外滑入 + 轻微放大过冲）；
        // dismiss 时会撤掉它，避免与退出动画在同一 ViewPropertyAnimator 上竞争
        barEnterRunnable?.let { bar.root.removeCallbacks(it) }
        val enter = Runnable {
            bar.root.translationY = -(bar.root.height + dp(40)).toFloat()
            bar.root.alpha = 0f
            bar.root.scaleX = 0.92f
            bar.root.scaleY = 0.92f
            bar.root.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(460L)
                .setInterpolator(com.example.liquidglass.IOSSpringInterpolator(0.46f, 0.82f))
                .start()
        }
        barEnterRunnable = enter
        bar.root.post(enter)
        val dismiss = Runnable { dismissGlassBar() }
        barDismissRunnable = dismiss
        barHandler.postDelayed(dismiss, BAR_AUTO_DISMISS_MS)
    }

    /** 创建（仅一次）并常驻挂载玻璃条视图；采样用内容快照隔离（GPU 透镜管线） */
    private fun ensureGlassBar(): com.liuli.weather.databinding.ViewGlassBarBinding {
        glassBar?.let { return it }
        val bar = com.liuli.weather.databinding.ViewGlassBarBinding.inflate(layoutInflater)
        // 内容快照采样：直接采样主页内容（GPU 透镜管线，库已修成环）；置于屏幕顶部
        bar.root.backdropSource = binding.mainRoot
        // 条身消费触摸：防止穿透到下方玻璃触发按压重录，点条身也可收起
        bar.root.isClickable = true
        bar.root.setOnClickListener { dismissGlassBar() }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP
            setMargins(dp(16), topInsetPx + dp(74), dp(16), 0)
        }
        binding.mainRoot.addView(bar.root, lp)
        bar.root.visibility = View.GONE
        glassBar = bar
        glassBarView = bar.root
        return bar
    }

    private fun dismissGlassBar() {
        barDismissRunnable?.let { barHandler.removeCallbacks(it) }
        barDismissRunnable = null
        barEnterRunnable?.let { r -> glassBarView?.removeCallbacks(r) }
        barEnterRunnable = null
        val bar = glassBar ?: return
        glassBar = null
        bar.tvBarAction.setOnClickListener(null)
        // 常驻视图：滑出动画后转 GONE（iOS 式干脆收走：上滑 + 轻微缩小 + 加速）
        bar.root.animate()
            .translationY(-(bar.root.height + dp(40)).toFloat())
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(180L)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.8f))
            .withEndAction {
                bar.root.visibility = View.GONE
                bar.root.translationY = 0f
                bar.root.alpha = 1f
                bar.root.scaleX = 1f
                bar.root.scaleY = 1f
            }
            .start()
    }

    // ---------------------------------------------------------------- actions

    private fun openSettings() {
        // 记录设置图标的屏幕位置，设置页按 iOS 启动式从该位置放大展开
        val loc = IntArray(2)
        binding.glassBtnSettings.getLocationOnScreen(loc)
        val intent = Intent(this, SettingsActivity::class.java).putExtras(
            Bundle().apply {
                putInt(SettingsActivity.EXTRA_SRC_CX, loc[0] + binding.glassBtnSettings.width / 2)
                putInt(SettingsActivity.EXTRA_SRC_CY, loc[1] + binding.glassBtnSettings.height / 2)
                putInt(SettingsActivity.EXTRA_SRC_W, binding.glassBtnSettings.width)
                putInt(SettingsActivity.EXTRA_SRC_H, binding.glassBtnSettings.height)
            }
        )
        blurMainForTransition()
        // 系统窗口动画关掉，展开完全由应用内动画呈现
        val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        settingsLauncher.launch(intent, options)
    }

    // ------------------------------------------------- settings transition

    /** 过渡用的压暗层：盖在 mainRoot 最上层，动画结束即隐藏。 */
    private fun setupTransitionScrim() {
        transitionScrim = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            visibility = View.GONE
        }
        binding.mainRoot.addView(
            transitionScrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** 主页整体模糊 + 压暗，与设置页展开动画同步（速率曲线同 iOS）。 */
    private fun blurMainForTransition() {
        transitionAnimator?.cancel()
        transitionScrim.visibility = View.VISIBLE
        val blurTo = dp(22).toFloat()
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = com.liuli.weather.ui.common.Motion.iosAppLaunch
            addUpdateListener {
                val t = it.animatedValue as Float
                applyMainBlur(blurTo * t)
                transitionScrim.alpha = 0.22f * t
            }
            start()
        }
    }

    /** 解除主页模糊与压暗；设置页开始缩回图标时由其回调触发，两条动画并行。 */
    private fun revealMainFromTransition() {
        transitionAnimator?.cancel()
        val from = currentBlurPx
        if (from <= 0.5f) {
            transitionScrim.visibility = View.GONE
            return
        }
        val blurMax = dp(22).toFloat()
        transitionAnimator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = 380L
            interpolator = com.liuli.weather.ui.common.Motion.iosAppLaunch
            addUpdateListener {
                val v = it.animatedValue as Float
                applyMainBlur(v)
                transitionScrim.alpha = 0.22f * (v / blurMax)
                if (v <= 0.5f) transitionScrim.visibility = View.GONE
            }
            start()
        }
    }

    private fun applyMainBlur(radiusPx: Float) {
        currentBlurPx = radiusPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.mainRoot.setRenderEffect(
                if (radiusPx >= 0.5f) {
                    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
    }

    private fun requestGpsLocation() {
        if (!LocationRepository.hasPermission(this)) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
            return
        }
        lifecycleScope.launch {
            setRefreshing(true)
            val loc = LocationRepository.getCurrentLocation(this@MainActivity)
            setRefreshing(false)
            if (loc == null) {
                glassToast(getString(R.string.locate_failed))
            } else {
                viewModel.setCurrentLocation(loc)
                glassToast(getString(R.string.city_updated, loc.name), withCheck = true)
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
        glassToast(getString(R.string.city_deleted))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        glassIdleHandler.removeCallbacks(glassIdleRunnable)
        backgroundAnimators.forEach { it.cancel() }
        backgroundAnimators.clear()
        transitionAnimator?.cancel()
        SettingsActivity.revealMain = null
        barHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /** 滚动停止多久后关闭悬浮玻璃的动态采样（降频节能）。 */
        private const val GLASS_IDLE_DELAY_MS = 250L

        /** 静止时内容卡片的刷新间隔（背景动画跟随，兼顾观感与耗电）。 */
        private const val CARD_REFRESH_INTERVAL_MS = 250L

        /** 松开按压后保持动态采样的时长（覆盖回弹动画，之后降频）。 */
        private const val PRESS_RELEASE_KEEP_MS = 420L

        /** 刷新图标转一整圈的周期。 */
        private const val SPIN_PERIOD_MS = 900L

        /** 玻璃提示条自动消失的停留时长。 */
        private const val BAR_AUTO_DISMISS_MS = 3200L
    }
}
