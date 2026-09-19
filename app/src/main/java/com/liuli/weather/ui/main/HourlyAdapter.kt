package com.liuli.weather.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.R
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.databinding.ItemHourlyBinding
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import kotlin.math.roundToInt

class HourlyAdapter : ListAdapter<HourlyWeather, HourlyAdapter.ViewHolder>(Diff) {

    /** true = 显示 °F。 */
    var imperialUnits: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHourlyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == 0, imperialUnits)
    }

    class ViewHolder(private val binding: ItemHourlyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HourlyWeather, isFirst: Boolean, imperial: Boolean) {
            // 仅当首格确实是当前小时才标"现在"（AccuWeather 等源首格是整点预报）
            val nowHour = System.currentTimeMillis() / 3_600_000L
            val itemHour = item.time / 3_600_000L
            binding.tvTime.text =
                if (isFirst && nowHour == itemHour) binding.root.context.getString(R.string.time_now)
                else TimeUtils.hourLabel(item.time)
            binding.ivIcon.setImageResource(WeatherCodeMapper.iconFor(item.skycon))
            binding.tvTemp.text =
                "${com.liuli.weather.util.UnitConverter.displayInt(item.temperature, imperial)}°"
            val prob = item.precipProbability
            if (prob != null && prob >= 20) {
                binding.tvProb.visibility = android.view.View.VISIBLE
                binding.tvProb.text = "$prob%"
            } else {
                binding.tvProb.visibility = android.view.View.INVISIBLE
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HourlyWeather>() {
        override fun areItemsTheSame(oldItem: HourlyWeather, newItem: HourlyWeather) =
            oldItem.time == newItem.time

        override fun areContentsTheSame(oldItem: HourlyWeather, newItem: HourlyWeather) =
            oldItem == newItem
    }
}
