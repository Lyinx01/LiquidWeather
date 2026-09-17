package com.liuli.weather.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.data.model.HourlyWeather
import com.liuli.weather.databinding.ItemHourlyBinding
import com.liuli.weather.util.TimeUtils
import com.liuli.weather.util.WeatherCodeMapper
import kotlin.math.roundToInt

class HourlyAdapter : ListAdapter<HourlyWeather, HourlyAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHourlyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == 0)
    }

    class ViewHolder(private val binding: ItemHourlyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HourlyWeather, isFirst: Boolean) {
            binding.tvTime.text = if (isFirst) "现在" else TimeUtils.hourLabel(item.time)
            binding.ivIcon.setImageResource(WeatherCodeMapper.iconFor(item.skycon))
            binding.tvTemp.text = "${item.temperature.roundToInt()}°"
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
