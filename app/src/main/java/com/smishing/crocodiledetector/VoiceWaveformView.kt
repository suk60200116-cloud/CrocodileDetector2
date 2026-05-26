package com.smishing.crocodiledetector

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class VoiceWaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val targetAmplitude = 0.75f // 진폭
    private val speed = 0.06f           // 애니메이션 속도
    private val complexity = 6          // 파동의 개수

    private var phase = 0f
    private val paths = Array(complexity) { Path() }

    // 온보딩 버튼과 동일한 연보라색
    private val baseColor = Color.parseColor("#A78BFA")

    // 1. 부드러운 물결 선을 그릴 페인트
    private val wavePaints = Array(complexity) { i ->
        Paint().apply {
            color = baseColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            strokeWidth = 8f - i
            alpha = 255 - (i * 35)
        }
    }

    // 2. 배경 스펙트럼 바 페인트
    private val barPaint = Paint().apply {
        color = baseColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        strokeWidth = 4f
        alpha = 50
    }

    private var animator: ValueAnimator? = null

    init {
        setupAnimator()
    }

    private fun setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase += speed
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        val maxAmplitude = (h / 2f) * targetAmplitude

        // --- A. 배경 텍스처 ---
        for (x in 0..w.toInt() step 12) {
            val progress = x / w
            // 수정된 부분: Kotlin의 Float.pow(Int) 익스텐션 함수 사용
            val envelope = exp(-((progress - 0.5f) * 4f).pow(2))

            var barHeight = 0f
            for (i in 1..3) {
                barHeight += abs(sin((progress * i * 15f) + phase))
            }
            barHeight = (barHeight / 3f) * maxAmplitude * envelope + 5f

            canvas.drawLine(x.toFloat(), midY - barHeight, x.toFloat(), midY + barHeight, barPaint)
        }

        // --- B. 메인 파형 ---
        paths.forEach { it.reset() }

        for (i in 0 until complexity) {
            val path = paths[i]
            path.moveTo(0f, midY)

            val freq = 1.5f + (i * 0.8f)
            val amplitudeMod = 1f - (i * 0.15f)
            val phaseSpeedMod = 1f + (i * 0.3f)

            for (x in 0..w.toInt() step 5) {
                val progress = x / w
                // 수정된 부분: Kotlin의 Float.pow(Int) 익스텐션 함수 사용
                val envelope = exp(-((progress - 0.5f) * 4f).pow(2))

                val waveAmplitude = maxAmplitude * amplitudeMod * envelope
                val currentPhase = phase * phaseSpeedMod

                val y = midY + sin((progress * freq * PI.toFloat() * 2f) + currentPhase) * waveAmplitude

                path.lineTo(x.toFloat(), y)
            }
            canvas.drawPath(path, wavePaints[i])
        }
    }
}