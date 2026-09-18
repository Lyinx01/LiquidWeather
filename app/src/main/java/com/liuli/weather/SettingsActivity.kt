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
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

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

        val store = SettingsStore(this)
        binding.etToken.setText(store.token ?: "")
        binding.etAccuToken.setText(store.accuToken ?: "")
        if (store.effectiveSource() == SettingsStore.SOURCE_ACCU) {
            binding.rbAccu.isChecked = true
        } else {
            binding.rbCaiyun.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            store.source =
                if (binding.rbAccu.isChecked) SettingsStore.SOURCE_ACCU
                else SettingsStore.SOURCE_CAIYUN
            store.token = binding.etToken.text.toString()
            store.accuToken = binding.etAccuToken.text.toString()
            Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
            // 保存一定带回 RESULT_OK，主页据此强制刷新以应用新数据源/Key
            setResult(RESULT_OK)
            finish()
        }

        binding.tvGetToken.setOnClickListener { openUrl("https://dashboard.caiyunapp.com/") }
        binding.tvGetAccuKey.setOnClickListener { openUrl("https://developer.accuweather.com/") }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
