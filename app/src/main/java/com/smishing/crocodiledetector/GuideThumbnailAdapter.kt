package com.smishing.crocodiledetector

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smishing.crocodiledetector.databinding.ItemGuideThumbnailBinding

class GuideThumbnailAdapter(
    private val categories: List<CartoonCategory>,
    private val onClick: (CartoonCategory) -> Unit
) : RecyclerView.Adapter<GuideThumbnailAdapter.ThumbnailViewHolder>() {

    inner class ThumbnailViewHolder(
        private val binding: ItemGuideThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: CartoonCategory) {
            try {
                val bitmap = binding.root.context.assets
                    .open(category.thumbnailPath)
                    .use { BitmapFactory.decodeStream(it) }
                binding.ivThumbnail.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            binding.tvCategoryName.text = category.name
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ItemGuideThumbnailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size
}