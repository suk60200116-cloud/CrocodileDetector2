package com.smishing.crocodiledetector

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class DatabaseHelper(private val context: Context) {

    private val TAG = "CrocodileDetector"
    private val DB_NAME = "voicephishing.db"

    data class SpamInfo(
        val phoneNumber: String,
        val spamType: String,
        val reportCount: Int,
        val lastReported: String
    )

    private fun getDatabasePath(): String {
        val dbFile = File(context.filesDir, DB_NAME)
        if (!dbFile.exists()) {
            context.assets.open(DB_NAME).use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "✅ voicephishing.db 복사 완료: ${dbFile.absolutePath}")
        }
        return dbFile.absolutePath
    }



    // 스팸 번호 조회 (하이픈 제거 후 조회)
    fun checkSpamNumber(phoneNumber: String): SpamInfo? {
        val normalized = phoneNumber.replace("-", "").replace(" ", "").trim()
        Log.w("CrocodileDetector", "🔍 스팸 조회: 원본=$phoneNumber / 정규화=$normalized")
        return try {
            val db = SQLiteDatabase.openDatabase(getDatabasePath(), null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                "SELECT phone_number, spam_type, report_count, last_reported FROM spam_numbers WHERE phone_number = ?",
                arrayOf(normalized)
            )
            val info = if (cursor.moveToFirst()) {
                SpamInfo(
                    phoneNumber    = cursor.getString(0),
                    spamType       = cursor.getString(1) ?: "스팸",
                    reportCount    = cursor.getInt(2),
                    lastReported   = cursor.getString(3) ?: ""
                )
            } else null
            cursor.close()
            db.close()
            info
        } catch (e: Exception) {
            Log.e(TAG, "❌ 스팸 번호 조회 실패: ${e.message}")
            null
        }
    }
}