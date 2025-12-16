package com.kakdela.p2p.auth

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import kotlin.random.Random

object SmsCodeManager {

    /** Генерация 6-значного кода */
    fun generateCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    /**
     * Отправка SMS
     * ВАЖНО: вызывается ТОЛЬКО после проверки SEND_SMS
     */
    fun sendCode(
        context: Context,
        phone: String,
        code: String
    ) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        smsManager.sendTextMessage(
            phone,
            null,
            "Код подтверждения KakDela: $code",
            null,
            null
        )
    }
}
