package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment

class FeatureDetailFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layoutId = arguments?.getInt(KEY_LAYOUT) ?: R.layout.fragment_ondevice_ai
        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener { dismiss() }
    }

    companion object {
        private const val KEY_LAYOUT = "layout_id"

        fun newInstance(@LayoutRes layoutId: Int) = FeatureDetailFragment().apply {
            arguments = Bundle().apply { putInt(KEY_LAYOUT, layoutId) }
        }
    }
}