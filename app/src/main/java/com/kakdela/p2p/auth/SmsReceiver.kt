package com.kakdela.p2p.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.kakdela.p2p.MyApplication
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

/**
 * SmsReceiver: Обеспечивает работу мессенджера через SMS-канал (Offline Mode).
 * Перехватывает [P2P] сообщения и коды авторизации.
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        
        val db = ChatDatabase.getDatabase(context)
        val app = context.applicationContext as? MyApplication
        val repository = app?.identityRepository

        for (sms in messages) {
            val body = sms.displayMessageBody ?: continue
            val senderPhone = sms.originatingAddress ?: "Unknown"

            Log.d(TAG, "Входящее SMS от $senderPhone")

            // 1. Проверка на служебное P2P сообщение
            if (body.startsWith("[P2P]")) {
                val cleanMessage = body.removePrefix("[P2P]").trim()
                processP2pMessage(context, db, senderPhone, cleanMessage)
            } 
            
            // 2. Извлечение кодов авторизации (4-6 цифр)
            val codeRegex = Regex("\\b\\d{4,6}\\b")
            val match = codeRegex.find(body)
            if (match != null) {
                val code = match.value
                SmsCodeStore.lastReceivedCode = code
                Log.d(TAG, "Код подтверждения сохранен в SmsCodeStore: $code")
            }
        }
    }

    private fun processP2pMessage(context: Context, db: ChatDatabase, phone: String, text: String) {
        scope.launch {
            try {
                val repository = (context.applicationContext as? MyApplication)?.identityRepository
                val phoneHash = repository?.generatePhoneDiscoveryHash(phone) ?: ""
                
                // Пытаемся найти отправителя в списке контактов/узлов
                val senderNode = db.nodeDao().getAllNodes().find { 
                    it.phone_hash == phoneHash || it.phone == phone 
                }
                
                val senderHash = senderNode?.userHash ?: "sms_identity_$phone"

                // Базовая логика дешифрования для SMS канала
                val decryptedText = if (text.length > 30 && !text.contains(" ")) {
                    try {
                        CryptoManager.decryptMessage(text).ifEmpty { text }
                    } catch (e: Exception) {
                        text
                    }
                } else {
                    text
                }

                val messageId = UUID.randomUUID().toString()
                val incomingMessage = MessageEntity(
                    messageId = messageId,
                    chatId = senderHash,
                    senderId = senderHash,
                    receiverId = "me",
                    text = decryptedText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    isRead = false,
                    status = "RECEIVED_SMS",
                    messageType = "TEXT"
                )

                db.messageDao().insert(incomingMessage)
                Log.i(TAG, "SMS-P2P сообщение обработано успешно")
                
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки P2P SMS: ${e.message}")
            }
        }
    }
}
