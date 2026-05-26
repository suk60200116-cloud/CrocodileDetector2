package com.smishing.crocodiledetector

import android.util.Log
import kotlin.math.sqrt

class SimBoxDetector {

    private val TAG = "SimBoxDetector"

    // ── 파라미터 ──────────────────────────────────────────────────
    private val FRAME_SAMPLES        = 160     // 10ms @ 16kHz (160 samples)
    private val MAX_DROP_FRAMES      = 4       // 40ms 이내 복구 기준 (Boxed Out 논문)
    private val REQUIRED_CHUNKS      = 30      // 1회 검사당 누적 청크 수 (약 3초)
    private val EVENT_RATE_THRESHOLD = 0.0100f // 실험을 통해 보정한 안전한 임계값

    // ── 신규: 누적 탐지 파라미터 ──────────────────────────────────
    private val REQUIRED_DETECTIONS  = 2       // 2회 이상 탐지 시 최종 SIM Box 확정
    private var simBoxDetectCount    = 0       // 현재까지 탐지된 횟수

    // ── 상태 변수 ─────────────────────────────────────────────────
    sealed class Result {
        object Undecided : Result()
        object Normal    : Result()
        object SIMBox    : Result()
        object Skip      : Result()
    }

    @Volatile var isSpeechStarted = false
    @Volatile private var isFinished = false

    private var chunkCount = 0
    private val pcmBuffer  = mutableListOf<Short>()  // 30청크 PCM 누적

    // ── 외부 호출 진입점 ──────────────────────────────────────────
    fun analyze(pcmChunk: ShortArray): Result {
        // 이미 최종 판정이 났거나, STT가 상대방 발화를 감지하지 않은 구간이면 스킵
        if (isFinished)       return Result.Skip
        if (!isSpeechStarted) return Result.Skip

        // PCM 누적
        pcmChunk.forEach { pcmBuffer.add(it) }
        chunkCount++

        // 30청크(3초) 쌓이면 판정 시작
        return if (chunkCount >= REQUIRED_CHUNKS) {
            val eventRate = computeEventRate(pcmBuffer.toShortArray())

            if (eventRate > EVENT_RATE_THRESHOLD) {
                simBoxDetectCount++
                Log.w(TAG, "🚨 SIM Box 패턴 1회 감지! (누적 $simBoxDetectCount/$REQUIRED_DETECTIONS) event_rate=${"%.4f".format(eventRate)}")

                if (simBoxDetectCount >= REQUIRED_DETECTIONS) {
                    // 목표 누적 횟수 도달: 최종 확정 판정 후 분석 종료
                    isFinished = true
                    Log.e(TAG, "🚨 최종 SIM Box 확정 판정! 경고창 트리거")
                    Result.SIMBox
                } else {
                    // 아직 누적 횟수 부족: 다음 3초 구간을 검사하기 위해 버퍼만 비움
                    resetBufferOnly()
                    Result.Undecided
                }
            } else {
                Log.i(TAG, "✅ 3초 정상 구간 통과 event_rate=${"%.4f".format(eventRate)}")
                // 정상이어도 영원히 끄지 않음. 다음 3초 구간을 다시 검사하기 위해 대기
                resetBufferOnly()
                Result.Normal
            }
        } else {
            Result.Undecided
        }
    }

    // ── 핵심 탐지 로직 ────────────────────────────────────────────
    /**
     * Silence Insertion 탐지 (Boxed Out, USENIX Security 2015)
     */
    private fun computeEventRate(samples: ShortArray): Float {
        val totalFrames = samples.size / FRAME_SAMPLES
        if (totalFrames < 2) return 0f

        // 1. 프레임별 RMS 계산
        val rms = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val start = i * FRAME_SAMPLES
            var sumSq = 0.0
            for (j in start until start + FRAME_SAMPLES) {
                val s = samples[j] / 32768f
                sumSq += s * s
            }
            rms[i] = sqrt(sumSq / FRAME_SAMPLES).toFloat()
        }

        // 2. 동적 임계값 계산
        val sorted       = rms.sorted()
        val bottom10     = sorted.take(maxOf(1, totalFrames / 10))
        val lowerEnv     = bottom10.average().toFloat()
        val silenceThr   = lowerEnv * 1.5f          // silence 판별 상한
        val voiceThr     = rms.average().toFloat() * 0.2f  // 발화 판별 하한

        // 3. Silence Insertion 이벤트 탐지
        var voicedFrames = 0
        var events       = 0
        var i = 1

        while (i < totalFrames) {
            if (rms[i] > voiceThr) voicedFrames++

            // 발화 → silence 전환 감지
            if (rms[i - 1] > voiceThr && rms[i] <= silenceThr) {
                // 40ms(4프레임) 이내 에너지 복구 확인
                var recoveryFound = false
                val scanEnd = minOf(i + 1 + MAX_DROP_FRAMES, totalFrames)
                for (j in i + 1 until scanEnd) {
                    if (rms[j] > voiceThr) {
                        recoveryFound = true
                        break
                    }
                }
                if (recoveryFound) {
                    events++
                    i += MAX_DROP_FRAMES + 1  // 중복 방지
                    continue
                }
            }
            i++
        }

        val rate = events.toFloat() / (voicedFrames + 1e-6f)
        return rate
    }

    // ── 버퍼 초기화 (연속 검사용) ──────────────────────────────────
    private fun resetBufferOnly() {
        chunkCount = 0
        pcmBuffer.clear()
        // 주의: isSpeechStarted는 STT 엔진이 제어해야 하므로 여기서 건드리지 않음
    }

    // ── 전체 초기화 (통화 종료 시 호출) ───────────────────────────
    fun reset() {
        isFinished        = false
        isSpeechStarted   = false
        simBoxDetectCount = 0
        chunkCount        = 0
        pcmBuffer.clear()
    }
}