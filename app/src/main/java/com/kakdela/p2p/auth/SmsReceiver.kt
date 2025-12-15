package com.kakdela.p2p.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver(
    private val onCodeReceived: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val text = sms.messageBody
            val regex = Regex("\\b\\d{6}\\b")
            val code = regex.find(text)?.value
            if (code != null) {
                onCodeReceived(code)
            }
        }
    }
}
