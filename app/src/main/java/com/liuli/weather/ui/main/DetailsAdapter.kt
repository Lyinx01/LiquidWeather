package com.liuli.weather.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.data.model.DetailItem
import com.liuli.weather.databinding.ItemDetailBinding

class DetailsAdapter : ListAdapter<DetailItem, DetailsAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DetailItem) {
            binding.ivIcon.setImageResource(item.icon)
            binding.tvLabel.text = item.label
            binding.tvValue.text = item.value
        }
    }

    private object Diff : DiffUtil.ItemCallback<DetailItem>() {
        override fun areItemsTheSame(oldItem: DetailItem, newItem: DetailItem) =
            oldItem.label == newItem.label

        override fun areContentsTheSame(oldItem: DetailItem, newItem: DetailItem) =
            oldItem == newItem
    }
}
