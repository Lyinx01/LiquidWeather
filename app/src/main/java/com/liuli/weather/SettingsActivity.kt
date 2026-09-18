package com.liuli.weather

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
        binding.etToken.setText(store.token ?: "")
        binding.etAccuToken.setText(store.accuToken ?: "")
        binding.etOwToken.setText(store.owToken ?: "")
        binding.etQwToken.setText(store.qwToken ?: "")

        binding.sourceRow.setOnClickListener { showSourcePicker() }
        refreshSourceLabel()

        binding.btnSave.setOnClickListener {
            store.token = binding.etToken.text.toString()
            store.accuToken = binding.etAccuToken.text.toString()
            store.owToken = binding.etOwToken.text.toString()
            store.qwToken = binding.etQwToken.text.toString()
            Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
            // 保存一定带回 RESULT_OK，主页据此强制刷新以应用新数据源/Key
            setResult(RESULT_OK)
            finish()
        }

        binding.tvGetToken.setOnClickListener { openUrl("https://dashboard.caiyunapp.com/") }
        binding.tvGetQwKey.setOnClickListener { openUrl("https://console.qweather.com/") }
        binding.tvGetAccuKey.setOnClickListener { openUrl("https://developer.accuweather.com/") }
        binding.tvGetOwKey.setOnClickListener { openUrl("https://home.openweathermap.org/api_keys") }
    }

    /** 数据源选择弹窗（breezy 风格）：未配置 Key 的源追加（未配置）提示。 */
    private fun showSourcePicker() {
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
        binding.tvSourceValue.text = sourceLabel(source)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
