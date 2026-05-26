package com.smishing.crocodiledetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class SttPerformanceTestFragment : DialogFragment() {

    private lateinit var tvIndicator: TextView
    private lateinit var btnStart: Button
    private var isTesting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_stt_performance_test, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvIndicator = view.findViewById(R.id.tvIndicator)
        btnStart    = view.findViewById(R.id.btnStart)

        view.findViewById<View>(R.id.btnBack).setOnClickListener { dismiss() }

        btnStart.setOnClickListener {
            if (CallAudioService.instance == null) {
                Toast.makeText(requireContext(), "접근성 서비스를 먼저 활성화해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isTesting) {
                if (SimulateStreamingAsr.recognizer == null) {
                    Toast.makeText(requireContext(), "⏳ 엔진 초기화 중입니다...", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isTesting = true
                tvIndicator.text = "● TESTING"
                tvIndicator.setTextColor(0xFFFFAA00.toInt())
                btnStart.text = "테스트 중지"

                if (DetectionService.overlayView == null) {
                    DetectionService.overlayView = OverlayView(requireContext())
                }
                if (DetectionService.overlayView?.isShowing == false) {
                    DetectionService.overlayView?.show()
                }
                CallAudioService.instance?.startTestCapture()
                CallAudioService.instance?.showFloatingButton()
            } else {
                stopTesting()
            }
        }
    }

    private fun stopTesting() {
        isTesting = false
        CallAudioService.instance?.stopCapture()
        CallAudioService.instance?.hideFloatingButton()
        if (DetectionService.overlayView?.isShowing == true) {
            DetectionService.overlayView?.hide()
        }
        DetectionService.overlayView = null
        tvIndicator.text = "● STANDBY"
        tvIndicator.setTextColor(0xFF555555.toInt())
        btnStart.text = "감지 시작"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isTesting) stopTesting()
    }
}