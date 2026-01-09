package com.kakdela.p2p.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

/**
 * Перехватчик SMS для резервного канала связи.
 * Если сообщение начинается с префикса [P2P], оно парсится и добавляется в БД мессенджера.
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    // Используем Scope для записи в БД в фоновом режиме
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val db = ChatDatabase.getDatabase(context)

        for (sms in messages) {
            val body = sms.displayMessageBody ?: continue
            val senderPhone = sms.originatingAddress ?: "Unknown"

            // 1. Проверка на служебное P2P сообщение
            if (body.startsWith("[P2P]")) {
                val cleanMessage = body.removePrefix("[P2P]").trim()
                processP2pMessage(db, senderPhone, cleanMessage)
            } 
            
            // 2. Резервный поиск кодов авторизации (если нужно)
            else if (body.contains("code", ignoreCase = true) || body.contains("код")) {
                extractAuthCode(body)
            }
        }
    }

    /**
     * Превращает входящее SMS в сообщение чата
     */
    private fun processP2pMessage(db: ChatDatabase, phone: String, text: String) {
        scope.launch {
            try {
                // Ищем владельца номера в нашей базе 2500 узлов
                val senderNode = db.nodeDao().getAllNodes().find { it.phone == phone }
                val senderHash = senderNode?.userHash ?: "unknown_sender_$phone"

                val messageId = UUID.randomUUID().toString()
                
                val incomingMessage = MessageEntity(
                    messageId = messageId,
                    chatId = senderHash, // Привязываем к чату отправителя
                    senderId = senderHash,
                    receiverId = "me",
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    isRead = false,
                    status = "SENT_SMS", // Пометка, что получено через SMS канал
                    messageType = "TEXT"
                )

                db.messageDao().insert(incomingMessage)
                Log.d(TAG, "P2P SMS успешно интегрировано в чат: $senderHash")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения P2P SMS: ${e.message}")
            }
        }
    }

    private fun extractAuthCode(body: String) {
        val codeRegex = Regex("\\b\\d{4,6}\\b")
        val code = codeRegex.find(body)?.value
        if (code != null) {
            SmsCodeStore.lastReceivedCode = code
            Log.d(TAG, "Код авторизации перехвачен: $code")
        }
    }
}
