package com.liuli.weather.ui.city

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.liquidglass.LiquidGlassToast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liuli.weather.R
import com.liuli.weather.data.city.CityDataSource
import com.liuli.weather.data.location.LocationRepository
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.databinding.SheetCityBinding
import kotlinx.coroutines.launch

/**
 * 城市管理面板：已保存城市（点击切换 / 长按删除）+ 内置城市搜索 + GPS 定位。
 *
 * 面板自身完成全部持久化与定位，不依赖宿主 Activity 实现回调，因此**任何界面都能调出**
 * （主页顶部胶囊、设置页的「城市管理」行……）。改动通过 [onCityChanged] 广播给订阅者，
 * 由订阅者决定如何刷新（主页据此重载天气）。
 */
class CityPickerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCityBinding? = null
    private val binding get() = _binding!!

    private val settings by lazy { SettingsStore(requireContext()) }
    private lateinit var cityAdapter: CityAdapter

    /** GPS 权限：申请通过后继续定位；被拒时给出提示并保证至少有一个城市。 */
    private val gpsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            fetchGpsLocation()
        } else {
            glassToast(getString(R.string.permission_denied), LiquidGlassToast.LENGTH_LONG)
            ensureDefaultLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetCityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        cityAdapter = CityAdapter { city -> pickCity(city) }
        binding.rvCities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCities.adapter = cityAdapter

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                cityAdapter.submitList(CityDataSource.search(requireContext(), s?.toString() ?: ""))
            }
        })
        cityAdapter.submitList(CityDataSource.load(requireContext()))

        binding.btnGps.setOnClickListener { requestGps() }

        rebuildSavedChips()
    }

    // ------------------------------------------------------------ 城市操作

    private fun rebuildSavedChips() {
        val saved = settings.locations()
        val current = settings.currentIndex()
        binding.savedContainer.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
        binding.savedChips.removeAllViews()
        saved.forEachIndexed { index, loc ->
            val chip = TextView(requireContext()).apply {
                text = if (index == current) "● ${loc.name}" else loc.name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = resources.getDrawable(R.drawable.bg_chip, null)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { selectSaved(index) }
                setOnLongClickListener {
                    confirmDelete(loc, index)
                    true
                }
            }
            binding.savedChips.addView(chip)
        }
    }

    private fun selectSaved(index: Int) {
        if (settings.selectLocation(index)) {
            dismiss()
            notifyCityChanged()
        } else {
            dismiss()
        }
    }

    private fun pickCity(city: com.liuli.weather.data.city.City) {
        settings.addOrSelectLocation(LocationInfo(city.name, city.lat, city.lng))
        dismiss()
        notifyCityChanged()
    }

    private fun requestGps() {
        if (LocationRepository.hasPermission(requireContext())) {
            fetchGpsLocation()
        } else {
            gpsPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    private fun fetchGpsLocation() {
        val appContext = context?.applicationContext ?: return
        // 用宿主 Activity 的 lifecycleScope，而不是 viewLifecycleOwner：
        // 下面会立刻 dismiss()，view 的生命周期随之销毁、其 scope 会被取消，
        // 而定位最长要等 15s，挂上去会导致协程中途被取消、定位永远不返回。
        val host = activity ?: return
        dismiss()
        host.lifecycleScope.launch {
            val loc = LocationRepository.getCurrentLocation(appContext)
            if (loc == null) {
                glassToast(host, getString(R.string.locate_failed))
            } else {
                settings.addOrSelectLocation(loc)
                notifyCityChanged()
                glassToast(host, getString(R.string.city_updated, loc.name), withCheck = true)
            }
        }
    }

    /**
     * 定位被拒绝且一个城市都没有时，保证界面仍有数据可显示（退回默认城市）。
     * 与原主页逻辑一致，现在由面板自己兜底，任何入口调出都成立。
     */
    private fun ensureDefaultLocation() {
        if (settings.locations().isNotEmpty()) return
        settings.addOrSelectLocation(
            LocationInfo(getString(R.string.city_default), DEFAULT_LAT, DEFAULT_LNG)
        )
        notifyCityChanged()
    }

    private fun confirmDelete(loc: LocationInfo, index: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_city))
            .setMessage(loc.name)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                if (settings.removeLocation(index)) {
                    rebuildSavedChips()
                    notifyCityChanged()
                    glassToast(getString(R.string.city_deleted))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun notifyCityChanged() {
        listeners.toList().forEach { it() }
    }

    /** 面板自身用的短提示（面板还活着时）。 */
    private fun glassToast(
        text: CharSequence,
        duration: Int = LiquidGlassToast.LENGTH_SHORT,
        withCheck: Boolean = false
    ) {
        val host = activity ?: return
        glassToast(host, text, duration, withCheck)
    }

    /**
     * 与主页同款的玻璃提示条：采样宿主窗口内容，文字颜色按表面亮度自适应。
     * 显式接收 Activity，便于面板已 dismiss 后仍能在宿主上弹出提示。
     */
    private fun glassToast(
        host: android.app.Activity,
        text: CharSequence,
        duration: Int = LiquidGlassToast.LENGTH_SHORT,
        withCheck: Boolean = false
    ) {
        val toast = LiquidGlassToast.makeText(host, text, duration)
            .setIconResource(if (withCheck) R.drawable.ic_check else 0)
            .setGravity(
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL,
                0,
                dp(84)
            )
        host.window.decorView
            .findViewById<android.view.ViewGroup>(android.R.id.content)
            ?.let { toast.glass.backdropSource = it }
        toast.glass.apply {
            blurAmount = 0.6f
            refractionHeight = dp(16).toFloat()
            bevelWidth = dp(18).toFloat()
            edgeSoftness = dp(4).toFloat()
            saturation = 150f
        }
        com.liuli.weather.ui.common.GlassTextTone.adaptTextColorToSurface(toast)
        toast.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CityPickerSheet"

        /**
         * 城市变更订阅（进程内，广播式）。
         *
         * 这里刻意用「多订阅者 + 显式退订」而不是单个可空回调：主页与设置页都能调出
         * 这个面板，若用单个字段，后注册的一方会把前一方覆盖掉。
         * 订阅方在 onResume 注册、onPause 退订（后台界面无需即时刷新，回来时自然重载）。
         */
        private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

        fun addCityChangedListener(listener: () -> Unit) {
            listeners.addIfAbsent(listener)
        }

        fun removeCityChangedListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        /**
         * 从任意 FragmentManager 调出城市管理面板。
         * 重复调用（面板已在显示）时直接忽略，避免 IllegalStateException。
         */
        fun show(fm: androidx.fragment.app.FragmentManager) {
            if (fm.findFragmentByTag(TAG) != null) return
            CityPickerSheet().show(fm, TAG)
        }

        private val LOCATION_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /** 默认城市（北京）坐标，与主页兜底逻辑保持一致。 */
        private const val DEFAULT_LAT = 39.9042
        private const val DEFAULT_LNG = 116.4074
    }
}
