package com.realpaths

import androidx.recyclerview.widget.DiffUtil


object UriDiffCallback : DiffUtil.ItemCallback<UriModel>() {
    override fun areItemsTheSame(oldItem: UriModel, newItem: UriModel): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: UriModel, newItem: UriModel): Boolean {
        return oldItem == newItem
    }
}