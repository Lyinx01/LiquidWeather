package com.liuli.weather.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.data.model.WeatherAlert
import com.liuli.weather.databinding.ItemAlertBinding

class AlertAdapter : ListAdapter<WeatherAlert, AlertAdapter.ViewHolder>(Diff) {

    /** 玻璃默认只采样直接父容器，需由外部注入根布局作为背景来源。 */
    var backdropSource: View? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), backdropSource)
    }

    class ViewHolder(private val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var expanded = false

        fun bind(item: WeatherAlert, backdrop: View?) {
            binding.tvAlertTitle.text = item.title
            binding.tvAlertDesc.text = item.description
            binding.tvAlertDesc.maxLines = if (expanded) Int.MAX_VALUE else 3
            binding.glassAlert.backdropSource = backdrop
            binding.glassAlert.glassTint = tintFor(item.title)
            binding.glassAlert.setOnClickListener {
                expanded = !expanded
                binding.tvAlertDesc.maxLines = if (expanded) Int.MAX_VALUE else 3
            }
        }

        /**
         * 根据预警级别给玻璃卡片染色。
         * 预警标题由 API 按当前语言返回，因此颜色词需覆盖多语言
         * （中/繁/英/日），未命中时用默认灰蓝。
         */
        private fun tintFor(title: String): Int = when {
            title.matchesAny(RED_WORDS) -> 0x66F44336
            title.matchesAny(ORANGE_WORDS) -> 0x66FF9800
            title.matchesAny(YELLOW_WORDS) -> 0x66FFC107
            title.matchesAny(BLUE_WORDS) -> 0x662196F3
            else -> 0x4D607D8B
        }

        private fun String.matchesAny(words: Array<String>): Boolean =
            words.any { contains(it, ignoreCase = true) }

        private companion object {
            val RED_WORDS = arrayOf(
                "红色", "紅", "红", "Red", "赤", "特別警報", "特别警报"
            )
            val ORANGE_WORDS = arrayOf(
                "橙色", "橙", "Orange", "大雨", "洪水", "土砂災害"
            )
            val YELLOW_WORDS = arrayOf(
                "黄色", "黃", "黄", "Yellow", "注意報", "注意报"
            )
            val BLUE_WORDS = arrayOf(
                "蓝色", "藍", "蓝", "Blue", "青色", "台风", "颱風", "Typhoon"
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<WeatherAlert>() {
        override fun areItemsTheSame(oldItem: WeatherAlert, newItem: WeatherAlert) =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: WeatherAlert, newItem: WeatherAlert) =
            oldItem == newItem
    }
}
