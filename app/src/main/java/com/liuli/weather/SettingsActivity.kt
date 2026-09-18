package com.liuli.weather

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        binding.sourceRow.setOnClickListener { showSourcePicker() }
        binding.unitsRow.setOnClickListener { showUnitsPicker() }
        refreshSourceLabel()
        refreshUnitsLabel()

        binding.btnSave.setOnClickListener {
            keyDrafts[binding.sourceRow.tag?.toString() ?: store.effectiveSource()] =
                binding.etKey.text.toString()
            store.token = keyDrafts[SettingsStore.SOURCE_CAIYUN]
            store.qwToken = keyDrafts[SettingsStore.SOURCE_QWEATHER]
            store.accuToken = keyDrafts[SettingsStore.SOURCE_ACCU]
            store.owToken = keyDrafts[SettingsStore.SOURCE_OPENWEATHER]
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

    // ---------------------------------------------------------------- labels

    private fun sourceLabel(source: String): String {
        val name = when (source) {
            SettingsStore.SOURCE_CAIYUN -> getString(R.string.source_caiyun)
            SettingsStore.SOURCE_ACCU -> getString(R.string.source_accu)
            SettingsStore.SOURCE_OPENWEATHER -> getString(R.string.source_openweather)
            SettingsStore.SOURCE_QWEATHER -> getString(R.string.source_qweather)
            else -> source
        }
        return if (store.hasTokenForSource(source)) name else "$name（未配置）"
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
        binding.etKey.hint = setText(
            when (source) {
                SettingsStore.SOURCE_QWEATHER -> R.string.settings_qw_hint
                SettingsStore.SOURCE_ACCU -> R.string.settings_accu_hint
                SettingsStore.SOURCE_OPENWEATHER -> R.string.settings_ow_hint
                else -> R.string.settings_token_hint
            }
        )
        binding.tvGetKey.text = setText(
            when (source) {
                SettingsStore.SOURCE_QWEATHER -> R.string.settings_get_qw_key
                SettingsStore.SOURCE_ACCU -> R.string.settings_get_accu_key
                SettingsStore.SOURCE_OPENWEATHER -> R.string.settings_get_ow_key
                else -> R.string.settings_get_token
            }
        )
        binding.etKey.setText(keyDrafts[source] ?: "")

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

    private fun setText(resId: Int): CharSequence = getString(resId)

    private fun refreshUnitsLabel() {
        binding.tvUnitsValue.text =
            if (store.imperialUnits) getString(R.string.units_imperial)
            else getString(R.string.units_metric)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
