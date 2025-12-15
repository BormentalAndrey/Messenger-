package com.kakdela.p2p.chat

import android.telephony.SmsManager

object SmsChatManager {

    fun sendMessage(phone: String, text: String) {
        SmsManager.getDefault().sendTextMessage(
            phone,
            null,
            text,
            null,
            null
        )
    }
}
