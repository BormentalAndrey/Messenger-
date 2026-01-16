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
 * Репозиторий для управления сообщениями (текст и файлы).
 * Реализует E2EE шифрование и гибридную маршрутизацию: P2P (UDP) -> SMS Fallback.
 */
class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val job = SupervisorJob()
    private val repositoryScope = CoroutineScope(job + Dispatchers.IO)
    
    // Лимит для хранения в SQLite во избежание CursorWindow exceptions (1МБ)
    private companion object {
        const val MAX_DB_BLOB_SIZE = 1024 * 1024 
    }

    /* ============================================================
       ОТПРАВКА ТЕКСТА
       ============================================================ */

    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        repositoryScope.launch {
            try {
                val myId = identityRepo.getMyId()
                val msgId = UUID.randomUUID().toString()
                
                // Получаем кэшированные данные узла (номер телефона и IP)
                val peer = identityRepo.getCachedNode(toHash)
                val receiverPhone = peer?.phone

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

                // Сначала сохраняем локально, чтобы сообщение сразу появилось в UI
                dao.insert(entity)

                if (isScheduled) {
                    scheduleMessageWork(
                        context, 
                        msgId, 
                        toHash, 
                        text, 
                        scheduledTime!!
                    )
                } else {
                    performNetworkSend(toHash, msgId, text, receiverPhone)
                }
            } catch (e: Exception) {
                Log.e("MessageRepo", "Ошибка при отправке текста", e)
            }
        }
    }

    /* ============================================================
       ОТПРАВКА ФАЙЛОВ
       ============================================================ */

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        repositoryScope.launch {
            try {
                val bytes = readBytes(uri) ?: return@launch
                
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val msgId = UUID.randomUUID().toString()
                val myId = identityRepo.getMyId()
                
                val peer = identityRepo.getCachedNode(toHash)
                val receiverPhone = peer?.phone
                
                // Протокол упаковки: FILEV1:длина_имени:имя:данные_base64
                val payload = "FILEV1:${fileName.length}:$fileName:$base64Data"

                val entity = MessageEntity(
                    messageId = msgId,
                    chatId = toHash,
                    senderId = myId,
                    receiverId = toHash,
                    text = "[Файл: $fileName]",
                    timestamp = System.currentTimeMillis(),
                    isMe = true,
                    status = "PENDING",
                    messageType = type,
                    fileName = fileName,
                    // Сохраняем в БД только если файл не превышает лимит окна курсора
                    fileBytes = if (bytes.size <= MAX_DB_BLOB_SIZE) bytes else null
                )

                dao.insert(entity)

                if (bytes.size > MAX_DB_BLOB_SIZE) {
                    Log.i("MessageRepo", "Файл $fileName слишком велик для SQLite, отправка пойдет через стрим")
                }

                performNetworkSend(toHash, msgId, payload, receiverPhone)
            } catch (e: Exception) {
                Log.e("MessageRepo", "Ошибка обработки файла", e)
            }
        }
    }

    /* ============================================================
       СЕТЕВАЯ ТРАНСЛЯЦИЯ (CORE)
       ============================================================ */

    suspend fun performNetworkSend(
        chatId: String,
        messageId: String,
        payload: String,
        phone: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Получение публичного ключа для E2EE шифрования
            val pubKey = identityRepo.getPeerPublicKey(chatId)
            val encryptedPayload = if (!pubKey.isNullOrBlank()) {
                CryptoManager.encryptMessage(payload, pubKey)
            } else {
                Log.w("MessageRepo", "Отправка без E2EE: публичный ключ не найден для $chatId")
                payload
            }

            // 2. Умная отправка через IdentityRepository (сначала P2P UDP, затем SMS)
            val delivered = identityRepo.sendMessageSmart(chatId, phone, encryptedPayload)
            
            // 3. Обновление статуса в БД (для UI индикации)
            val finalStatus = if (delivered) "SENT" else "FAILED"
            dao.updateStatus(messageId, finalStatus)
            
            delivered
        } catch (e: Exception) {
            Log.e("MessageRepo", "Критическая ошибка сетевой отправки $messageId", e)
            dao.updateStatus(messageId, "FAILED")
            false
        }
    }

    /* ============================================================
       ПРИЁМ И ДЕКОДИРОВАНИЕ (ВЫЗЫВАЕТСЯ ИЗ IDENTITY REPO)
       ============================================================ */

    fun handleIncoming(type: String, data: String, fromHash: String) {
        repositoryScope.launch {
            try {
                Log.i("MessageRepo", "Обработка входящего $type от $fromHash")
                
                // 1. Дешифровка входящего сообщения (если применимо)
                val decrypted = try {
                    CryptoManager.decryptMessage(data).ifEmpty { data }
                } catch (e: Exception) {
                    Log.e("MessageRepo", "Ошибка расшифровки, сохраняем как есть", e)
                    data
                }

                var displayText = decrypted
                var msgType = "TEXT"
                var incomingFileName: String? = null
                var incomingFileBytes: ByteArray? = null

                // 2. Распознавание протокола FILEV1
                if (decrypted.startsWith("FILEV1:")) {
                    try {
                        val content = decrypted.substring(7)
                        val firstColon = content.indexOf(':')
                        
                        if (firstColon != -1) {
                            val nameLength = content.substring(0, firstColon).toInt()
                            val nameStart = firstColon + 1
                            val nameEnd = nameStart + nameLength
                            
                            incomingFileName = content.substring(nameStart, nameEnd)
                            val base64Part = content.substring(nameEnd + 1)
                            
                            val bytes = Base64.decode(base64Part, Base64.NO_WRAP)
                            incomingFileBytes = if (bytes.size <= MAX_DB_BLOB_SIZE) bytes else null
                            
                            displayText = "[Файл: $incomingFileName]"
                            msgType = "FILE"
                        }
                    } catch (e: Exception) {
                        Log.e("MessageRepo", "Ошибка парсинга FILEV1", e)
                        displayText = "[Ошибка передачи файла]"
                    }
                }

                // 3. Запись в локальную базу данных
                dao.insert(
                    MessageEntity(
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
                    )
                )
            } catch (e: Exception) {
                Log.e("MessageRepo", "Ошибка в handleIncoming", e)
            }
        }
    }

    /* ============================================================
       УТИЛИТЫ
       ============================================================ */

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        Log.e("MessageRepo", "Ошибка чтения Uri: $uri", e)
        null
    }

    fun clear() {
        job.cancel()
    }
}
