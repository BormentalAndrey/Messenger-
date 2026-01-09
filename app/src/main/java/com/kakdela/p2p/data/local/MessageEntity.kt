package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность сообщения.
 * Поля приведены в соответствие с требованиями WebRtcClient и MessageRepository.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,          // UUID или Timestamp
    val chatId: String,      // Hash собеседника
    val senderId: String,    // Hash отправителя
    val receiverId: String,  // Hash получателя
    val text: String,        // Содержимое
    val timestamp: Long,     // Время
    val isMe: Boolean,       // Отправлено мной?
    val isRead: Boolean,     // Прочитано?
    val encrypted: Boolean,  // Было ли зашифровано
    
    // Поля для передачи файлов
    val fileName: String? = null,
    val fileMime: String? = null,
    val fileBytes: ByteArray? = null
)
