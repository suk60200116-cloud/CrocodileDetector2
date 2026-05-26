package com.smishing.crocodiledetector

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class IncomingCallBanner(private val context: Context) {

    enum class BannerType {
        UNKNOWN_NUMBER,
        SPAM_NUMBER
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler   = Handler(Looper.getMainLooper())
    private var bannerView: LinearLayout? = null
    private var countdownTextView: TextView? = null
    var isShowing = false

    fun show(type: BannerType, spamInfo: DatabaseHelper.SpamInfo? = null, autoHide: Boolean = true) {
        if (isShowing) return
        mainHandler.post {

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }

            when (type) {
                BannerType.UNKNOWN_NUMBER -> {
                    layout.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.argb(230, 60, 50, 20))
                        setStroke(dp(2), Color.parseColor("#FFD966"))
                    }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    TextView(context).apply {
                        text = "❓"
                        textSize = 26f
                        setPadding(0, 0, dp(12), 0)
                        row.addView(this)
                    }

                    val textColumn = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    TextView(context).apply {
                        text = "모르는 번호입니다"
                        textSize = 17f
                        setTextColor(Color.parseColor("#FFD966"))
                        setTypeface(typeface, Typeface.BOLD)
                        textColumn.addView(this)
                    }
                    TextView(context).apply {
                        text = "주의해서 통화하세요!"
                        textSize = 13f
                        setTextColor(Color.parseColor("#FFCC44"))
                        setTypeface(typeface, Typeface.NORMAL)
                        setPadding(0, dp(2), 0, 0)
                        textColumn.addView(this)
                    }

                    row.addView(textColumn)
                    layout.addView(row)
                }

                BannerType.SPAM_NUMBER -> {
                    layout.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.argb(240, 180, 20, 20))
                        setStroke(dp(2), Color.parseColor("#FF4444"))
                    }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    TextView(context).apply {
                        text = "⚠️"
                        textSize = 26f
                        setPadding(0, 0, dp(12), 0)
                        row.addView(this)
                    }

                    // 텍스트 컬럼
                    val textColumn = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    TextView(context).apply {
                        text = "보이스피싱 신고된 번호"
                        textSize = 17f
                        setTextColor(Color.WHITE)
                        setTypeface(typeface, Typeface.BOLD)  // ← Bold
                        textColumn.addView(this)
                    }
                    TextView(context).apply {
                        text = "${spamInfo?.spamType ?: "스팸"} 유형의 피싱으로 신고접수 되었습니다"
                        textSize = 13f
                        setTextColor(Color.parseColor("#FFAAAA"))  // ← 살짝 연한 빨간색
                        setTypeface(typeface, Typeface.NORMAL)     // ← 일반체
                        setPadding(0, dp(2), 0, 0)
                        textColumn.addView(this)
                    }

                    row.addView(textColumn)
                    layout.addView(row)
                }
            }

            // 카운트다운 텍스트
            countdownTextView = TextView(context).apply {
                textSize = 13f
                setTextColor(Color.argb(180, 200, 200, 200))
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, 0)
                text = if (autoHide) "5초 후 사라집니다"
                else "통화 수락 후 5초 뒤 사라집니다"
            }.also { layout.addView(it) }

            // ⭐️ [여기가 핵심 변경점] 잠금화면 뚫기 옵션 3종 세트 추가
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or   // ✅ 잠금화면 위로 표시
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or     // ✅ 화면 강제로 켜기
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,       // ✅ 화면 꺼짐 방지
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(200)
            }

            bannerView = layout
            windowManager.addView(layout, params)
            isShowing = true

            if (autoHide) {
                runCountdown(5)
            }
        }
    }

    fun startCountdownAndHide(seconds: Int = 5) {
        if (!isShowing) return
        runCountdown(seconds)
    }

    private fun runCountdown(seconds: Int) {
        var remaining = seconds
        countdownTextView?.text = "${remaining}초 후 사라집니다"

        val tickRunnable = object : Runnable {
            override fun run() {
                if (!isShowing) return
                remaining--
                if (remaining > 0) {
                    countdownTextView?.text = "${remaining}초 후 사라집니다"
                    mainHandler.postDelayed(this, 1000)
                } else {
                    hide()
                }
            }
        }
        mainHandler.postDelayed(tickRunnable, 1000)
    }

    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            try {
                bannerView?.let { windowManager.removeView(it) }
            } catch (e: Exception) { }
            bannerView = null
            countdownTextView = null
            isShowing = false
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}