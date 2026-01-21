package com.kakdela.p2p.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.data.local.MessageDao
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.workers.scheduleMessageWork
import kotlinx.coroutines.*
import java.util.UUID

/**
 * MessageRepository: Реализует отправку сообщений.
 * Модификация: Отправляет НЕЗАШИФРОВАННЫЕ сообщения, если транспорт SMS или нет ключа.
 */
class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {
    private val TAG = "MessageRepository"
    private val job = SupervisorJob()
    private val repositoryScope = CoroutineScope(job + Dispatchers.IO)
    
    private companion object {
        const val MAX_DB_BLOB_SIZE = 1024 * 1024 // 1MB limit for SQLite
    }

    /* ======================= ОТПРАВКА ======================= */

    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        repositoryScope.launch {
            try {
                val myId = identityRepo.getMyId()
                val msgId = UUID.randomUUID().toString()
                
                // Пытаемся найти ноду. Если toHash похож на телефон, ищем по телефону или используем как есть.
                val peer = identityRepo.getCachedNode(toHash)
                val targetPhone = peer?.phone ?: if (toHash.matches(Regex("^[+]?[0-9]{10,15}$"))) toHash else null

                val isScheduled = scheduledTime != null && scheduledTime > System.currentTimeMillis()

                val entity = MessageEntity(
                    messageId = msgId,
                    chatId = toHash,
                    senderId = myId,
                    receiverId = toHash,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    scheduledTime = scheduledTime,
                    isMe = true,
                    status = if (isScheduled) "SCHEDULED" else "PENDING",
                    messageType = "TEXT"
                )

                dao.insert(entity)

                if (isScheduled) {
                    scheduleMessageWork(context, msgId, toHash, text, scheduledTime!!)
                } else {
                    // Передаем targetPhone явно, если он есть
                    performNetworkSend(toHash, msgId, text, targetPhone)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text", e)
            }
        }
    }

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        repositoryScope.launch {
            try {
                val bytes = readBytes(uri) ?: return@launch
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val msgId = UUID.randomUUID().toString()
                val peer = identityRepo.getCachedNode(toHash)
                
                // Протокол упаковки: FILEV1:длина_имени:имя:данные_base64
                val payload = "FILEV1:${fileName.length}:$fileName:$base64Data"

                val entity = MessageEntity(
                    messageId = msgId,
                    chatId = toHash,
                    senderId = identityRepo.getMyId(),
                    receiverId = toHash,
                    text = "[Файл: $fileName]",
                    timestamp = System.currentTimeMillis(),
                    isMe = true,
                    status = "PENDING",
                    messageType = type,
                    fileName = fileName,
                    fileBytes = if (bytes.size <= MAX_DB_BLOB_SIZE) bytes else null
                )

                dao.insert(entity)
                performNetworkSend(toHash, msgId, payload, peer?.phone)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing file", e)
            }
        }
    }

    /* ======================= СЕТЕВОЙ ЦИКЛ ======================= */

    suspend fun performNetworkSend(
        chatId: String,
        messageId: String,
        payload: String,
        phone: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Проверяем наличие ключа шифрования
            val pubKey = identityRepo.getPeerPublicKey(chatId)
            
            // ЛОГИКА ОТПРАВКИ SMS:
            // Если публичного ключа нет (это обычный SMS контакт) ИЛИ мы явно шлем на номер телефона -> НЕ ШИФРУЕМ.
            val shouldUseEncryption = !pubKey.isNullOrBlank() && (phone == null || identityRepo.wifiPeers.containsKey(chatId))

            val finalPayload = if (shouldUseEncryption) {
                CryptoManager.encryptMessage(payload, pubKey!!)
            } else {
                Log.i(TAG, "Sending unencrypted message (SMS mode) to $chatId")
                payload
            }

            // 2. Транспорт через IdentityRepo
            // Если chatId - это номер телефона, sendMessageSmart должен обработать это корректно через SMS fallback
            val delivered = identityRepo.sendMessageSmart(chatId, phone, finalPayload)
            
            // 3. Обновление статуса
            val finalStatus = if (delivered) "SENT" else "FAILED"
            dao.updateStatus(messageId, finalStatus)
            
            delivered
        } catch (e: Exception) {
            Log.e(TAG, "Network send critical failure", e)
            dao.updateStatus(messageId, "FAILED")
            false
        }
    }

    /* ======================= ПРИЕМ ======================= */

    fun handleIncoming(type: String, data: String, fromHash: String) {
        repositoryScope.launch {
            try {
                // Попытка дешифровки. Если не удается (пришло обычное SMS), оставляем как есть.
                val decrypted = try {
                    if (type == "SMS" && !data.contains("IV:")) {
                         data // Это обычное SMS, не трогаем
                    } else {
                        CryptoManager.decryptMessage(data).ifEmpty { data }
                    }
                } catch (e: Exception) { 
                    data // Ошибка дешифровки или простой текст
                }

                var displayText = decrypted
                var msgType = "TEXT"
                var incomingFileName: String? = null
                var incomingFileBytes: ByteArray? = null

                // Разбор FILEV1 (только если это наш протокол)
                if (decrypted.startsWith("FILEV1:")) {
                    try {
                        val content = decrypted.substring(7)
                        val firstColon = content.indexOf(':')
                        val nameLength = content.substring(0, firstColon).toInt()
                        val nameStart = firstColon + 1
                        val nameEnd = nameStart + nameLength
                        
                        incomingFileName = content.substring(nameStart, nameEnd)
                        val base64Part = content.substring(nameEnd + 1)
                        
                        val bytes = Base64.decode(base64Part, Base64.NO_WRAP)
                        incomingFileBytes = if (bytes.size <= MAX_DB_BLOB_SIZE) bytes else null
                        
                        displayText = "[Файл: $incomingFileName]"
                        msgType = "FILE"
                    } catch (e: Exception) {
                        displayText = "[Ошибка файла]"
                    }
                }

                dao.insert(MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = fromHash,
                    senderId = fromHash,
                    receiverId = identityRepo.getMyId(),
                    text = displayText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    status = if (type == "SMS") "RECEIVED_SMS" else "DELIVERED",
                    messageType = msgType,
                    fileName = incomingFileName,
                    fileBytes = incomingFileBytes
                ))
            } catch (e: Exception) {
                Log.e(TAG, "handleIncoming error", e)
            }
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) { null }

    fun clear() { job.cancel() }
}
