package com.smishing.crocodiledetector

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OverlayView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: FrameLayout? = null
    var isShowing = false

    private var cardView: LinearLayout? = null
    private var stagePill: TextView? = null
    private var warningBanner: LinearLayout? = null
    private var sttTextView: TextView? = null
    private var scriptTextView: TextView? = null
    private var aiStatusRow: LinearLayout? = null
    private var aiStatusText: TextView? = null
    private var actionIconsRow: LinearLayout? = null

    enum class Stage { WARNING, DANGER }

    // ── 표시 / 숨기기 ─────────────────────────────────────────

    fun show() {
        if (isShowing) return
        mainHandler.post {
            buildView()
            // ⭐️ [변경됨] 잠금화면 뚫기, 화면 켜기, 화면 유지 옵션 추가
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or   // 👈 잠금화면 위로 표시
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or     // 👈 화면 강제 켜기
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,       // 👈 화면 꺼짐 방지
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(40)
            }
            windowManager.addView(rootView, params)
            isShowing = true
        }
    }

    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            try { windowManager.removeView(rootView) } catch (e: Exception) {}
            isShowing = false
            rootView = null
        }
    }

    // ── 공개 업데이트 메서드 ───────────────────────────────────

    /** 1패스(isInterim=true) → 반투명, 2패스(false) → 선명 */
    fun updateSttText(text: String, isInterim: Boolean = false) {
        mainHandler.post {
            sttTextView?.text = text
            sttTextView?.alpha = if (isInterim) 0.55f else 1.0f
        }
    }

    /** 서버에서 받은 대응 스크립트 표시 */
    fun updateScriptText(text: String) {
        mainHandler.post {
            if (text.isBlank()) {
                scriptTextView?.text = "분석 중..."
                scriptTextView?.alpha = 0.4f
            } else {
                scriptTextView?.text = text
                scriptTextView?.alpha = 1.0f
            }
        }
    }

    /**
     * AI 대행(WebSocket) 상태 행 업데이트
     * @param text    표시할 텍스트
     * @param visible false → 행 자체를 숨김 (stopAiAgent 호출 시)
     */
    fun updateAiStatus(text: String, visible: Boolean = true) {
        mainHandler.post {
            if (!visible || text.isBlank()) {
                aiStatusRow?.visibility = View.GONE
            } else {
                aiStatusText?.text = text
                aiStatusRow?.visibility = View.VISIBLE
            }
        }
    }

    fun setStage(stage: Stage) {
        mainHandler.post { applyStage(stage) }
    }

    // ── View 구성 ─────────────────────────────────────────────

    private fun buildView() {
        rootView = FrameLayout(context).apply {
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = buildCardBg(COLOR_WARNING)
            elevation = dpToPx(8).toFloat()
        }

        // ── 헤더: 상태 pill + 닫기 버튼 ──────────────────────
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(11), dpToPx(10), dpToPx(11))
        }
        stagePill = TextView(context).apply {
            text = "⚠  주의"
            textSize = 12f
            setTextColor(Color.parseColor(COLOR_WARNING))
            setTypeface(typeface, Typeface.BOLD)
            background = buildPillBg(COLOR_WARNING)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
        }
        val spacer = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        // 닫기 버튼: 40×40dp 터치 영역 확보
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).also {
                it.gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener { hide() }
        }
        headerRow.addView(stagePill); headerRow.addView(spacer); headerRow.addView(closeBtn)

        // ── 경고 배너 (DANGER 단계만 표시) ───────────────────
        warningBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#33EF4444"))
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            visibility = View.GONE
        }.also { banner ->
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).also {
                    it.marginEnd = dpToPx(8); it.gravity = Gravity.CENTER_VERTICAL
                }
                background = buildRoundBg(Color.parseColor("#EF4444"), dpToPx(4).toFloat())
                banner.addView(this)
            }
            TextView(context).apply {
                text = "보이스피싱 의심 통화 감지됨 — 즉시 대응 필요"
                textSize = 11f; setTextColor(Color.parseColor("#FFAAAA"))
                banner.addView(this)
            }
        }

        // ── 구분선 ────────────────────────────────────────────
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(dpToPx(14), 0, dpToPx(14), 0) }
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
        }

        // ── STT 섹션 ──────────────────────────────────────────
        val sttSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
        }
        val sttHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6)).also {
                it.marginEnd = dpToPx(6); it.gravity = Gravity.CENTER_VERTICAL
            }
            background = buildRoundBg(Color.parseColor("#EF4444"), dpToPx(3).toFloat())
            sttHeaderRow.addView(this)
        }
        TextView(context).apply {
            text = "실시간 통화 내용"; textSize = 10f
            setTextColor(Color.parseColor("#666666")); sttHeaderRow.addView(this)
        }
        val sttBox = FrameLayout(context).apply {
            background = buildRoundBg(Color.parseColor("#18FFFFFF"), dpToPx(10).toFloat())
            setPadding(dpToPx(12), dpToPx(9), dpToPx(12), dpToPx(9))
        }
        sttTextView = TextView(context).apply {
            text = "통화 감지 중..."; textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setLineSpacing(dpToPx(2).toFloat(), 1f); maxLines = 3
        }
        sttBox.addView(sttTextView); sttSection.addView(sttHeaderRow); sttSection.addView(sttBox)

        // ── 대응 스크립트 섹션 ────────────────────────────────
        val scriptSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), 0, dpToPx(14), dpToPx(12))
        }
        val scriptHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }
        TextView(context).apply {
            text = "💬  대응 스크립트"; textSize = 10f
            setTextColor(Color.parseColor("#666666")); scriptHeaderRow.addView(this)
        }
        val scriptBox = FrameLayout(context).apply {
            minimumHeight = dpToPx(64)
            background = buildRoundBg(Color.parseColor("#14FFFFFF"), dpToPx(10).toFloat())
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        scriptTextView = TextView(context).apply {
            text = "분석 중..."; textSize = 12f; alpha = 0.4f
            setTextColor(Color.parseColor(COLOR_WARNING))
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }
        scriptBox.addView(scriptTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        ))
        scriptSection.addView(scriptHeaderRow); scriptSection.addView(scriptBox)

        // ── AI 대행 상태 행 (기본 숨김) ──────────────────────
        aiStatusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A3B82F6"))
            setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7))
            visibility = View.GONE
        }.also { row ->
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6)).also {
                    it.marginEnd = dpToPx(8); it.gravity = Gravity.CENTER_VERTICAL
                }
                background = buildRoundBg(Color.parseColor("#3B82F6"), dpToPx(3).toFloat())
                row.addView(this)
            }
            aiStatusText = TextView(context).apply {
                text = ""; textSize = 11f
                setTextColor(Color.parseColor("#93C5FD"))
                row.addView(this)
            }
        }

        // ── 액션 버튼 행 (DANGER 단계만) ─────────────────────
        actionIconsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dpToPx(14), 0, dpToPx(14), dpToPx(14))
            visibility = View.GONE
        }.also { row ->
            listOf(
                Triple("📵", "전화 끊기", "#EF4444"),
                Triple("📝", "메모하기", "#F59E0B"),
                Triple("🚨", "112 신고", "#3B82F6")
            ).forEach { (icon, label, hex) ->
                row.addView(buildActionBtn(icon, label, Color.parseColor(hex)))
            }
        }

        // ── 카드 조립 ─────────────────────────────────────────
        cardView!!.apply {
            addView(headerRow)
            addView(warningBanner)
            addView(divider)
            addView(sttSection)
            addView(scriptSection)
            addView(aiStatusRow)        // 스크립트 아래, 액션 버튼 위
            addView(actionIconsRow)
        }
        rootView!!.addView(cardView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        applyStage(Stage.WARNING)
    }

    // ── 스테이지 전환 ──────────────────────────────────────────

    private fun applyStage(stage: Stage) {
        val hex   = if (stage == Stage.DANGER) COLOR_DANGER else COLOR_WARNING
        val color = Color.parseColor(hex)
        cardView?.background = buildCardBg(hex)

        when (stage) {
            Stage.WARNING -> {
                stagePill?.text = "⚠  주의"
                stagePill?.setTextColor(color)
                stagePill?.background = buildPillBg(COLOR_WARNING)
                warningBanner?.visibility = View.GONE
                actionIconsRow?.visibility = View.GONE
                scriptTextView?.setTextColor(Color.parseColor(COLOR_WARNING))
                scriptTextView?.setTypeface(scriptTextView?.typeface, Typeface.NORMAL)
            }
            Stage.DANGER -> {
                stagePill?.text = "🔴  위험"
                stagePill?.setTextColor(color)
                stagePill?.background = buildPillBg(COLOR_DANGER)
                warningBanner?.visibility = View.VISIBLE
                actionIconsRow?.visibility = View.VISIBLE
                scriptTextView?.setTextColor(Color.WHITE)
                scriptTextView?.setTypeface(scriptTextView?.typeface, Typeface.BOLD)
            }
        }
    }

    // ── 드로어블 헬퍼 ──────────────────────────────────────────

    private fun buildActionBtn(icon: String, label: String, color: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke(dpToPx(1), Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
            }
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginEnd = dpToPx(6)
            }
            TextView(context).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER; addView(this) }
            TextView(context).apply {
                text = label; textSize = 9f; setTextColor(color)
                gravity = Gravity.CENTER; setPadding(0, dpToPx(3), 0, 0); addView(this)
            }
        }
    }

    private fun buildCardBg(hexBorder: String) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(18).toFloat()
        setColor(Color.parseColor("#E8101027"))
        setStroke(dpToPx(1), Color.parseColor(hexBorder))
    }

    private fun buildPillBg(hexBorder: String) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(20).toFloat()
        setColor(Color.parseColor("#20${hexBorder.removePrefix("#")}"))
        setStroke(dpToPx(1), Color.parseColor(hexBorder))
    }

    private fun buildRoundBg(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = radius; setColor(color)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val COLOR_WARNING = "#F59E0B"
        private const val COLOR_DANGER  = "#EF4444"
    }
}