package com.kakdela.p2p.auth

import android.content.Context
import android.telephony.SmsManager
import kotlin.random.Random

object SmsCodeManager {

    fun generateCode(): String =
        Random.nextInt(100000, 999999).toString()

    fun sendCode(
        context: Context,
        phone: String,
        code: String
    ) {
        val sms = SmsManager.getDefault()
        sms.sendTextMessage(
            phone,
            null,
            "Код подтверждения KakDela: $code",
            null,
            null
        )
    }
}
