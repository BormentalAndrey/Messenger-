package com.kakdela.p2p.auth

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsCodeManager {

    fun generateCode(): String = (100000..999999).random().toString()

    fun sendCode(context: Context, phone: String, code: String): Boolean {
        return try {
            // Современный способ получения SmsManager
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val text = "Код подтверждения KakDela: $code"
            
            // Отправляем. Если текст будет длинным, используем sendMultipartTextMessage
            smsManager.sendTextMessage(phone, null, text, null, null)
            
            Log.d("SmsCodeManager", "SMS отправлено на $phone: $code")
            true
        } catch (e: Exception) {
            Log.e("SmsCodeManager", "Ошибка отправки SMS", e)
            false
        }
    }
}
