package com.liuli.weather

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.databinding.ActivitySettingsBinding
import com.liuli.weather.ui.common.GlassPickerDialog

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsStore

    /** 各数据源的 Key 输入草稿：进入页面时加载，保存时统一写回。 */
    private val keyDrafts = mutableMapOf<String, String>()

    /** 数据源展示顺序与弹窗选项。 */
    private val sourceOrder = listOf(
        SettingsStore.SOURCE_CAIYUN,
        SettingsStore.SOURCE_QWEATHER,
        SettingsStore.SOURCE_ACCU,
        SettingsStore.SOURCE_OPENWEATHER
    )

    /** 主页设置图标的屏幕矩形（圆心 + 尺寸），展开/收起动画的起止位置。 */
    private var srcCx = 0
    private var srcCy = 0
    private var srcW = 0
    private var srcH = 0

    /** 收起动画只跑一次的护栏。 */
    private var closeAnimated = false

    /** 页面根视图的圆角（视图局部像素，随缩放换算屏幕半径）。 */
    private var outlineRadiusPx = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = sys.top + dp(12),
                bottom = sys.bottom + dp(20),
                left = dp(4),
                right = dp(4)
            )
            insets
        }

        store = SettingsStore(this)
        keyDrafts[SettingsStore.SOURCE_CAIYUN] = store.token ?: ""
        keyDrafts[SettingsStore.SOURCE_QWEATHER] = store.qwToken ?: ""
        keyDrafts[SettingsStore.SOURCE_ACCU] = store.accuToken ?: ""
        keyDrafts[SettingsStore.SOURCE_OPENWEATHER] = store.owToken ?: ""
        binding.etQwHost.setText(store.qwHost ?: "")

        binding.sourceRow.setOnClickListener { showSourcePicker() }
        binding.unitsRow.setOnClickListener { showUnitsPicker() }
        binding.languageRow.setOnClickListener { showLanguagePicker() }
        refreshSourceLabel()
        refreshUnitsLabel()
        refreshLanguageLabel()

        binding.btnSave.setOnClickListener { save() }

        // 玻璃卡片嵌在 ScrollView 内，需显式指定采样源为根布局（含渐变装饰背景），
        // 否则默认只捕获透明的直接父容器，看不到折射。
        setupGlassCards()

        com.liuli.weather.ui.common.GlassPressEffect.attach(
            binding.glassSave, null, binding.btnSave
        )

        setupLaunchMorph(savedInstanceState)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * 让设置页所有玻璃卡片采样独立的背景层。
     * 注意不要指向 root —— root 包含玻璃自身，会形成互相捕获并导致崩溃。
     */
    private fun setupGlassCards() {
        val backdrop = binding.settingsBackdrop
        val scrollContent = binding.scroll.getChildAt(0) as? android.view.ViewGroup ?: return
        for (i in 0 until scrollContent.childCount) {
            val child = scrollContent.getChildAt(i)
            if (child is com.example.liquidglass.LiquidGlassView) {
                child.backdropSource = backdrop
            }
        }
        binding.glassSave.backdropSource = backdrop
    }

    // ---------------------------------------------------------------- save

    private fun save() {
        keyDrafts[binding.sourceRow.tag?.toString() ?: store.effectiveSource()] =
            binding.etKey.text.toString()
        store.token = keyDrafts[SettingsStore.SOURCE_CAIYUN]
        store.qwToken = keyDrafts[SettingsStore.SOURCE_QWEATHER]
        store.accuToken = keyDrafts[SettingsStore.SOURCE_ACCU]
        store.owToken = keyDrafts[SettingsStore.SOURCE_OPENWEATHER]
        store.qwHost = binding.etQwHost.text.toString()
        Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    // ------------------------------------------------- iOS 式进出场动画

    /**
     * 从主页设置图标位置放大展开（iOS 应用启动动画）：
     * 窗口透明，动画作用于页面根视图——初始把整页缩到图标大小并定位到图标中心，
     * 随后按 iOS 曲线放大铺满；收起时反向缩回图标并渐隐，主页模糊同步解除。
     * 圆角裁剪跟随缩放：图标大小处近似 iOS 图标的圆角，铺满时为页面圆角。
     */
    private fun setupLaunchMorph(savedInstanceState: Bundle?) {
        srcCx = intent.getIntExtra(EXTRA_SRC_CX, 0)
        srcCy = intent.getIntExtra(EXTRA_SRC_CY, 0)
        srcW = intent.getIntExtra(EXTRA_SRC_W, 0)
        srcH = intent.getIntExtra(EXTRA_SRC_H, 0)
        binding.root.clipToOutline = true
        binding.root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, outlineRadiusPx)
            }
        }
        if (savedInstanceState == null && srcW > 0) {
            // 首帧前隐藏整页，布局完成后从图标位置展开，避免全屏内容闪现
            binding.root.alpha = 0f
            binding.root.post { playMorph(open = true) }
        } else {
            // 语言切换重建（Intent 复带 extras）或来源不明：直接整页呈现
            outlineRadiusPx = dp(28).toFloat()
            binding.root.invalidateOutline()
        }
    }

    private fun playMorph(open: Boolean, onEnd: (() -> Unit)? = null) {
        val root = binding.root
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (w <= 0f || h <= 0f) {
            onEnd?.invoke()
            return
        }

        val fullRadius = dp(28).toFloat()
        val iconRadius = (srcW * 0.28f).coerceAtLeast(fullRadius / 4f)
        val scaleStart = (srcW.toFloat() / w).coerceIn(0.04f, 1f)
        val fromS = if (open) scaleStart else 1f
        val toS = if (open) 1f else scaleStart
        val fromCx = if (open) srcCx.toFloat() else w / 2f
        val toCx = if (open) w / 2f else srcCx.toFloat()
        val fromCy = if (open) srcCy.toFloat() else h / 2f
        val toCy = if (open) h / 2f else srcCy.toFloat()
        val fromR = if (open) iconRadius else fullRadius
        val toR = if (open) fullRadius else iconRadius
        val fromA = if (open) 0.55f else 1f
        val toA = if (open) 1f else 0f

        root.pivotX = 0f
        root.pivotY = 0f
        if (!open) {
            // 收起一开始就通知主页解除模糊，两条动画并行（与 iOS 一致）
            revealMain?.invoke()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (open) 420L else 360L
            interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val s = fromS + (toS - fromS) * t
                root.scaleX = s
                root.scaleY = s
                root.translationX = fromCx + (toCx - fromCx) * t - s * w / 2f
                root.translationY = fromCy + (toCy - fromCy) * t - s * h / 2f
                root.alpha = fromA + (toA - fromA) * t
                outlineRadiusPx = (fromR + (toR - fromR) * t) / s
                root.invalidateOutline()
            }
            doOnEnd {
                if (open) {
                    outlineRadiusPx = fullRadius
                    root.invalidateOutline()
                }
                onEnd?.invoke()
            }
            start()
        }
    }

    override fun finish() {
        if (closeAnimated || binding.root.width <= 0 || srcW <= 0) {
            super.finish()
            return
        }
        closeAnimated = true
        playMorph(open = false) {
            super.finish()
            overridePendingTransition(0, 0)
        }
    }

    // ---------------------------------------------------------------- pickers

    private fun showSourcePicker() {
        keyDrafts[store.effectiveSource()] = binding.etKey.text.toString()

        val labels = sourceOrder.map { sourceLabel(it) }
        val checked = sourceOrder.indexOf(store.effectiveSource()).coerceAtLeast(0)
        GlassPickerDialog.show(
            this,
            getString(R.string.settings_source_label),
            labels,
            checked
        ) { which ->
            store.source = sourceOrder[which]
            refreshSourceLabel()
        }
    }

    private fun showUnitsPicker() {
        GlassPickerDialog.show(
            this,
            getString(R.string.settings_units_label),
            listOf(
                getString(R.string.units_metric),
                getString(R.string.units_imperial)
            ),
            if (store.imperialUnits) 1 else 0
        ) { which ->
            store.imperialUnits = which == 1
            refreshUnitsLabel()
        }
    }

    /** 语言选择：跟随系统 / 简体中文 / 繁體中文 / English / 日本語。 */
    private fun showLanguagePicker() {
        val tags = arrayOf("", "zh-CN", "zh-TW", "en", "ja")
        val labels = listOf(
            getString(R.string.language_system),
            "简体中文", "繁體中文", "English", "日本語"
        )
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checked = tags.indexOfFirst { it.isNotEmpty() && current.startsWith(it, ignoreCase = true) }
            .let { if (it >= 0) it else 0 }

        GlassPickerDialog.show(
            this,
            getString(R.string.settings_language_label),
            labels,
            checked
        ) { which ->
            applyLanguage(tags[which])
        }
    }

    /**
     * 应用语言。
     * AppCompatDelegate 会触发 Activity 重建（Android 13+ 由系统重建，低版本 AppCompat 重建）；
     * 这里不再手动 recreate()，避免二次重建造成的闪屏。
     * 过渡动画由 Theme.LiquidWeather.Settings 的 windowAnimationStyle 统一提供。
     */
    private fun applyLanguage(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        // 缓存里的天气描述是旧语言，标记过期以强制按新语言重新请求
        store.lastSuccessAt = 0L
        com.liuli.weather.widget.WeatherWidgetProvider.updateAll(this)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    // ---------------------------------------------------------------- labels

    private fun sourceLabel(source: String): String {
        val name = when (source) {
            SettingsStore.SOURCE_CAIYUN -> getString(R.string.source_caiyun)
            SettingsStore.SOURCE_ACCU -> getString(R.string.source_accu)
            SettingsStore.SOURCE_OPENWEATHER -> getString(R.string.source_openweather)
            SettingsStore.SOURCE_QWEATHER -> getString(R.string.source_qweather)
            else -> source
        }
        return if (store.hasTokenForSource(source)) name
        else getString(R.string.source_not_configured, name)
    }

    private fun refreshSourceLabel() {
        val source = store.effectiveSource()
        binding.sourceRow.tag = source
        binding.tvSourceValue.text = sourceLabel(source)

        binding.tvKeyLabel.setText(
            when (source) {
                SettingsStore.SOURCE_QWEATHER -> R.string.settings_qw_label
                SettingsStore.SOURCE_ACCU -> R.string.settings_accu_label
                SettingsStore.SOURCE_OPENWEATHER -> R.string.settings_ow_label
                else -> R.string.settings_token_label
            }
        )
        binding.etKey.hint = when (source) {
            SettingsStore.SOURCE_QWEATHER -> getString(R.string.settings_qw_hint)
            SettingsStore.SOURCE_ACCU -> getString(R.string.settings_accu_hint)
            SettingsStore.SOURCE_OPENWEATHER -> getString(R.string.settings_ow_hint)
            else -> getString(R.string.settings_token_hint)
        }
        binding.tvGetKey.text = when (source) {
            SettingsStore.SOURCE_QWEATHER -> getString(R.string.settings_get_qw_key)
            SettingsStore.SOURCE_ACCU -> getString(R.string.settings_get_accu_key)
            SettingsStore.SOURCE_OPENWEATHER -> getString(R.string.settings_get_ow_key)
            else -> getString(R.string.settings_get_token)
        }
        binding.etKey.setText(keyDrafts[source] ?: "")

        binding.qwHostContainer.visibility =
            if (source == SettingsStore.SOURCE_QWEATHER) View.VISIBLE else View.GONE

        binding.tvGetKey.setOnClickListener {
            val url = when (source) {
                SettingsStore.SOURCE_QWEATHER -> "https://console.qweather.com/"
                SettingsStore.SOURCE_ACCU -> "https://developer.accuweather.com/"
                SettingsStore.SOURCE_OPENWEATHER -> "https://home.openweathermap.org/api_keys"
                else -> "https://dashboard.caiyunapp.com/"
            }
            openUrl(url)
        }
    }

    private fun refreshUnitsLabel() {
        binding.tvUnitsValue.text =
            if (store.imperialUnits) getString(R.string.units_imperial)
            else getString(R.string.units_metric)
    }

    /** 当前语言标签：跟随系统时显示"跟随系统"，否则显示语言自称。 */
    private fun refreshLanguageLabel() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        binding.tvLanguageValue.text = when {
            current.isEmpty() -> getString(R.string.language_system)
            current.startsWith("zh", true) &&
                (current.contains("TW", true) || current.contains("HK", true)) -> "繁體中文"
            current.startsWith("zh", true) -> "简体中文"
            current.startsWith("ja", true) -> "日本語"
            else -> "English"
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SRC_CX = "src_cx"
        const val EXTRA_SRC_CY = "src_cy"
        const val EXTRA_SRC_W = "src_w"
        const val EXTRA_SRC_H = "src_h"

        /**
         * 设置页收起动画开始时通知主页解除模糊（进程内跨 Activity 回调）。
         * 由 MainActivity 在 onCreate 注册、onDestroy 注销。
         */
        var revealMain: (() -> Unit)? = null
    }
}
