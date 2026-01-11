package com.kakdela.p2p.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.kakdela.p2p.data.local.MessageDao
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /* ============================================================
       ОТПРАВКА ТЕКСТА (МГНОВЕННАЯ)
       ============================================================ */

    fun sendText(toHash: String, text: String) {
        scope.launch {
            val messageId = UUID.randomUUID().toString()
            val myId = identityRepo.getMyId()

            val entity = MessageEntity(
                messageId = messageId,
                chatId = toHash,
                senderId = myId,
                receiverId = toHash,
                text = text,
                timestamp = System.currentTimeMillis(),
                isMe = true,
                status = "PENDING",
                messageType = "TEXT"
            )

            dao.insert(entity)

            val encrypted = encryptForPeer(toHash, text)
            val delivered = identityRepo.sendMessageSmart(toHash, null, encrypted)

            dao.updateStatus(messageId, if (delivered) "SENT" else "FAILED")
        }
    }

    /* ============================================================
       ОТПРАВКА ФАЙЛОВ
       ============================================================ */

    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        scope.launch {
            val bytes = readBytes(uri) ?: return@launch
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val messageId = UUID.randomUUID().toString()

            val entity = MessageEntity(
                messageId = messageId,
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

            dao.insert(entity)

            val encrypted = encryptForPeer(toHash, base64)
            val delivered = identityRepo.sendMessageSmart(toHash, null, encrypted)

            dao.updateStatus(messageId, if (delivered) "SENT" else "FAILED")
        }
    }

    /* ============================================================
       ОТЛОЖЕННЫЕ СООБЩЕНИЯ (ДЛЯ WORKMANAGER)
       ============================================================ */

    suspend fun insertOutgoing(message: Message) {
        dao.insert(
            MessageEntity(
                messageId = message.id,
                chatId = message.senderId.takeIf { message.isMe } ?: message.senderId,
                senderId = message.senderId,
                receiverId = message.senderId,
                text = message.text,
                timestamp = message.timestamp,
                isMe = true,
                status = message.status,
                messageType = message.type.name,
                scheduledTime = message.scheduledTime
            )
        )
    }

    suspend fun sendMessageNow(chatId: String, message: Message): Boolean {
        val encrypted = encryptForPeer(chatId, message.text)
        return identityRepo.sendMessageSmart(chatId, null, encrypted)
    }

    suspend fun updateStatus(messageId: String, status: String) {
        dao.updateStatus(messageId, status)
    }

    /* ============================================================
       ВХОДЯЩИЕ СООБЩЕНИЯ (UDP)
       ============================================================ */

    fun handleIncoming(type: String, data: String, fromHash: String) {
        scope.launch {
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (_: Exception) {
                data
            }

            val entity = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = fromHash,
                senderId = fromHash,
                receiverId = identityRepo.getMyId(),
                text = decrypted,
                timestamp = System.currentTimeMillis(),
                isMe = false,
                status = "DELIVERED",
                messageType = when (type) {
                    "CHAT_FILE" -> "FILE"
                    else -> "TEXT"
                }
            )

            dao.insert(entity)
        }
    }

    /* ============================================================
       УТИЛИТЫ
       ============================================================ */

    private fun encryptForPeer(peerHash: String, plain: String): String {
        val pubKey = identityRepo.getPeerPublicKey(peerHash)
        return if (!pubKey.isNullOrBlank()) {
            CryptoManager.encryptMessage(plain, pubKey)
        } else {
            plain
        }
    }

    private fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
