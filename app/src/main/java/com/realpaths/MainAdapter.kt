package com.realpaths

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.realpaths.databinding.MainItemLayoutBinding

class MainAdapter : ListAdapter<UriModel, MainAdapter.MainHolder>(UriDiffCallback) {

    inner class MainHolder(private val binding: MainItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(response: UriModel) {
            binding.run {
                originalUriTV.text = response.uri.toString()
                realPathTV.text = response.path
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainHolder {
        return MainHolder(
            MainItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MainHolder, position: Int) {
        holder.bind(getItem(position))
    }

}