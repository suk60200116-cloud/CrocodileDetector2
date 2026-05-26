package com.smishing.crocodiledetector

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.k2fsa.sherpa.onnx.*

class SettingsFragment : Fragment() {

    private val PREFS_NAME = "crocodile_settings"
    private val KEY_DETECTION_ENABLED = "detection_enabled"
    private val KEY_NOTIFICATION_ENABLED = "notification_enabled"
    private val KEY_SENSITIVITY = "sensitivity"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ── 탐지 ON/OFF ──────────────────────────────────────────────
        val switchDetection = view.findViewById<SwitchCompat>(R.id.switchDetection)
        switchDetection.isChecked = prefs.getBoolean(KEY_DETECTION_ENABLED, true)
        switchDetection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DETECTION_ENABLED, isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "탐지가 활성화됐어요" else "탐지가 비활성화됐어요",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ── 알림 ON/OFF ──────────────────────────────────────────────
        val switchNotification = view.findViewById<SwitchCompat>(R.id.switchNotification)
        switchNotification.isChecked = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, isChecked).apply()
        }

        // ── 민감도 슬라이더 ──────────────────────────────────────────
        val seekBarSensitivity = view.findViewById<SeekBar>(R.id.seekBarSensitivity)
        val tvSensitivityLabel = view.findViewById<TextView>(R.id.tvSensitivityLabel)

        val savedSensitivity = prefs.getInt(KEY_SENSITIVITY, 1)
        seekBarSensitivity.progress = savedSensitivity
        tvSensitivityLabel.text = getSensitivityLabel(savedSensitivity)

        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSensitivityLabel.text = getSensitivityLabel(progress)
                prefs.edit().putInt(KEY_SENSITIVITY, progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ── STT 엔진 테스트 ──────────────────────────────────────────
        view.findViewById<View>(R.id.btnSttTest).setOnClickListener {
            testSttEngine(view)
        }

        // ── STT 성능테스트 (감지 시작) ────────────────────────────────
        view.findViewById<View>(R.id.btnSttPerformanceTest).setOnClickListener {
            SttPerformanceTestFragment()
                .show(parentFragmentManager, "stt_performance_test")
        }

        // ── 접근성 설정 ──────────────────────────────────────────────
        view.findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // ── 앱 버전 ──────────────────────────────────────────────────
        val tvAppVersion = view.findViewById<TextView>(R.id.tvAppVersion)
        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        tvAppVersion.text = "v$versionName"

        // ── 개인정보처리방침 ──────────────────────────────────────────
        view.findViewById<View>(R.id.btnPrivacyPolicy).setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("개인정보처리방침")
                .setMessage(
                    "CrocodileDetector는 다음과 같이 개인정보를 처리합니다.\n\n" +
                            "1. 수집 항목: 통화 음성 데이터 (STT 변환 후 즉시 삭제)\n\n" +
                            "2. 수집 목적: 보이스피싱 탐지\n\n" +
                            "3. 보관 기간: 통화 종료 즉시 삭제\n\n" +
                            "4. 외부 전송: 없음 (온디바이스 처리)\n\n" +
                            "문의: seunghak@hufs.ac.kr"
                )
                .setPositiveButton("확인", null)
                .show()
        }

        // ── 오픈소스 라이선스 ─────────────────────────────────────────
        view.findViewById<View>(R.id.btnOpenSource).setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("오픈소스 라이선스")
                .setMessage(
                    "sherpa-onnx\nApache License 2.0\n\n" +
                            "OkHttp\nApache License 2.0\n\n" +
                            "Kotlin Coroutines\nApache License 2.0\n\n" +
                            "FastAPI\nMIT License\n\n" +
                            "Moonshine STT\nApache License 2.0"
                )
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun getSensitivityLabel(progress: Int): String {
        return when (progress) {
            0 -> "😴 느긋한 악어  —  확실한 것만 탐지"
            1 -> "👀 깨어있는 악어  —  기본값"
            2 -> "🔥 사나운 악어  —  조금이라도 의심되면 경고"
            else -> "👀 깨어있는 악어  —  기본값"
        }
    }

    private fun testSttEngine(view: View) {
        val tvSttStatus = view.findViewById<TextView>(R.id.tvSttStatus)
        tvSttStatus.text = "문샤인 엔진 초기화 중..."

        Thread {
            try {
                val modelDir = "sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27"
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        moonshine = OfflineMoonshineModelConfig(
                            encoder = "$modelDir/encoder_model.ort",
                            mergedDecoder = "$modelDir/decoder_model_merged.ort"
                        ),
                        tokens = "$modelDir/tokens.txt",
                        modelType = "moonshine",
                        numThreads = 2
                    ),
                    decodingMethod = "greedy_search"
                )
                val testRecognizer = OfflineRecognizer(requireContext().assets, config)
                testRecognizer.release()

                activity?.runOnUiThread {
                    tvSttStatus.text = "✅ 엔진 로드 성공"
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    tvSttStatus.text = "❌ 로드 실패: ${e.message}"
                }
            }
        }.start()
    }
}