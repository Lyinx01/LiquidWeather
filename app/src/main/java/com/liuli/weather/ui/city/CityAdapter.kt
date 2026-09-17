package com.liuli.weather.ui.city

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liuli.weather.data.city.City
import com.liuli.weather.databinding.ItemCityBinding

class CityAdapter(
    private val onClick: (City) -> Unit
) : ListAdapter<City, CityAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(city: City) {
            binding.tvCityName.text = city.name
            binding.tvCityProvince.text = city.province
            binding.root.setOnClickListener { onClick(city) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<City>() {
        override fun areItemsTheSame(oldItem: City, newItem: City) =
            oldItem.name == newItem.name && oldItem.lat == newItem.lat

        override fun areContentsTheSame(oldItem: City, newItem: City) = oldItem == newItem
    }
}
