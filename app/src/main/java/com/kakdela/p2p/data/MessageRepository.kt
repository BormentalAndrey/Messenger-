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
 * Реализует E2EE шифрование и маршрутизацию: P2P (UDP) -> SMS Fallback.
 */
class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val job = SupervisorJob()
    private val repositoryScope = CoroutineScope(job + Dispatchers.IO)
    
    // Лимит для хранения в SQLite во избежание CursorWindow exceptions (1МБ)
    private val MAX_DB_BLOB_SIZE = 1024 * 1024 

    /* ============================================================
       ОТПРАВКА ТЕКСТА
       ============================================================ */

    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        repositoryScope.launch {
            val myId = identityRepo.getMyId()
            val msgId = UUID.randomUUID().toString()
            
            // Исправлено: getCachedNode — это suspend функция, вызываем внутри корутины
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

            dao.insert(entity)

            if (isScheduled) {
                // Исправлено: передаем 5 аргументов согласно ScheduleMessage.kt
                // Номер телефона воркер получит сам из БД для актуальности данных
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
                
                // Исправлено: извлекаем данные пира для сетевой отправки
                val peer = identityRepo.getCachedNode(toHash)
                val receiverPhone = peer?.phone
                
                // Протокол упаковки: FILEV1:длина_имени:имя:данные
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
                    // Сохраняем только если размер позволяет прочитать это потом из БД без краша
                    fileBytes = if (bytes.size <= MAX_DB_BLOB_SIZE) bytes else null
                )

                dao.insert(entity)

                if (bytes.size > MAX_DB_BLOB_SIZE) {
                    Log.i("MessageRepo", "Файл $fileName слишком велик для БД, отправка только по сети")
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
            // 1. Получение публичного ключа для E2EE
            val pubKey = identityRepo.getPeerPublicKey(chatId)
            val encryptedPayload = if (!pubKey.isNullOrBlank()) {
                CryptoManager.encryptMessage(payload, pubKey)
            } else {
                Log.w("MessageRepo", "Отправка без шифрования: нет ключа для $chatId")
                payload
            }

            // 2. Умная отправка через IdentityRepository (UDP -> SMS fallback)
            val delivered = identityRepo.sendMessageSmart(chatId, phone, encryptedPayload)
            
            // 3. Обновление статуса в БД
            val finalStatus = if (delivered) "SENT" else "FAILED"
            dao.updateStatus(messageId, finalStatus)
            
            delivered
        } catch (e: Exception) {
            Log.e("MessageRepo", "Ошибка сетевой отправки $messageId", e)
            dao.updateStatus(messageId, "FAILED")
            false
        }
    }

    /* ============================================================
       ПРИЁМ И ДЕКОДИРОВАНИЕ
       ============================================================ */

    fun handleIncoming(type: String, data: String, fromHash: String) {
        repositoryScope.launch {
            // 1. Дешифровка входящего сообщения
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (e: Exception) {
                Log.e("MessageRepo", "Ошибка расшифровки от $fromHash", e)
                "[Зашифрованное сообщение]"
            }

            var displayText = decrypted
            var msgType = "TEXT"
            var incomingFileName: String? = null
            var incomingFileBytes: ByteArray? = null

            // 2. Обработка протокола файлов
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
                    Log.e("MessageRepo", "Ошибка парсинга файла", e)
                    displayText = "[Поврежденный файл]"
                }
            }

            // 3. Сохранение в локальную базу данных
            dao.insert(
                MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = fromHash,
                    senderId = fromHash,
                    receiverId = identityRepo.getMyId(),
                    text = displayText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    status = "DELIVERED",
                    messageType = msgType,
                    fileName = incomingFileName,
                    fileBytes = incomingFileBytes
                )
            )
        }
    }

    /* ============================================================
       УТИЛИТЫ И ЖИЗНЕННЫЙ ЦИКЛ
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
