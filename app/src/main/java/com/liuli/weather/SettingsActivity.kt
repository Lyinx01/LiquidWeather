package com.liuli.weather

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.databinding.ActivitySettingsBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(
                top = sys.top + (24 * resources.displayMetrics.density).toInt(),
                bottom = sys.bottom + (24 * resources.displayMetrics.density).toInt(),
                left = sys.left + (20 * resources.displayMetrics.density).toInt(),
                right = sys.right + (20 * resources.displayMetrics.density).toInt()
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

        binding.btnSave.setOnClickListener {
            keyDrafts[binding.sourceRow.tag?.toString() ?: store.effectiveSource()] =
                binding.etKey.text.toString()
            store.token = keyDrafts[SettingsStore.SOURCE_CAIYUN]
            store.qwToken = keyDrafts[SettingsStore.SOURCE_QWEATHER]
            store.accuToken = keyDrafts[SettingsStore.SOURCE_ACCU]
            store.owToken = keyDrafts[SettingsStore.SOURCE_OPENWEATHER]
            store.qwHost = binding.etQwHost.text.toString()
            Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
            // 保存一定带回 RESULT_OK，主页据此强制刷新以应用新数据源/Key/单位
            setResult(RESULT_OK)
            finish()
        }
    }

    // ---------------------------------------------------------------- pickers

    private fun showSourcePicker() {
        // 切换前把当前输入框内容写回草稿
        keyDrafts[store.effectiveSource()] = binding.etKey.text.toString()

        val labels = sourceOrder.map { sourceLabel(it) }.toTypedArray()
        val checked = sourceOrder.indexOf(store.effectiveSource()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_source_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                store.source = sourceOrder[which]
                refreshSourceLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnitsPicker() {
        val labels = arrayOf(
            getString(R.string.units_metric),
            getString(R.string.units_imperial)
        )
        val checked = if (store.imperialUnits) 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_units_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                store.imperialUnits = which == 1
                refreshUnitsLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 语言选择：跟随系统 / 简体中文 / 繁體中文 / English / 日本語。 */
    private fun showLanguagePicker() {
        val tags = arrayOf("", "zh-CN", "zh-TW", "en", "ja")
        val labels = arrayOf(
            getString(R.string.language_system),
            "简体中文", "繁體中文", "English", "日本語"
        )
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checked = tags.indexOfFirst { it.isNotEmpty() && current.startsWith(it, ignoreCase = true) }
            .let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                applyLanguage(tags[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 应用语言：空串=跟随系统；切换后天气描述需按新语言重取。 */
    private fun applyLanguage(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        // 缓存里的天气描述是旧语言，标记过期以强制按新语言重新请求
        store.lastSuccessAt = 0L
        // 桌面小部件同步按新语言重绘
        com.liuli.weather.widget.WeatherWidgetProvider.updateAll(this)
        // 系统应用语言是异步生效的，稍等片刻再重建界面，避免仍显示旧语言
        binding.root.postDelayed({ recreate() }, 300L)
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

    /** 当前语言标签：跟随系统时显示“跟随系统”，否则显示语言自称。 */
    private fun refreshLanguageLabel() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        binding.tvLanguageValue.text = when {
            current.isEmpty() -> getString(R.string.language_system)
            current.startsWith("zh", true) && (current.contains("TW", true) || current.contains("HK", true)) -> "繁體中文"
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
}
