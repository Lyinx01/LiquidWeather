package com.liuli.weather.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.data.model.DailyWeather
import com.liuli.weather.databinding.ItemDailyBinding
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import kotlin.math.roundToInt

class DailyAdapter : ListAdapter<DailyWeather, DailyAdapter.ViewHolder>(Diff) {

    private var globalMin = 0.0
    private var globalMax = 1.0

    /** true = 显示 °F。 */
    var imperialUnits: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), globalMin, globalMax, imperialUnits)
    }

    override fun submitList(list: List<DailyWeather>?) {
        list?.takeIf { it.isNotEmpty() }?.let {
            globalMin = it.minOf { d -> d.tempMin }
            globalMax = it.maxOf { d -> d.tempMax }
        }
        super.submitList(list)
    }

    class ViewHolder(private val binding: ItemDailyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DailyWeather, globalMin: Double, globalMax: Double, imperial: Boolean) {
            val u = com.liuli.weather.util.UnitConverter
            binding.tvWeek.text = TimeUtils.dayLabel(item.date)
            binding.ivIcon.setImageResource(WeatherCodeMapper.iconFor(item.skycon))
            binding.tvMin.text = "${u.displayInt(item.tempMin, imperial)}°"
            binding.tvMax.text = "${u.displayInt(item.tempMax, imperial)}°"
            binding.tempBar.setRange(globalMin, globalMax, item.tempMin, item.tempMax)
            val prob = item.precipProbability
            if (prob != null && prob >= 20) {
                binding.tvProb.visibility = View.VISIBLE
                binding.tvProb.text = "$prob%"
            } else {
                binding.tvProb.visibility = View.INVISIBLE
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DailyWeather>() {
        override fun areItemsTheSame(oldItem: DailyWeather, newItem: DailyWeather) =
            oldItem.date == newItem.date

        override fun areContentsTheSame(oldItem: DailyWeather, newItem: DailyWeather) =
            oldItem == newItem
    }
}
