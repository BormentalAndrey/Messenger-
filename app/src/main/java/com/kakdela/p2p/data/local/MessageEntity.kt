package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность сообщения для P2P-мессенджера.
 * Соответствует пункту 6 и 7 Технического Задания.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]), 
        Index(value = ["timestamp"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,   // UUID или SHA-256 контента сообщения

    /**
     * ID чата. В P2P это user_id собеседника (SHA-256 его публичного ключа).
     * Именно по этому ID происходит lookup в таблице пиров.
     */
    val chatId: String,      

    val senderId: String,    // user_id отправителя
    val receiverId: String,  // user_id получателя
    
    /**
     * Зашифрованный текст или путь к локальному файлу.
     * Согласно ТЗ (п. 4), данные здесь должны быть уже зашифрованы E2EE.
     */
    val text: String,        

    val timestamp: Long,     // System.currentTimeMillis()
    val isMe: Boolean,       // true - исходящее, false - входящее
    val isRead: Boolean = false,
    
    /**
     * Статусы согласно ТЗ (п. 3.3):
     * PENDING - ожидание отправки
     * SENT_WIFI - доставлено через NSD/Direct
     * SENT_SWARM - доставлено через DHT/Swarm
     * SENT_SERVER - доставлено через fallback сервер
     * SENT_SMS - доставлено через SMS резерв
     * READ - прочитано собеседником
     */
    val status: String = "PENDING",

    // --- Медиа-данные (п. 3.3 ТЗ) ---
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "FILE", "VOICE"
    val fileName: String? = null,
    val fileMime: String? = null,
    
    /**
     * Внимание: Для файлов > 1MB рекомендуется хранить здесь URI (путь к файлу),
     * а не сами байты, чтобы не переполнять курсор SQLite.
     */
    val fileBytes: ByteArray? = null,
    
    // Дополнительное поле для привязки к телефонному номеру из Discovery (п. 2.2 ТЗ)
    val contactPhone: String? = null 
) {
    // Вспомогательные методы для логики UI
    fun isMedia(): Boolean = messageType != "TEXT"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageEntity
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
