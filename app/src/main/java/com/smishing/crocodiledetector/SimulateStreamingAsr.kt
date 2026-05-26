package com.smishing.crocodiledetector

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

private const val TAG = "CrocodileAsr"

object SimulateStreamingAsr {
    private var _recognizer: OfflineRecognizer? = null
    val recognizer: OfflineRecognizer
        get() = _recognizer!!

    private var _vad: Vad? = null
    val vad: Vad
        get() = _vad!!

    // 1. STT 엔진 초기화 (문샤인 모델 전용)
    fun initOfflineRecognizer(context: Context, asrModelType: Int) {
        synchronized(this) {
            if (_recognizer != null) return

            Log.i(TAG, "Initializing Moonshine offline recognizer")

            // ⭐️ 질문자님이 확인하신 날짜 포함 폴더명
            val modelDir = "sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27"

            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    // ⭐️ [필독] 질문자님 라이브러리 규격인 mergedDecoder를 사용합니다
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = "$modelDir/encoder_model.ort", //
                        mergedDecoder = "$modelDir/decoder_model_merged.ort" //
                    ),
                    // ⭐️ tokens.txt로 이름을 바꾸셨다면 이대로 유지합니다
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 2,
                    modelType = "moonshine"
                ),
                decodingMethod = "greedy_search"
            )

            try {
                _recognizer = OfflineRecognizer(context.assets, config)
                Log.i(TAG, "✅ Moonshine 엔진 로드 성공!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 엔진 초기화 실패: ${e.message}")
            }
        }
    }

    // 2. VAD 엔진 초기화 (질문자님 요청 방식 반영)
    fun initVad(assetManager: AssetManager) {
        if (_vad != null) return

        Log.i(TAG, "Initializing VAD")

        try {
            // ⭐️ 라이브러리 내장 함수를 사용하여 기본 설정을 불러옵니다
            val config = getVadModelConfig(type = 0)

            // ⭐️ [중요] silero_vad 대신 sileroVadModelConfig를 사용합니다.
            config?.sileroVadModelConfig?.model = "silero_vad.onnx" //

            if (config != null) {
                _vad = Vad(assetManager, config)
                Log.i(TAG, "✅ VAD 엔진 로드 성공!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ VAD 초기화 실패: ${e.message}")
        }
    }
}
