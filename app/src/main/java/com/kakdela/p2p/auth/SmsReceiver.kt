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
 * Реализует склейку многокомпонентных сообщений, извлечение OTP-кодов 
 * и обработку зашифрованных P2P данных.
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // 1. Извлекаем сообщения. Используем склейку, так как P2P пакеты часто длинные.
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
        val senderPhone = messages[0].originatingAddress ?: "Unknown"

        Log.d(TAG, "Входящее SMS от $senderPhone (длина: ${fullBody.length})")

        val app = context.applicationContext as? MyApplication
        val db = ChatDatabase.getDatabase(context)

        // 2. Обработка P2P трафика
        if (fullBody.startsWith("[P2P]")) {
            val cleanPayload = fullBody.removePrefix("[P2P]").trim()
            processP2pMessage(context, db, senderPhone, cleanPayload)
        } 
        
        // 3. Извлечение кодов авторизации (регулярное выражение для 4-6 значных кодов)
        val codeRegex = Regex("\\b\\d{4,6}\\b")
        val match = codeRegex.find(fullBody)
        if (match != null) {
            val code = match.value
            // Сохраняем в статический стор для автоматического подхвата в UI авторизации
            SmsCodeStore.lastReceivedCode = code
            Log.i(TAG, "Обнаружен код подтверждения: $code")
        }
    }

    /**
     * Обработка защищенного сообщения: идентификация отправителя, дешифровка и запись в БД.
     */
    private fun processP2pMessage(context: Context, db: ChatDatabase, phone: String, payload: String) {
        scope.launch {
            try {
                val app = context.applicationContext as? MyApplication
                val repository = app?.identityRepository
                
                // Генерируем хэш номера для поиска в БД
                val phoneHash = repository?.generatePhoneDiscoveryHash(phone) ?: ""
                val myId = repository?.getMyId() ?: "me"
                
                // Ищем узел в БД по номеру телефона или его хэшу
                val senderNode = db.nodeDao().getNodeByPhone(phone) 
                    ?: db.nodeDao().getAllNodes().find { it.phone_hash == phoneHash }
                
                val senderHash = senderNode?.userHash ?: "sms_identity_$phone"

                // Пытаемся дешифровать контент
                // Если это Base64 строка (типично для зашифрованного P2P), пробуем CryptoManager
                val decryptedText = try {
                    if (isBase64(payload)) {
                        CryptoManager.decryptMessage(payload).ifEmpty { payload }
                    } else {
                        payload
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось расшифровать SMS, сохраняем как текст")
                    payload
                }

                val messageId = UUID.randomUUID().toString()
                val incomingMessage = MessageEntity(
                    messageId = messageId,
                    chatId = senderHash,
                    senderId = senderHash,
                    receiverId = myId,
                    text = decryptedText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    status = "RECEIVED_SMS", // Специальный статус для SMS-канала
                    messageType = "TEXT"
                )

                db.messageDao().insert(incomingMessage)
                Log.i(TAG, "SMS-P2P успешно сохранено в БД чата $senderHash")
                
            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка обработки P2P SMS", e)
            }
        }
    }

    /**
     * Простая проверка, является ли строка потенциально зашифрованным Base64 блоком.
     */
    private fun isBase64(s: String): Boolean {
        if (s.isEmpty() || s.contains(" ")) return false
        return try {
            android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
            true
        } catch (e: Exception) {
            false
        }
    }
}
