package com.smishing.crocodiledetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator

class HomeFragment : Fragment() {

    private lateinit var tvIndicator: TextView
    private lateinit var tutorialPager: ViewPager2
    private lateinit var tutorialDots: DotsIndicator
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val slides = TutorialData.getSlides()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvIndicator   = view.findViewById(R.id.tvIndicator)
        tutorialPager = view.findViewById(R.id.tutorialPager)
        tutorialDots  = view.findViewById(R.id.tutorialDots)
        btnPrev       = view.findViewById(R.id.btnPrev)
        btnNext       = view.findViewById(R.id.btnNext)

        setupTutorial()
        setupFeatureGrid(view)
    }

    private fun setupTutorial() {
        tutorialPager.adapter = TutorialPagerAdapter(slides)
        tutorialDots.attachTo(tutorialPager)
        updateButtons(0)

        tutorialPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateButtons(position)
        })

        btnPrev.setOnClickListener {
            val cur = tutorialPager.currentItem
            if (cur > 0) tutorialPager.currentItem = cur - 1
        }
        btnNext.setOnClickListener {
            val cur = tutorialPager.currentItem
            if (cur < slides.size - 1) tutorialPager.currentItem = cur + 1
        }
    }

    private fun updateButtons(position: Int) {
        btnPrev.isEnabled = position > 0
        btnPrev.alpha = if (position > 0) 1f else 0.3f
        btnNext.text = if (position == slides.size - 1) "완료" else "다음"
    }

    private fun setupFeatureGrid(view: View) {
        view.findViewById<View>(R.id.featureOndeviceAi).setOnClickListener {
            FeatureDetailFragment.newInstance(R.layout.fragment_ondevice_ai)
                .show(parentFragmentManager, "feature")
        }
        view.findViewById<View>(R.id.featureSimBox).setOnClickListener {
            FeatureDetailFragment.newInstance(R.layout.fragment_sim_box)
                .show(parentFragmentManager, "feature")
        }
        view.findViewById<View>(R.id.featureAiCrocodile).setOnClickListener {
            FeatureDetailFragment.newInstance(R.layout.fragment_ai_crocodile)
                .show(parentFragmentManager, "feature")
        }
        view.findViewById<View>(R.id.featureUrlCheck).setOnClickListener {
            FeatureDetailFragment.newInstance(R.layout.fragment_url_check_detail)
                .show(parentFragmentManager, "feature")
        }
    }

    override fun onResume() {
        super.onResume()
        when {
            !Settings.canDrawOverlays(requireContext()) -> {
                tvIndicator.text = "● PERMISSION REQUIRED"
                tvIndicator.setTextColor(0xFFFFAA00.toInt())
            }
            CallAudioService.instance != null -> {
                tvIndicator.text = "● READY"
                tvIndicator.setTextColor(0xFF00FF00.toInt())
            }
            else -> {
                tvIndicator.text = "● SETUP REQUIRED"
                tvIndicator.setTextColor(0xFFFFAA00.toInt())
            }
        }
    }
}