package com.smishing.crocodiledetector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class CallAudioService : AccessibilityService() {

    private val TAG = "CrocodileDetector"
    private val sampleRate = 16000

    private val simBoxDetector = SimBoxDetector()
    private var simBoxDetected = false

    private lateinit var lstmDetector: LSTMDetector

    private var audioRecord: AudioRecord? = null
    private var audioManager: AudioManager? = null

    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val audioBuffer = mutableListOf<FloatArray>()

    private var tts: TextToSpeech? = null

    @Volatile
    private var isCapturing = false

    private lateinit var dbHelper: DatabaseHelper

    private val newTextBuffer      = StringBuilder()
    private var lastSendTime       = 0L
    private var lastAnalysisResult = ""

    private var cumulativeScore    = 0.0
    private var lastScriptDisplayTime = 0L
    private var overlayActivatedTime  = 0L

    @Volatile
    private var currentRms = 0f

    @Volatile
    private var isAiAgentActive = false
    private var agentWebSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null
    private val wsClient = OkHttpClient()
    private val WS_URL = "wss://exemptible-solidary-bryce.ngrok-free.dev/ws/agent"

    private var telephonyManager: TelephonyManager? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    private var floatingButton: View?    = null
    private var floatingButtonAdded      = false
    private var isOnDialerScreen         = false

    // ↓ 발화 누적 버퍼 추가
    private val utteranceBuffer = ArrayDeque<String>()
    private val BUFFER_SIZE = 3

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        dbHelper = DatabaseHelper(this)

        initSherpaEngine()
        setupPhoneStateListener()

        initLSTMModel()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(0.8f)
                Log.i(TAG, "✅ TTS 초기화 완료")
            }
        }
    }

    private fun initLSTMModel() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                lstmDetector = LSTMDetector(this@CallAudioService)
                Log.i(TAG, "✅ LSTM 딥러닝 문맥 탐지 모델 로드 완료!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ LSTM 모델 로드 실패: ${e.message}")
            }
        }
    }

    private fun initSherpaEngine() {
        serviceScope.launch(Dispatchers.Default) {
            try {
                SimulateStreamingAsr.initOfflineRecognizer(this@CallAudioService, 15)
                SimulateStreamingAsr.initVad(assets)
                Log.i(TAG, "✅ 문샤인 STT & VAD 엔진 초기화 완료!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 엔진 초기화 에러: ${e.message}")
            }
        }
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CrocodileDetector:WakeLock"
            )
            wakeLock.acquire(3000)
            Log.i(TAG, "💡 화면 강제 깨우기 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ WakeLock 에러: ${e.message}")
        }
    }

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> {
                            wakeUpScreen()
                            if (PhoneStateReceiver.incomingCallBanner?.isShowing != true) {
                                PhoneStateReceiver.isUnknownCall = true
                                val banner = IncomingCallBanner(this@CallAudioService)
                                PhoneStateReceiver.incomingCallBanner = banner
                                banner.show(IncomingCallBanner.BannerType.UNKNOWN_NUMBER, autoHide = false)
                            }
                        }
                        TelephonyManager.CALL_STATE_OFFHOOK -> handleOffhook()
                        TelephonyManager.CALL_STATE_IDLE    -> handleIdle()
                    }
                }
            }
            telephonyCallback = cb
            telephonyManager?.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> {
                            wakeUpScreen()
                            if (PhoneStateReceiver.incomingCallBanner?.isShowing != true) {
                                PhoneStateReceiver.isUnknownCall = true
                                val banner = IncomingCallBanner(this@CallAudioService)
                                PhoneStateReceiver.incomingCallBanner = banner
                                banner.show(IncomingCallBanner.BannerType.UNKNOWN_NUMBER, autoHide = false)
                            }
                            if (phoneNumber != null) checkAndUpdateForSpam(phoneNumber)
                        }
                        TelephonyManager.CALL_STATE_OFFHOOK -> handleOffhook()
                        TelephonyManager.CALL_STATE_IDLE    -> handleIdle()
                    }
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun checkAndUpdateForSpam(phoneNumber: String) {
        if (isNumberInContacts(phoneNumber)) {
            PhoneStateReceiver.incomingCallBanner?.hide()
            PhoneStateReceiver.incomingCallBanner = null
            PhoneStateReceiver.isUnknownCall = false
            return
        }
        val spamInfo = dbHelper.checkSpamNumber(phoneNumber)
        if (spamInfo != null) {
            Handler(Looper.getMainLooper()).post {
                PhoneStateReceiver.incomingCallBanner?.hide()
                val banner = IncomingCallBanner(this)
                PhoneStateReceiver.incomingCallBanner = banner
                banner.show(IncomingCallBanner.BannerType.SPAM_NUMBER, spamInfo, autoHide = true)
            }
        }
    }

    private fun isNumberInContacts(number: String): Boolean {
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        val cursor = contentResolver.query(
            uri, arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )
        val isKnown = (cursor?.count ?: 0) > 0
        cursor?.close()
        return isKnown
    }

    private fun handleOffhook() {
        PhoneStateReceiver.incomingCallBanner?.let { banner ->
            if (banner.isShowing) banner.startCountdownAndHide(5)
        }
        if (PhoneStateReceiver.isUnknownCall && !DetectionService.isRunning) {
            val intent = android.content.Intent(this, DetectionService::class.java).apply {
                action = DetectionService.ACTION_START
            }
            startForegroundService(intent)
        }
    }

    private fun handleIdle() {
        PhoneStateReceiver.incomingCallBanner?.hide()
        PhoneStateReceiver.incomingCallBanner = null
        PhoneStateReceiver.isUnknownCall = false
        if (DetectionService.overlayView?.isShowing == true) {
            DetectionService.overlayView?.hide()
            DetectionService.overlayView = null
        }
        stopCapture()
        stopService(android.content.Intent(this, DetectionService::class.java))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val DIALER_PACKAGES = setOf(
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.google.android.dialer"
        )

        val nowOnDialer = pkg in DIALER_PACKAGES
        if (nowOnDialer == isOnDialerScreen) return
        isOnDialerScreen = nowOnDialer

        if (isCapturing && floatingButtonAdded) {
            Handler(Looper.getMainLooper()).post {
                if (!nowOnDialer) {
                    floatingButton?.visibility = View.GONE
                } else {
                    updateFloatingButtonVisibility()
                }
            }
        }
    }

    override fun onInterrupt() {
        stopCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        stopCapture()
        hideFloatingButton()
        tts?.stop(); tts?.shutdown(); tts = null
        instance = null
    }

    private fun updateFloatingButtonVisibility() {
        val shouldShow = isCapturing
                && PhoneStateReceiver.isUnknownCall
                && (DetectionService.overlayView?.isShowing != true)

        Handler(Looper.getMainLooper()).post {
            floatingButton?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
    }

    internal fun showFloatingButton() {
        if (floatingButtonAdded) {
            updateFloatingButtonVisibility()
            return
        }

        val wm      = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val radarIcon = object : View(this) {
            private val AMBER = Color.parseColor("#F59E0B")
            private val paintArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = AMBER
                style       = Paint.Style.STROKE
                strokeWidth = 2.2f * density
                strokeCap   = Paint.Cap.ROUND
            }
            private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AMBER
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height * 0.72f
                canvas.drawCircle(cx, cy, 3f * density, paintDot)
                val radii = floatArrayOf(cy * 0.28f, cy * 0.52f, cy * 0.76f)
                val sweepAngle = 150f
                val startAngle = 180f + (180f - sweepAngle) / 2f
                for (r in radii) {
                    canvas.drawArc(
                        cx - r, cy - r, cx + r, cy + r,
                        startAngle, sweepAngle, false, paintArc
                    )
                }
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(38), dp(30)) }

        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E8101027"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            elevation = dp(6).toFloat()
            addView(radarIcon)
            addView(TextView(this@CallAudioService).apply {
                text      = "피싱\n탐지"
                textSize  = 8.5f
                setTextColor(Color.parseColor("#F59E0B"))
                gravity   = Gravity.CENTER
                typeface  = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(4) }
            })

            setOnClickListener {
                if (DetectionService.overlayView?.isShowing != true) {
                    DetectionService.overlayView = OverlayView(this@CallAudioService)
                    DetectionService.overlayView?.show()
                    overlayActivatedTime = System.currentTimeMillis()
                }
                DetectionService.overlayView?.setStage(OverlayView.Stage.WARNING)
                speakGuide("주의하세요")
                visibility = View.GONE
            }
            visibility = View.GONE
        }

        @Suppress("DEPRECATION")
        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(220)
        }

        floatingButton      = badge
        floatingButtonAdded = true
        Handler(Looper.getMainLooper()).post {
            wm.addView(badge, params)
            updateFloatingButtonVisibility()
        }
    }

    internal fun hideFloatingButton() {
        val btn = floatingButton ?: return
        floatingButton      = null
        floatingButtonAdded = false
        isOnDialerScreen    = false
        Handler(Looper.getMainLooper()).post {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(btn) }
            catch (e: Exception) { Log.e(TAG, "플로팅 버튼 제거 실패: ${e.message}") }
        }
    }

    fun startCapture() {
        if (isCapturing) return
        showFloatingButton()

        serviceScope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(this@CallAudioService, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return@launch

            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
            }
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
            }
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return@launch

            audioRecord?.startRecording()
            isCapturing = true
            samplesChannel = Channel(Channel.UNLIMITED)
            Log.i(TAG, "🎤 오디오 캡처 시작 (Sherpa-ONNX 엔진 연동)")

            updateFloatingButtonVisibility()
            startAudioProcessingLoop()
        }
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        simBoxDetector.reset()
        simBoxDetected = false

        stopAiAgent()
        hideFloatingButton()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioManager?.mode = AudioManager.MODE_NORMAL

        samplesChannel.close()
        audioBuffer.clear()

        newTextBuffer.clear()
        lastSendTime = 0L
        lastAnalysisResult = ""
        cumulativeScore = 0.0
        currentRms = 0f

        // ↓ 발화 누적 버퍼 초기화
        utteranceBuffer.clear()
    }

    private fun startAudioProcessingLoop() {
        SimulateStreamingAsr.vad.reset()

        serviceScope.launch(Dispatchers.IO) {
            val interval = 0.1
            val bufferSize = (interval * sampleRate).toInt()
            val buffer = ShortArray(bufferSize)

            while (isCapturing) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (ret > 0) {
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    currentRms = Math.sqrt(samples.map { it * it }.average()).toFloat()

                    if (!simBoxDetected) {
                        val simResult = simBoxDetector.analyze(buffer.copyOf(ret))
                        when (simResult) {
                            is SimBoxDetector.Result.SIMBox -> {
                                simBoxDetected = true
                                Handler(Looper.getMainLooper()).post {
                                    if (DetectionService.overlayView == null || !DetectionService.overlayView!!.isShowing) {
                                        DetectionService.overlayView = OverlayView(this@CallAudioService)
                                        DetectionService.overlayView?.show()
                                        overlayActivatedTime = System.currentTimeMillis()
                                        floatingButton?.visibility = View.GONE
                                    }
                                    DetectionService.overlayView?.setStage(OverlayView.Stage.WARNING)
                                    speakGuide("불법 중계기가 의심됩니다")
                                    Log.w(TAG, "🚨 SIM Box 탐지! 오버레이 표시")
                                }
                            }
                            is SimBoxDetector.Result.Normal -> {
                                Log.i(TAG, "✅ 3초 구간 정상. 다음 발화 구간 대기 중...")
                            }
                            else -> { /* 계속 분석 중 */ }
                        }
                    }

                    if (isAiAgentActive) sendAudioToAgent(buffer, ret)
                    samplesChannel.send(samples)
                }
            }
            samplesChannel.send(FloatArray(0))
        }

        serviceScope.launch(Dispatchers.Default) {
            var buffer = arrayListOf<Float>()
            var offset = 0
            val windowSize = 512
            var isSpeechStarted = false
            var startTime = System.currentTimeMillis()
            var lastText = ""
            var speechStartOffset = 0

            while (isCapturing) {
                for (s in samplesChannel) {
                    if (s.isEmpty()) break

                    buffer.addAll(s.toList())
                    while (offset + windowSize < buffer.size) {
                        SimulateStreamingAsr.vad.acceptWaveform(
                            buffer.subList(offset, offset + windowSize).toFloatArray()
                        )
                        offset += windowSize

                        if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                            isSpeechStarted = true
                            speechStartOffset = kotlin.math.max(0, offset - 6400)
                            startTime = System.currentTimeMillis()
                            simBoxDetector.isSpeechStarted = true
                        }
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    if (isSpeechStarted && elapsed > 200) {
                        val stream = SimulateStreamingAsr.recognizer.createStream()
                        stream.acceptWaveform(
                            buffer.subList(speechStartOffset, offset).toFloatArray(),
                            sampleRate
                        )
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val result = SimulateStreamingAsr.recognizer.getResult(stream)
                        stream.release()

                        lastText = result.text
                        if (lastText.isNotBlank()) {
                            Handler(Looper.getMainLooper()).post {
                                DetectionService.overlayView?.updateSttText(lastText, isInterim = true)
                            }
                        }
                        startTime = System.currentTimeMillis()
                    }

                    while (!SimulateStreamingAsr.vad.empty()) {
                        val stream = SimulateStreamingAsr.recognizer.createStream()
                        stream.acceptWaveform(
                            SimulateStreamingAsr.vad.front().samples,
                            sampleRate
                        )
                        SimulateStreamingAsr.recognizer.decode(stream)
                        val finalResult = SimulateStreamingAsr.recognizer.getResult(stream)
                        stream.release()

                        isSpeechStarted = false
                        SimulateStreamingAsr.vad.pop()

                        simBoxDetector.isSpeechStarted = false

                        buffer = arrayListOf()
                        offset = 0

                        if (finalResult.text.isNotBlank()) {
                            val finalText = finalResult.text
                            Handler(Looper.getMainLooper()).post {
                                DetectionService.overlayView?.updateSttText(finalText, isInterim = false)

                                analyzeContextWithLSTM(finalText)

                                newTextBuffer.append(" $finalText")
                                lastSendTime = System.currentTimeMillis()
                                val newText = newTextBuffer.toString().trim()
                                newTextBuffer.clear()

                                sendTextToServer(newText, lastAnalysisResult, emptyList())
                            }
                        }
                    }
                }
            }
        }
    }

    // ↓ 수정된 analyzeContextWithLSTM
    private fun analyzeContextWithLSTM(text: String) {
        if (!isCapturing) return

        // 발화 누적 버퍼에 추가
        utteranceBuffer.addLast(text)
        if (utteranceBuffer.size > BUFFER_SIZE) utteranceBuffer.removeFirst()

        // 누적된 발화 합쳐서 모델에 넘기기
        val contextText = utteranceBuffer.joinToString(" ")

        serviceScope.launch(Dispatchers.Default) {
            try {
                val result = lstmDetector.predict(contextText)

                if (result.confidence > 0.6) {
                    val addScore = result.confidence * 20.0
                    cumulativeScore += addScore
                    Log.w(TAG, "🚨 [LSTM 탐지] 피싱 의심! 유형: ${result.type}, 확률: ${result.confidence}, 누적점수: $cumulativeScore")
                    Handler(Looper.getMainLooper()).post { triggerOverlay() }
                } else {
                    // 정상 청크 → 점수 감쇠
                    cumulativeScore *= 0.7
                    cumulativeScore = maxOf(cumulativeScore, 0.0)
                    Log.i(TAG, "✅ [LSTM 분석] 정상 문맥 (확률: ${result.confidence}), 누적점수 감쇠: $cumulativeScore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LSTM 분석 실패: ${e.message}")
            }
        }
    }

    private fun triggerOverlay() {
        if (cumulativeScore >= 15.0) {
            if (DetectionService.overlayView == null || !DetectionService.overlayView!!.isShowing) {
                DetectionService.overlayView = OverlayView(this@CallAudioService)
                DetectionService.overlayView?.show()
                overlayActivatedTime = System.currentTimeMillis()

                speakGuide("보이스피싱 의심 정황이 감지되었습니다. 주의하세요.")
                Log.i(TAG, "🚨 오버레이 최초 표시 (누적: $cumulativeScore)")

                floatingButton?.visibility = View.GONE

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 300), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 300, 100, 300), -1)
                    }
                }
            }

            DetectionService.overlayView?.setStage(
                if (cumulativeScore >= 30.0) OverlayView.Stage.DANGER else OverlayView.Stage.WARNING
            )
        }
    }

    fun startAiAgent() {
        if (isAiAgentActive) return

        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = true

        initAudioTrack()
        val request = Request.Builder().url(WS_URL).build()
        agentWebSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isAiAgentActive = true
                Handler(Looper.getMainLooper()).post {
                    DetectionService.overlayView?.updateAiStatus("🤖 AI 실시간 조언 탐지 중...")
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "audio"      -> playAiAudio(Base64.decode(json.getString("data"), Base64.NO_WRAP))
                        "transcript" -> Handler(Looper.getMainLooper()).post {
                            DetectionService.overlayView?.updateAiStatus("🤖 ${json.optString("text")}")
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "WebSocket 파싱 실패: ${e.message}") }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isAiAgentActive = false
                Handler(Looper.getMainLooper()).post {
                    DetectionService.overlayView?.updateAiStatus("❌ AI 대행 연결 실패")
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isAiAgentActive = false
            }
        })
    }

    fun stopAiAgent() {
        if (!isAiAgentActive) return
        isAiAgentActive = false

        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = false

        agentWebSocket?.close(1000, "사용자 종료")
        agentWebSocket = null
        audioTrack?.stop(); audioTrack?.release(); audioTrack = null
        Handler(Looper.getMainLooper()).post {
            DetectionService.overlayView?.updateAiStatus("", visible = false)
        }
    }

    private fun sendAudioToAgent(buffer: ShortArray, size: Int) {
        try {
            val bb = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until size) bb.putShort(buffer[i])
            agentWebSocket?.send(JSONObject().apply {
                put("type", "audio")
                put("data", Base64.encodeToString(bb.array(), Base64.NO_WRAP))
            }.toString())
        } catch (e: Exception) { Log.e(TAG, "오디오 전송 실패: ${e.message}") }
    }

    private fun initAudioTrack() {
        val sr = 24000
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        audioTrack?.setVolume(0.3f)
        audioTrack?.play()
    }

    private fun playAiAudio(pcmBytes: ByteArray) { audioTrack?.write(pcmBytes, 0, pcmBytes.size) }

    private fun speakGuide(message: String) {
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.3f)
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "guide")
    }

    private fun sendTextToServer(text: String, previousResult: String = "", detectedKeywords: List<String> = emptyList()) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://exemptible-solidary-bryce.ngrok-free.dev/analyze-text")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("ngrok-skip-browser-warning", "true")
                conn.doOutput = true
                val body = org.json.JSONObject().apply {
                    put("text", text)
                    put("previous_result", previousResult)
                    put("detected_keywords", org.json.JSONArray(detectedKeywords))
                }.toString()
                conn.outputStream.write(body.toByteArray())
                val result = org.json.JSONObject(conn.inputStream.bufferedReader().readText()).getString("result")
                lastAnalysisResult = result

                Handler(Looper.getMainLooper()).post {
                    val now = System.currentTimeMillis()
                    val timeSinceOverlay    = now - overlayActivatedTime
                    val timeSinceLastScript = now - lastScriptDisplayTime

                    val shouldUpdate = when {
                        lastScriptDisplayTime == 0L && timeSinceOverlay > 3000    -> true
                        lastScriptDisplayTime  > 0L && timeSinceLastScript > 7000 -> true
                        else -> false
                    }
                    if (shouldUpdate) {
                        DetectionService.overlayView?.updateScriptText(result)
                        lastScriptDisplayTime = now
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "❌ 서버 전송 실패: ${e.javaClass.simpleName} - ${e.message}") }
        }
    }

    fun startTestCapture() {
        if (isCapturing) return
        serviceScope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(this@CallAudioService, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return@launch

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return@launch

            audioRecord?.startRecording()
            isCapturing = true
            samplesChannel = Channel(Channel.UNLIMITED)
            Log.i(TAG, "🎤 [테스트모드] 내 목소리 캡처 시작")

            updateFloatingButtonVisibility()
            startAudioProcessingLoop()
        }
    }

    companion object {
        var instance: CallAudioService? = null
            private set
    }
}