package com.smishing.crocodiledetector

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

data class LstmResult(
    val type: String,
    val confidence: Float
)

class LSTMDetector(private val context: Context) {

    private var module: Module? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var idx2Type: Map<String, String> = emptyMap()

    private var embeddingWeights: FloatArray? = null
    private var vocabSize: Int = 0
    private val embeddingDim: Int = 300
    private val maxLen: Int = 64
    private val unknownRatioThreshold: Float = 0.7f

    init {
        try {
            val modelFile = assetFilePath(context, "model_scripted.ptl")
            module = Module.load(modelFile)

            loadVocab("fasttext_lite_v2_vocab.json")
            loadFastTextWeights("fasttext_lite_v2.bin")
            loadLabelMap("idx2type.json")

            Log.d("LSTMDetector", "모델 및 사전 로드 완료")
        } catch (e: Exception) {
            Log.e("LSTMDetector", "초기화 오류", e)
        }
    }

    private fun loadVocab(fileName: String) {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, Int>>() {}.type
        vocab = Gson().fromJson(jsonString, type)
    }

    private fun loadLabelMap(fileName: String) {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, String>>() {}.type
        idx2Type = Gson().fromJson(jsonString, type)
    }

    private fun loadFastTextWeights(fileName: String) {
        try {
            val inputStream = context.assets.open(fileName)
            val fileSize = inputStream.available()
            val floatCount = fileSize / 4

            embeddingWeights = FloatArray(floatCount)
            val buffer = ByteArray(4096)
            val byteBuffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            var floatIdx = 0

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                byteBuffer.clear()
                byteBuffer.put(buffer, 0, bytesRead)
                byteBuffer.flip()

                val floatsRead = bytesRead / 4
                repeat(floatsRead) {
                    if (floatIdx < embeddingWeights!!.size) {
                        embeddingWeights!![floatIdx++] = byteBuffer.float
                    }
                }
            }

            inputStream.close()
            vocabSize = vocab.size
            Log.d("LSTMDetector", "임베딩 로드 완료: ${embeddingWeights?.size}개 float")
        } catch (e: Exception) {
            Log.e("LSTMDetector", "임베딩 로드 실패", e)
            vocabSize = vocab.size
            embeddingWeights = FloatArray(vocabSize * embeddingDim)
        }
    }

    private fun getWordVector(word: String): FloatArray {
        val vector = FloatArray(embeddingDim)
        val idx = vocab[word] ?: return vector  // 미등록 단어는 영벡터 반환

        if (embeddingWeights != null && idx * embeddingDim + embeddingDim <= embeddingWeights!!.size) {
            System.arraycopy(embeddingWeights!!, idx * embeddingDim, vector, 0, embeddingDim)
        }
        return vector
    }

    private fun cleanText(text: String): String {
        return text
            .replace(".", " ")
            .replace(",", " ")
            .replace("?", " ")
            .replace("!", " ")
            .replace("~", " ")
            .replace("·", " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    fun predict(text: String): LstmResult {
        if (module == null) return LstmResult("모델없음", 0f)

        try {
            // 구두점 제거 후 토큰화 + MAX_LEN 64 제한
            val cleanedText = cleanText(text)
            val tokens = cleanedText.split(" ").filter { it.isNotBlank() }.take(maxLen)
            if (tokens.isEmpty()) return LstmResult("분석불가", 0f)

            // 미등록 단어 비율 체크
            val unknownCount = tokens.count { vocab[it] == null }
            val unknownRatio = unknownCount.toFloat() / tokens.size
            Log.d("LSTMDetector", "토큰 수: ${tokens.size}, 미등록: $unknownCount, 비율: $unknownRatio")

            if (unknownRatio >= unknownRatioThreshold) {
                Log.d("LSTMDetector", "미등록 단어 비율 높음 → 정상 처리")
                return LstmResult("정상", 0f)
            }

            // 임베딩 텐서 생성 [1, maxLen, embeddingDim]
            val flatFloatArray = FloatArray(maxLen * embeddingDim)
            for (i in tokens.indices) {
                val vec = getWordVector(tokens[i])
                System.arraycopy(vec, 0, flatFloatArray, i * embeddingDim, embeddingDim)
            }

            val inputTensor = Tensor.fromBlob(
                flatFloatArray,
                longArrayOf(1, maxLen.toLong(), embeddingDim.toLong())
            )

            // 모델 추론
            val outputTensor = module!!.forward(IValue.from(inputTensor)).toTensor()
            val scores = outputTensor.dataAsFloatArray

            Log.d("LSTMDetector", "numClasses: ${scores.size}")

            // Softmax 계산
            val maxScore = scores.maxOrNull() ?: 0f
            var sumExp = 0f
            val exps = FloatArray(scores.size)
            for (i in scores.indices) {
                exps[i] = exp(scores[i] - maxScore)
                sumExp += exps[i]
            }

            val probs = FloatArray(scores.size) { exps[it] / sumExp }

            // 정상(0번) 확률
            val normalProb = probs[0]
            val phishingProb = 1f - normalProb

            // 피싱 유형 중 최대 확률 인덱스
            var bestIdx = 1
            var maxProb = probs[1]
            for (i in 2 until probs.size) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    bestIdx = i
                }
            }

            val predictedType = idx2Type[bestIdx.toString()] ?: "알수없음"

            Log.d("LSTMDetector", "추론 완료: $predictedType (phishingProb: $phishingProb, typeProb: $maxProb)")

            // 정상으로 판정된 경우
            if (phishingProb < 0.5f) {
                return LstmResult("정상", 0f)
            }

            return LstmResult(
                type = predictedType,
                confidence = maxProb
            )

        } catch (e: Exception) {
            Log.e("LSTMDetector", "추론 중 오류", e)
            return LstmResult("에러", 0f)
        }
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
        }
        return file.absolutePath
    }
}