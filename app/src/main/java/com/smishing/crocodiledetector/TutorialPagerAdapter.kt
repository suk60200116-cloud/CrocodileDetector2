package com.smishing.crocodiledetector

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smishing.crocodiledetector.databinding.ItemTutorialSlideBinding

class TutorialPagerAdapter(
    private val slides: List<TutorialSlide>
) : RecyclerView.Adapter<TutorialPagerAdapter.SlideViewHolder>() {

    inner class SlideViewHolder(
        private val binding: ItemTutorialSlideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slide: TutorialSlide) {
            binding.tvIcon.text = slide.icon
            binding.tvTitle.text = slide.title
            binding.tvDescription.text = slide.description

            // 태그 뱃지
            binding.tvTag.text = slide.tag
            binding.tvTag.setTextColor(Color.parseColor(slide.tagColor))
            binding.tvTag.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(slide.tagBgColor))
                setStroke(2, Color.parseColor(slide.tagColor + "44"))
                cornerRadius = 100f
            }

            // 아이콘 원형 배경
            binding.flIconBg.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(slide.iconBgColor))
                setStroke(3, Color.parseColor(slide.iconBorderColor))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemTutorialSlideBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount() = slides.size
}