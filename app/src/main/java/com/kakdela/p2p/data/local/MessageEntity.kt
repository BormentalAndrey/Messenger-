package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сообщение в чате (P2P / E2EE).
 * Хранится уже в расшифрованном виде.
 */
@Entity(tableName = "messages")
data class MessageEntity(

    @PrimaryKey
    val id: String,          // UUID сообщения

    val chatId: String,      // hash собеседника (один чат = один peer)

    val senderId: String,    // hash отправителя
    val receiverId: String,  // hash получателя

    val text: String,        // текст сообщения (или описание файла)

    val timestamp: Long,     // unix time millis

    val encrypted: Boolean,  // было ли зашифровано при передаче

    val fileName: String? = null,   // имя файла (если есть)
    val fileMime: String? = null,   // MIME тип
    val fileBytes: ByteArray? = null // бинарные данные (если файл)
)
