package com.smishing.crocodiledetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class DetectionService : Service() {

    private val TAG = "CrocodileDetector"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "crocodile_detection_channel"

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DetectionService 생성됨")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isRunning) {
                    Log.i(TAG, "이미 실행 중 → 무시")
                    return START_NOT_STICKY
                }
                isRunning = true
                Log.i(TAG, "DetectionService 시작됨")
                startForeground(NOTIFICATION_ID, buildNotification("통화 모니터링 중..."))
                CallAudioService.instance?.startCapture()
                    ?: Log.e(TAG, "❌ Accessibility Service 미활성화!")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "DetectionService 종료됨")
        overlayView?.hide()
        overlayView = null
        CallAudioService.instance?.stopCapture()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "보이스피싱 탐지", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "통화 중 보이스피싱 탐지 서비스" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🐊 Crocodile Detector")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START_DETECTION"
        var isRunning = false
        var overlayView: OverlayView? = null
    }
}