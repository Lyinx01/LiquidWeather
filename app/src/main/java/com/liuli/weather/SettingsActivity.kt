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
import kotlin.math.hypot

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

    // 圆形揭示裁剪的当前状态（根视图坐标，根视图本身不做变换）
    private var revealCircleMode = false
    private var revealCx = 0f
    private var revealCy = 0f
    private var revealR = 0f

    /** 非圆形模式（整页呈现）下的圆角。 */
    private var rectRadiusPx = 0f

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
     * 从主页设置图标做圆形揭示（iOS 应用启动动画的圆形态）：
     * 窗口透明、根视图不变形，用 ViewOutlineProvider 在根视图上裁出一个圆——
     * 展开时圆从设置图标的位置与大小开始，按 iOS 曲线扩张到覆盖全屏；
     * 收起时圆从全屏缩回图标并渐隐。起止的圆与图标完全重合，两个方向一致。
     * 揭示期间页面内容以图标为轴心做轻微缩放（1.06 -> 1.0），增加纵深而保持原布局。
     */
    private fun setupLaunchMorph(savedInstanceState: Bundle?) {
        srcCx = intent.getIntExtra(EXTRA_SRC_CX, 0)
        srcCy = intent.getIntExtra(EXTRA_SRC_CY, 0)
        srcW = intent.getIntExtra(EXTRA_SRC_W, 0)
        srcH = intent.getIntExtra(EXTRA_SRC_H, 0)
        rectRadiusPx = dp(28).toFloat()
        binding.root.clipToOutline = true
        binding.root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (revealCircleMode) {
                    val r = revealR
                    outline.setRoundRect(
                        (revealCx - r).toInt(), (revealCy - r).toInt(),
                        (revealCx + r).toInt(), (revealCy + r).toInt(),
                        r
                    )
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, rectRadiusPx)
                }
            }
        }
        if (savedInstanceState == null && srcW > 0) {
            // 首帧前隐藏整页，布局完成后从图标圆形展开，避免全屏内容闪现
            binding.root.alpha = 0f
            binding.root.post { playReveal(open = true) }
        } else {
            // 语言切换重建（Intent 复带 extras）或来源不明：直接整页呈现
            revealCircleMode = false
            binding.root.invalidateOutline()
        }
    }

    /** 把揭示期间的内容缩放与位移应用到页面的两个直接子层（背景层 + 滚动内容）。 */
    private fun applyContentScale(scale: Float) {
        listOf(binding.settingsBackdrop, binding.scroll).forEach { child ->
            child.pivotX = srcCx.toFloat()
            child.pivotY = srcCy.toFloat()
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    private fun playReveal(open: Boolean, onEnd: (() -> Unit)? = null) {
        val root = binding.root
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (w <= 0f || h <= 0f || srcW <= 0) {
            if (!open) {
                revealCircleMode = false
                root.invalidateOutline()
                applyContentScale(1f)
                root.alpha = 1f
            }
            onEnd?.invoke()
            return
        }

        // 圆心 = 图标中心；起始半径 = 图标外接圆；终止半径 = 覆盖全屏的最小圆
        val cx = srcCx.toFloat().coerceIn(0f, w)
        val cy = srcCy.toFloat().coerceIn(0f, h)
        val rStart = (maxOf(srcW, srcH) / 2f + dp(2)).coerceAtLeast(dp(8).toFloat())
        val rEnd = maxOf(
            hypot(cx, cy), hypot(w - cx, cy),
            hypot(cx, h - cy), hypot(w - cx, h - cy)
        ) + dp(2)
        val fromR = if (open) rStart else rEnd
        val toR = if (open) rEnd else rStart
        val fromA = if (open) 0.85f else 1f
        val toA = if (open) 1f else 0f
        val fromS = if (open) 1.06f else 1f
        val toS = if (open) 1f else 1.06f

        // 收起从"圆盖全屏"起跳，与矩形模式无视觉差异；展开结束切回矩形模式
        revealCircleMode = true
        revealCx = cx
        revealCy = cy
        revealR = fromR
        root.invalidateOutline()

        if (!open) {
            // 收起一开始就通知主页解除模糊，两条动画并行（与 iOS 一致）
            revealMain?.invoke()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (open) 460L else 380L
            interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                revealR = fromR + (toR - fromR) * t
                root.invalidateOutline()
                root.alpha = fromA + (toA - fromA) * t
                applyContentScale(fromS + (toS - fromS) * t)
            }
            doOnEnd {
                if (open) {
                    // 圆已覆盖全屏，切回整页矩形模式，后续帧无裁剪痕迹
                    revealCircleMode = false
                    revealR = 0f
                    root.alpha = 1f
                    applyContentScale(1f)
                }
                root.invalidateOutline()
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
        playReveal(open = false) {
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
