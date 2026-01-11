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
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        
        // Получаем доступ к базе через Application
        val db = ChatDatabase.getDatabase(context)
        val repository = (context.applicationContext as? MyApplication)?.identityRepository

        for (sms in messages) {
            val body = sms.displayMessageBody ?: continue
            val senderPhone = sms.originatingAddress ?: "Unknown"

            Log.d(TAG, "Входящее SMS от $senderPhone: $body")

            // 1. Проверка на служебное P2P сообщение
            if (body.startsWith("[P2P]")) {
                val cleanMessage = body.removePrefix("[P2P]").trim()
                processP2pMessage(context, db, senderPhone, cleanMessage)
            } 
            
            // 2. Извлечение кодов авторизации
            val codeRegex = Regex("\\b\\d{4,6}\\b")
            if (codeRegex.containsMatchIn(body)) {
                extractAuthCode(body)
            }
        }
    }

    private fun processP2pMessage(context: Context, db: ChatDatabase, phone: String, text: String) {
        scope.launch {
            try {
                // Пытаемся сопоставить номер телефона с хешем в нашей БД
                val repository = (context.applicationContext as? MyApplication)?.identityRepository
                val phoneHash = repository?.generatePhoneDiscoveryHash(phone) ?: ""
                
                // Ищем узел по хешу телефона или по номеру
                val senderNode = db.nodeDao().getAllNodes().find { 
                    it.phone_hash == phoneHash || it.phone == phone 
                }
                
                val senderHash = senderNode?.userHash ?: "sms_identity_$phoneHash"

                // Если сообщение зашифровано (выглядит как Base64), пробуем расшифровать
                // В SMS канале обычно передается текст, но для безопасности заложена база
                val decryptedText = if (text.length > 40 && !text.contains(" ")) {
                    CryptoManager.decryptMessage(text).ifEmpty { text }
                } else {
                    text
                }

                val messageId = UUID.randomUUID().toString()
                val incomingMessage = MessageEntity(
                    messageId = messageId,
                    chatId = senderHash,
                    senderId = senderHash,
                    receiverId = "me", // Специальная метка для текущего пользователя
                    text = decryptedText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    isRead = false,
                    status = "RECEIVED_SMS",
                    messageType = "TEXT"
                )

                db.messageDao().insert(incomingMessage)
                
                // Здесь можно добавить запуск NotificationManager для показа уведомления
                Log.i(TAG, "Сообщение через SMS канал добавлено в чат: $senderHash")
                
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки P2P SMS: ${e.message}")
            }
        }
    }

    private fun extractAuthCode(body: String) {
        val codeRegex = Regex("\\b\\d{4,6}\\b")
        val code = codeRegex.find(body)?.value
        if (code != null) {
            // Сохраняем во временный стор для UI (Splash/Auth экран)
            SmsCodeStore.lastReceivedCode = code
            Log.d(TAG, "Код подтверждения перехвачен: $code")
        }
    }
}

/**
 * Простой синглтон для хранения кода в памяти (на время сессии регистрации)
 */
object SmsCodeStore {
    var lastReceivedCode: String? = null
}
