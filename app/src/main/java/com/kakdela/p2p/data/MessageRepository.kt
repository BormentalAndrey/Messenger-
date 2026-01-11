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
import java.io.InputStream
import java.util.UUID

class MessageRepository(
    private val context: Context,
    private val dao: MessageDao,
    private val identityRepo: IdentityRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Отправка текстового сообщения.
     * В БД сохраняем чистый текст, чтобы пользователь мог видеть свою историю.
     */
    fun sendText(toHash: String, text: String) {
        scope.launch {
            val myId = identityRepo.getMyId()
            val msgId = UUID.randomUUID().toString()

            // 1. Создаем запись в БД (локально видим расшифрованный текст)
            val msgEntity = MessageEntity(
                messageId = msgId,
                chatId = toHash,
                senderId = myId,
                receiverId = toHash,
                text = text, // В локальной базе храним текст как есть
                timestamp = System.currentTimeMillis(),
                isMe = true,
                status = "PENDING",
                messageType = "TEXT"
            )
            dao.insert(msgEntity)

            // 2. Шифруем сообщение ПУБЛИЧНЫМ ключом получателя для передачи по сети
            val peerPubKey = identityRepo.getPeerPublicKey(toHash)
            val encryptedForNetwork = if (!peerPubKey.isNullOrBlank()) {
                CryptoManager.encryptMessage(text, peerPubKey)
            } else {
                text // Fallback (небезопасно, но позволяет не терять связь)
            }

            // 3. Пытаемся отправить
            // Сначала ищем в активных пирах, если нет — запрашиваем через IdentityRepo (DHT/Server)
            val delivered = identityRepo.sendMessageSmart(toHash, null, encryptedForNetwork)

            // 4. Обновляем статус в БД
            dao.updateStatus(msgId, if (delivered) "SENT" else "FAILED")
        }
    }

    /**
     * Отправка медиа-файлов.
     * Согласно ТЗ, файлы шифруются аналогично тексту.
     */
    fun sendFile(toHash: String, uri: Uri, type: String, fileName: String) {
        scope.launch {
            val bytes = readBytes(uri) ?: return@launch
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val msgId = UUID.randomUUID().toString()

            // Сохраняем в базу (для файлов большого объема в text лучше хранить путь к файлу)
            val msgEntity = MessageEntity(
                messageId = msgId,
                chatId = toHash,
                senderId = identityRepo.getMyId(),
                receiverId = toHash,
                text = "[Файл: $fileName]", // Не храним гигантский base64 в колонке text
                timestamp = System.currentTimeMillis(),
                isMe = true,
                status = "PENDING",
                messageType = type,
                fileName = fileName,
                fileBytes = if (bytes.size < 1024 * 500) bytes else null // Храним в БД только если < 500КБ
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

    /**
     * Обработка входящих сообщений.
     * Вызывается автоматически при получении UDP пакета.
     */
    fun handleIncoming(type: String, data: String, fromHash: String) {
        scope.launch {
            // Расшифровываем своим ПРИВАТНЫМ ключом
            val decrypted = try {
                CryptoManager.decryptMessage(data)
            } catch (e: Exception) {
                data // Если не удалось расшифровать, оставляем как есть
            }

            val messageType = when (type) {
                "CHAT_FILE" -> "FILE"
                else -> "TEXT"
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
                messageType = messageType
            )
            
            dao.insert(msgEntity)
            // Автоматически помечаем чат как прочитанный, если пользователь в нем находится
            // (Логика markAsRead обычно вызывается из UI)
        }
    }

    private fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
