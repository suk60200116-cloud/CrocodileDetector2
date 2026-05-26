package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.smishing.crocodiledetector.databinding.FragmentGuideBinding

class GuideFragment : Fragment() {

    private var _binding: FragmentGuideBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
    }

    private fun setupGrid() {
        val categories = loadCategoriesFromAssets()

        val adapter = GuideThumbnailAdapter(categories) { category ->
            CartoonDetailDialogFragment
                .newInstance(category)
                .show(parentFragmentManager, "cartoon_detail")
        }

        binding.rvGuide.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            this.adapter = adapter
        }
    }

    private fun loadCategoriesFromAssets(): List<CartoonCategory> {
        val folder = "guide_images/cartoon"
        return try {
            val files = requireContext().assets.list(folder) ?: emptyArray()

            files
                .filter { it.endsWith(".webp") }
                .mapNotNull { filename ->
                    val nameOnly = filename.removeSuffix(".webp")
                    val lastUnderscore = nameOnly.lastIndexOf("_")
                    if (lastUnderscore == -1) return@mapNotNull null
                    val category = nameOnly.substring(0, lastUnderscore)
                    val number = nameOnly.substring(lastUnderscore + 1).toIntOrNull()
                        ?: return@mapNotNull null
                    Triple(category, number, "$folder/$filename")
                }
                .groupBy { it.first }
                .map { (name, triples) ->
                    CartoonCategory(
                        name = name,
                        assetPaths = triples.sortedBy { it.second }.map { it.third }
                    )
                }
                .sortedBy { it.name }

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}