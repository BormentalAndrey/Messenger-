package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность сообщения.
 * Оптимизирована для работы в P2P сети с поддержкой различных каналов доставки.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId"]), Index(value = ["timestamp"])]
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,   // Уникальный ID (UUID)
    val chatId: String,      // Hash собеседника (ID чата)
    val senderId: String,    // Hash отправителя
    val receiverId: String,  // Hash получателя
    val text: String,        // Текст сообщения
    val timestamp: Long,     // Время отправки/получения
    val isMe: Boolean,       // Флаг: отправлено текущим пользователем
    val isRead: Boolean = false,
    val encrypted: Boolean = true,
    
    /**
     * Статус доставки:
     * "PENDING" - в очереди
     * "SENT_P2P" - отправлено через Wi-Fi или Swarm
     * "SENT_SERVER" - отправлено через сигнальный сервер
     * "SENT_SMS" - отправлено через SMS (резервный канал)
     * "DELIVERED" - подтверждено получение
     */
    val status: String = "PENDING",

    // Поля для передачи медиафайлов
    val fileName: String? = null,
    val fileMime: String? = null,
    val fileBytes: ByteArray? = null,
    
    // Тип контента: "TEXT", "IMAGE", "FILE"
    val messageType: String = "TEXT"
)
