package com.kakdela.p2p.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.kakdela.p2p.data.local.MessageDao
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.workers.scheduleMessageWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Репозиторий для управления сообщениями (текст, файлы, отложенная отправка).
 * Реализует E2EE шифрование перед передачей в P2P сеть.
 */
class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /* ============================================================
       ОТПРАВКА ТЕКСТА (МГНОВЕННАЯ И ОТЛОЖЕННАЯ)
       ============================================================ */

    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        scope.launch {
            val msgId = UUID.randomUUID().toString()
            val myId = identityRepo.getMyId()

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

            // 1. Сохраняем в локальную БД
            dao.insert(msgEntity)

            // 2. Логика отправки
            if (scheduledTime != null) {
                // Если время в будущем — планируем задачу в WorkManager
                scheduleMessageWork(context, msgId, toHash, text, scheduledTime)
            } else {
                // Иначе отправляем немедленно по сети
                performNetworkSend(toHash, msgId, text)
            }
        }
    }

    /**
     * Выполняет фактическую сетевую отправку с E2EE шифрованием.
     * Используется как для мгновенных сообщений, так и воркером для отложенных.
     */
    suspend fun performNetworkSend(toHash: String, msgId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val encrypted = encryptForPeer(toHash, text)
                val delivered = identityRepo.sendMessageSmart(toHash, null, encrypted)
                
                dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
                delivered
            } catch (e: Exception) {
                dao.updateStatus(msgId, "FAILED")
                false
            }
        }
    }

    /* ============================================================
       ОТПРАВКА МЕДИА-ФАЙЛОВ
       ============================================================ */

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        scope.launch {
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
                scheduledTime = null,
                isMe = true,
                status = "PENDING",
                messageType = type,
                fileName = fileName,
                fileBytes = if (bytes.size <= 1024 * 512) bytes else null // Храним в БД только превью/мелкие файлы
            )

            dao.insert(msgEntity)

            val encryptedFile = encryptForPeer(toHash, base64Data)
            val delivered = identityRepo.sendMessageSmart(toHash, null, encryptedFile)

            dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
        }
    }

    /* ============================================================
       ВХОДЯЩИЕ СООБЩЕНИЯ (UDP/Network)
       ============================================================ */

    fun handleIncoming(type: String, data: String, fromHash: String) {
        scope.launch {
            // Расшифровка приватным ключом (CryptoManager знает наш ключ)
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (e: Exception) {
                data // Если расшифровка не удалась, сохраняем как есть
            }

            val msgEntity = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = fromHash,
                senderId = fromHash,
                receiverId = identityRepo.getMyId(),
                text = decrypted,
                timestamp = System.currentTimeMillis(),
                scheduledTime = null,
                isMe = false,
                status = "DELIVERED",
                messageType = if (type == "CHAT_FILE") "FILE" else "TEXT"
            )
            
            dao.insert(msgEntity)
        }
    }

    /* ============================================================
       ВНУТРЕННИЕ УТИЛИТЫ
       ============================================================ */

    /**
     * Шифрует данные публичным ключом получателя.
     */
    private suspend fun encryptForPeer(peerHash: String, plainText: String): String {
        val peerPubKey = identityRepo.getPeerPublicKey(peerHash)
        return if (!peerPubKey.isNullOrBlank()) {
            CryptoManager.encryptMessage(plainText, peerPubKey)
        } else {
            plainText // Fallback (не рекомендуется для продакшна без ключа)
        }
    }

    /**
     * Безопасное чтение байтов из URI (ContentResolver).
     */
    private fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Пометка сообщений прочитанными (вызывается из UI при открытии чата).
     */
    suspend fun markAsRead(chatId: String) {
        dao.markChatAsRead(chatId)
    }
}
