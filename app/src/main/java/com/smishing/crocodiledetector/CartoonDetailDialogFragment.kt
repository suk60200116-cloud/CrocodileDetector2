package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.smishing.crocodiledetector.databinding.FragmentCartoonDetailBinding

class CartoonDetailDialogFragment : DialogFragment() {

    private var _binding: FragmentCartoonDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_NAME = "category_name"
        private const val ARG_PATHS = "asset_paths"

        fun newInstance(category: CartoonCategory): CartoonDetailDialogFragment {
            return CartoonDetailDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, category.name)
                    putStringArrayList(ARG_PATHS, ArrayList(category.assetPaths))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartoonDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_NAME) ?: ""
        val paths = arguments?.getStringArrayList(ARG_PATHS) ?: arrayListOf()

        binding.tvDetailTitle.text = name
        binding.tvPageIndicator.text = "1 / ${paths.size}"

        binding.btnClose.setOnClickListener { dismiss() }

        val pagerAdapter = CartoonPagerAdapter(paths)
        binding.viewPager.adapter = pagerAdapter
        binding.dotsIndicator.attachTo(binding.viewPager)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tvPageIndicator.text = "${position + 1} / ${paths.size}"
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}