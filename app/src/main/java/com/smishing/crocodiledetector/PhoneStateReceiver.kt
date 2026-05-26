package com.smishing.crocodiledetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class PhoneStateReceiver : BroadcastReceiver() {

    private val TAG = "CrocodileDetector"

    companion object {
        var isUnknownCall = false
        var incomingCallBanner: IncomingCallBanner? = null
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state          = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // 번호 있을 때만 스팸 체크 → CallAudioService에서 배너 업데이트
                if (incomingNumber != null) {
                    Log.i(TAG, "📞 [BroadcastReceiver] RINGING 번호: $incomingNumber")
                    CallAudioService.instance?.checkAndUpdateForSpam(incomingNumber)
                }
            }
            // OFFHOOK, IDLE은 CallAudioService TelephonyCallback에서 처리
        }
    }
}