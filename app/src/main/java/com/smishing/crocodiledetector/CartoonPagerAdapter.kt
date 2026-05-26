package com.smishing.crocodiledetector

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smishing.crocodiledetector.databinding.ItemCartoonPageBinding

class CartoonPagerAdapter(
    private val assetPaths: List<String>
) : RecyclerView.Adapter<CartoonPagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(
        private val binding: ItemCartoonPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(assetPath: String) {
            try {
                val bitmap = binding.root.context.assets
                    .open(assetPath)
                    .use { BitmapFactory.decodeStream(it) }
                binding.ivCartoon.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemCartoonPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(assetPaths[position])
    }

    override fun getItemCount() = assetPaths.size
}