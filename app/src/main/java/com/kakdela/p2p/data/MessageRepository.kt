package com.kakdela.p2p.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.data.local.MessageDao
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.workers.scheduleMessageWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Репозиторий для управления сообщениями в P2P среде.
 * Реализует логику E2EE шифрования, многоканальной отправки и планирования задач.
 */
class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    // Используем SupervisorJob, чтобы ошибка в одной задаче не отменяла весь scope репозитория
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /* ============================================================
       ОТПРАВКА ТЕКСТА
       ============================================================ */

    /**
     * Инициирует отправку текста.
     * Автоматически выбирает между мгновенной отправкой и планированием через WorkManager.
     */
    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        repositoryScope.launch {
            val myId = identityRepo.getMyId()
            val msgId = UUID.randomUUID().toString()

            val msgEntity = MessageEntity(
                messageId = msgId,
                chatId = toHash,
                senderId = myId,
                receiverId = toHash,
                text = text,
                timestamp = System.currentTimeMillis(),
                scheduledTime = scheduledTime,
                isMe = true,
                status = if (scheduledTime != null) "SCHEDULED" else "PENDING",
                messageType = "TEXT"
            )

            // 1. Локальное сохранение
            dao.insert(msgEntity)

            // 2. Маршрутизация задачи
            if (scheduledTime != null && scheduledTime > System.currentTimeMillis()) {
                // Планируем в ОС через WorkManager
                scheduleMessageWork(context, msgId, toHash, text, scheduledTime)
            } else {
                // Прямая попытка отправки в P2P сеть
                performNetworkSend(toHash, msgId, text)
            }
        }
    }

    /**
     * Точка входа для сетевой трансляции. 
     * Вызывается как из репозитория, так и из ScheduledMessageWorker.
     */
    suspend fun performNetworkSend(chatId: String, msgId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Получаем публичный ключ для шифрования сообщения (E2EE)
                val peerPubKey = identityRepo.getPeerPublicKey(chatId)
                
                val encrypted = if (!peerPubKey.isNullOrBlank()) {
                    CryptoManager.encryptMessage(text, peerPubKey)
                } else {
                    Log.w("MessageRepo", "Encrypting without peer public key (Insecure!)")
                    text
                }

                // Пытаемся отправить через IdentityRepository (Wi-Fi/Swarm/SMS)
                val delivered = identityRepo.sendMessageSmart(chatId, null, encrypted)
                
                // Фиксируем результат в локальной БД
                val finalStatus = if (delivered) "SENT" else "FAILED"
                dao.updateStatus(msgId, finalStatus)
                
                delivered
            } catch (e: Exception) {
                Log.e("MessageRepo", "Network send failed for $msgId", e)
                dao.updateStatus(msgId, "FAILED")
                false
            }
        }
    }

    /* ============================================================
       ОТПРАВКА МЕДИА
       ============================================================ */

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        repositoryScope.launch {
            try {
                val bytes = readBytes(uri) ?: return@launch
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val msgId = UUID.randomUUID().toString()
                val myId = identityRepo.getMyId()

                val msgEntity = MessageEntity(
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
                    // Для оптимизации БД храним байты только если файл < 512KB
                    fileBytes = if (bytes.size <= 512 * 1024) bytes else null
                )

                dao.insert(msgEntity)

                val peerPubKey = identityRepo.getPeerPublicKey(toHash)
                val encryptedFile = if (!peerPubKey.isNullOrBlank()) {
                    CryptoManager.encryptMessage(base64Data, peerPubKey)
                } else base64Data

                val delivered = identityRepo.sendMessageSmart(toHash, null, encryptedFile)
                dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
                
            } catch (e: Exception) {
                Log.e("MessageRepo", "File processing error", e)
            }
        }
    }

    /* ============================================================
       ПРИЕМ СООБЩЕНИЙ
       ============================================================ */

    /**
     * Обрабатывает входящие сырые данные из IdentityRepository.
     */
    fun handleIncoming(type: String, data: String, fromHash: String) {
        repositoryScope.launch {
            // Дешифровка контента (E2EE)
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (e: Exception) {
                Log.e("MessageRepo", "Decryption failed", e)
                "[Зашифрованное сообщение]" 
            }

            val msgEntity = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = fromHash,
                senderId = fromHash,
                receiverId = identityRepo.getMyId(),
                text = decrypted,
                timestamp = System.currentTimeMillis(),
                isMe = false,
                status = "DELIVERED",
                messageType = if (type == "CHAT_FILE") "FILE" else "TEXT"
            )
            
            dao.insert(msgEntity)
        }
    }

    /* ============================================================
       УТИЛИТЫ
       ============================================================ */

    private fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("MessageRepo", "Error reading URI: $uri", e)
            null
        }
    }

    suspend fun markAsRead(chatId: String) = withContext(Dispatchers.IO) {
        dao.markChatAsRead(chatId)
    }

    suspend fun updateStatus(messageId: String, status: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(messageId, status)
    }
}
