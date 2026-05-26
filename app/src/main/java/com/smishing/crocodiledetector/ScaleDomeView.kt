package com.smishing.crocodiledetector

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class ScaleDomeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val baseColor = Color.parseColor("#A78BFA")
    private val paint = Paint().apply {
        color = baseColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
    }

    private var pulseFraction = 0f

    init {
        // 더 자연스러운 숨 쉬는 애니메이션(가속/감속 적용)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val size = 35f // 비늘 크기

        // 1. 숨 쉬는 효과: 투명도와 두께가 동시에 조절됨
        val alphaBase = (120 + (pulseFraction * 135)).toInt()
        paint.strokeWidth = 2f + pulseFraction

        // 육각형 그리드
        for (i in -6..6) {
            for (j in -6..6) {
                val x = centerX + i * size * 1.6f + (if (j % 2 != 0) size * 0.8f else 0f)
                val y = centerY + j * size * 1.4f

                // 중심부와 가까울수록 선명하게, 멀어질수록 흐리게(Dome 효과)
                val dist = Math.sqrt(((x - centerX)*(x - centerX) + (y - centerY)*(y - centerY)).toDouble()).toFloat()

                if (dist < 350f) {
                    val alphaMod = 1f - (dist / 400f)
                    paint.alpha = (alphaBase * alphaMod).toInt()

                    drawHexagon(canvas, x, y, size)
                }
            }
        }
    }

    private fun drawHexagon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        for (i in 0..6) {
            val angle = Math.toRadians((i * 60).toDouble())
            val px = x + size * cos(angle).toFloat()
            val py = y + size * sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close() // 육각형을 닫아줌
        canvas.drawPath(path, paint)
    }
}