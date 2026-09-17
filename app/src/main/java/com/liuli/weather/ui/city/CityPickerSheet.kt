package com.liuli.weather.ui.city

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liuli.weather.data.city.CityDataSource
import com.liuli.weather.data.model.LocationInfo
import com.liuli.weather.data.prefs.SettingsStore
import com.liuli.weather.databinding.SheetCityBinding

/**
 * 城市选择面板：已保存城市（点击切换 / 长按删除）+ 内置城市搜索 + GPS 定位入口。
 */
class CityPickerSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onSelectSaved(index: Int)
        fun onPickCity(city: com.liuli.weather.data.city.City)
        fun onGpsRequested()
        fun onDeleteSaved(index: Int)
    }

    private var _binding: SheetCityBinding? = null
    private val binding get() = _binding!!

    private val settings by lazy { SettingsStore(requireContext()) }
    private lateinit var cityAdapter: CityAdapter

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

        cityAdapter = CityAdapter { city ->
            (activity as? Callback)?.onPickCity(city)
            dismiss()
        }
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

        binding.btnGps.setOnClickListener {
            (activity as? Callback)?.onGpsRequested()
            dismiss()
        }

        rebuildSavedChips()
    }

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
                background = resources.getDrawable(com.liuli.weather.R.drawable.bg_chip, null)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    (activity as? Callback)?.onSelectSaved(index)
                    dismiss()
                }
                setOnLongClickListener {
                    confirmDelete(loc, index)
                    true
                }
            }
            binding.savedChips.addView(chip)
        }
    }

    private fun confirmDelete(loc: LocationInfo, index: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(com.liuli.weather.R.string.delete_city))
            .setMessage(loc.name)
            .setPositiveButton(com.liuli.weather.R.string.action_delete) { _, _ ->
                (activity as? Callback)?.onDeleteSaved(index)
                rebuildSavedChips()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CityPickerSheet"

        fun show(fm: androidx.fragment.app.FragmentManager) {
            CityPickerSheet().show(fm, TAG)
        }
    }
}
