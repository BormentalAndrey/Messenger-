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

class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Отправка текста. Если задано scheduledTime, ставим в очередь WorkManager.
     */
    fun sendText(toHash: String, text: String, scheduledTime: Long? = null) {
        scope.launch {
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

            dao.insert(msgEntity)

            if (scheduledTime != null) {
                scheduleMessageWork(context, msgId, toHash, text, scheduledTime)
            } else {
                performNetworkSend(toHash, msgId, text)
            }
        }
    }

    /**
     * Фактическая отправка по сети. 
     * Имя метода синхронизировано с ScheduledMessageWorker.
     */
    suspend fun performNetworkSend(chatId: String, msgId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val peerPubKey = identityRepo.getPeerPublicKey(chatId)
                val encrypted = if (!peerPubKey.isNullOrBlank()) {
                    CryptoManager.encryptMessage(text, peerPubKey)
                } else text

                val delivered = identityRepo.sendMessageSmart(chatId, null, encrypted)
                
                // Обновляем статус в БД на основе результата отправки
                dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
                delivered
            } catch (e: Exception) {
                dao.updateStatus(msgId, "FAILED")
                false
            }
        }
    }

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        scope.launch {
            val bytes = readBytes(uri) ?: return@launch
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val msgId = UUID.randomUUID().toString()

            val msgEntity = MessageEntity(
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
                fileBytes = if (bytes.size <= 512 * 1024) bytes else null
            )
            dao.insert(msgEntity)

            val peerPubKey = identityRepo.getPeerPublicKey(toHash)
            val encryptedFile = if (!peerPubKey.isNullOrBlank()) {
                CryptoManager.encryptMessage(base64Data, peerPubKey)
            } else base64Data

            val delivered = identityRepo.sendMessageSmart(toHash, null, encryptedFile)
            dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
        }
    }

    fun handleIncoming(type: String, data: String, fromHash: String) {
        scope.launch {
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (e: Exception) {
                data 
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

    private fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun markAsRead(chatId: String) = dao.markChatAsRead(chatId)
    
    suspend fun updateStatus(messageId: String, status: String) = dao.updateStatus(messageId, status)
}
