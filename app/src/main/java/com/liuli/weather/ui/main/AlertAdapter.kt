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

        /** 根据预警级别（标题中的颜色词）给玻璃卡片染色。 */
        private fun tintFor(title: String): Int = when {
            title.contains("红色") -> 0x66F44336
            title.contains("橙色") -> 0x66FF9800
            title.contains("黄色") -> 0x66FFC107
            title.contains("蓝色") -> 0x662196F3
            else -> 0x4D607D8B
        }
    }

    private object Diff : DiffUtil.ItemCallback<WeatherAlert>() {
        override fun areItemsTheSame(oldItem: WeatherAlert, newItem: WeatherAlert) =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: WeatherAlert, newItem: WeatherAlert) =
            oldItem == newItem
    }
}
