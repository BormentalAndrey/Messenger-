package com.kakdela.p2p.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val messageBody = sms.messageBody
            val sender = sms.originatingAddress

            Log.d("SmsReceiver", "SMS от $sender: $messageBody")

            // 🔐 Пример: извлекаем 6-значный код
            val codeRegex = Regex("\\b\\d{6}\\b")
            val code = codeRegex.find(messageBody)?.value

            if (code != null) {
                SmsCodeStore.lastReceivedCode = code
                Log.d("SmsReceiver", "Код получен: $code")
            }
        }
    }
}
